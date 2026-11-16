package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * An installed server and its static configuration, functional role, and
 * mutable simulation state.
 *
 * @author CPZ
 */
public class Server {

    private final ServerLocation location;
    private final ServerConfig config;

    private HardwareStatus status;
    private double utilization;
    private double requestedUtilization;
    private double requestedPowerWatts;
    private ThermalProtectionState thermalProtectionState = ThermalProtectionState.NORMAL;
    private double shutdownElapsedSeconds = Double.NaN;
    private boolean thermalRestartRequested;
    private float currentPowerWatts;
    private final ServerRole role;

    /**
     * Creates a server with an explicit, non-null primary functional role.
     */
    public Server(ServerLocation location, ServerConfig config, HardwareStatus initialStatus, ServerRole role) {
        this.location = Objects.requireNonNull(location, "location cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.status = Objects.requireNonNull(initialStatus, "initialStatus cannot be null");
        this.role = Objects.requireNonNull(role, "role cannot be null");
        updatePowerConsumption();
    }

    public void updatePowerConsumption() {
        requestedPowerWatts = config.idlePowerWatts() + requestedUtilization * (config.maxPowerWatts() - config.idlePowerWatts());
        applyPowerLimit();
    }

    private void applyPowerLimit() {
        if (status == HardwareStatus.OFFLINE) {
            currentPowerWatts = (float) (thermalProtectionState == ThermalProtectionState.SHUTDOWN
                    ? config.thermalProtection().shutdownPowerWatts() : 0.0);
            utilization = 0;
        } else {
            double cap = thermalProtectionState == ThermalProtectionState.THROTTLED
                    ? config.idlePowerWatts() + config.thermalProtection().throttledMaxUtilization()
                    * (config.maxPowerWatts() - config.idlePowerWatts()) : config.maxPowerWatts();
            currentPowerWatts = (float) Math.min(requestedPowerWatts, cap);
        }
    }

    public String getCode() {
        return location.code();
    }

    public ServerLocation getLocation() {
        return location;
    }

    public ServerConfig getConfig() {
        return config;
    }

    public HardwareStatus getStatus() {
        return status;
    }

    public void setStatus(HardwareStatus status) {
        Objects.requireNonNull(status, "status cannot be null");
        if (thermalProtectionState == ThermalProtectionState.SHUTDOWN) {
            if (status != HardwareStatus.OFFLINE)
                throw new IllegalStateException("Use requestThermalRestart; a thermal shutdown cannot bypass recovery conditions");
        }
        if (status == HardwareStatus.OFFLINE) {
            // Explicit operator commands own availability and cancel automatic recovery.
            thermalProtectionState = ThermalProtectionState.NORMAL;
            shutdownElapsedSeconds = Double.NaN;
            thermalRestartRequested = false;
        }
        this.status = status;
        applyPowerLimit();
    }

    public double getUtilization() {
        return utilization;
    }

    public void setUtilization(double utilization) {
        if (!Double.isFinite(utilization) || utilization < 0.0f || utilization > 1.0f)
            throw new IllegalArgumentException("utilization must be finite and within [0, 1]");
        this.requestedUtilization = utilization;
        this.utilization = status == HardwareStatus.OFFLINE ? 0.0 : Math.min(utilization,
                thermalProtectionState == ThermalProtectionState.THROTTLED
                        ? config.thermalProtection().throttledMaxUtilization() : 1.0);
    }

    public float getCurrentPowerWatts() {
        return currentPowerWatts;
    }

    /**
     * Supplies requested server power; protection can reduce the delivered power.
     *
     * <p>This method is intended for power-driven simulations where server
     * power demand is supplied by an input source instead of
     * being calculated from utilization.</p>
     *
     * @param currentPowerWatts requested electrical power in watts; use zero for offline servers
     */
    public void setCurrentPowerWatts(double currentPowerWatts) {
        if (!Double.isFinite(currentPowerWatts) || currentPowerWatts < 0.0)
            throw new IllegalArgumentException("currentPowerWatts must be finite and >= 0");
        if (status == HardwareStatus.OFFLINE && currentPowerWatts != 0.0)
            throw new IllegalArgumentException("currentPowerWatts must be 0.0 for OFFLINE servers");
        if (currentPowerWatts > config.maxPowerWatts())
            throw new IllegalArgumentException("currentPowerWatts must not exceed maxPowerWatts");
        this.requestedPowerWatts = currentPowerWatts;
        applyPowerLimit();
    }

    /**
     * Estimates utilization from the current power value.
     *
     * <p>This is intended for power-driven simulations where power is the
     * authoritative input and utilization is only an estimated value derived for
     * snapshots, health checks, and UI compatibility.</p>
     */
    public void estimateUtilizationFromCurrentPower() {
        if (status == HardwareStatus.OFFLINE) {
            utilization = 0.0;
            return;
        }
        double dynamicPowerRange = config.maxPowerWatts() - config.idlePowerWatts();
        double estimatedUtilization = (currentPowerWatts - config.idlePowerWatts()) / dynamicPowerRange;
        utilization = Math.clamp(estimatedUtilization, 0.0, 1.0);
        requestedUtilization = Math.clamp((requestedPowerWatts - config.idlePowerWatts()) / dynamicPowerRange, 0.0, 1.0);
    }

    public ThermalProtectionState getThermalProtectionState() { return thermalProtectionState; }
    public double getRequestedUtilization() { return requestedUtilization; }
    public double getRequestedPowerWatts() { return requestedPowerWatts; }

    /** Queues a manual restart; temperature and minimum-off-time checks still apply. */
    public void requestThermalRestart() {
        if (thermalProtectionState != ThermalProtectionState.SHUTDOWN)
            throw new IllegalStateException("Server is not thermally shut down");
        thermalRestartRequested = true;
    }

    /** Evaluates protection at a control boundary; never clamps a temperature. */
    public void evaluateThermalProtection(double temperatureCelsius, double elapsedSeconds) {
        if (!Double.isFinite(temperatureCelsius) || !Double.isFinite(elapsedSeconds) || elapsedSeconds < 0)
            throw new IllegalArgumentException("thermal protection requires finite temperature and non-negative elapsed seconds");
        ServerThermalProtection protection = config.thermalProtection();
        if (protection == null || !protection.enabled()) return;
        if (thermalProtectionState == ThermalProtectionState.SHUTDOWN) {
            if (temperatureCelsius <= protection.restartTemperatureCelsius()
                    && elapsedSeconds - shutdownElapsedSeconds >= protection.minimumShutdownSeconds()
                    && (protection.restartPolicy() == ThermalRestartPolicy.AUTOMATIC || thermalRestartRequested)) {
                status = HardwareStatus.OK;
                thermalProtectionState = ThermalProtectionState.NORMAL;
                shutdownElapsedSeconds = Double.NaN;
                thermalRestartRequested = false;
            }
        } else if (status != HardwareStatus.OFFLINE) {
            if (temperatureCelsius >= protection.shutdownTemperatureCelsius()) {
                status = HardwareStatus.OFFLINE;
                thermalProtectionState = ThermalProtectionState.SHUTDOWN;
                shutdownElapsedSeconds = elapsedSeconds;
                thermalRestartRequested = false;
            } else if (temperatureCelsius >= protection.throttleTemperatureCelsius()) {
                thermalProtectionState = ThermalProtectionState.THROTTLED;
            } else if (temperatureCelsius <= protection.throttleRecoveryTemperatureCelsius()) {
                thermalProtectionState = ThermalProtectionState.NORMAL;
            }
        }
        utilization = status == HardwareStatus.OFFLINE ? 0 : Math.min(requestedUtilization,
                thermalProtectionState == ThermalProtectionState.THROTTLED ? protection.throttledMaxUtilization() : 1);
        applyPowerLimit();
    }

    /** Restores protection-owned availability while preserving operator OFFLINE commands. */
    public void resetThermalProtection() {
        if (thermalProtectionState == ThermalProtectionState.SHUTDOWN) status = HardwareStatus.OK;
        thermalProtectionState = ThermalProtectionState.NORMAL;
        shutdownElapsedSeconds = Double.NaN;
        thermalRestartRequested = false;
        utilization = status == HardwareStatus.OFFLINE ? 0 : requestedUtilization;
        applyPowerLimit();
    }

    /**
     * Returns the server's primary functional role.
     *
     * @return the non-null role assigned when the server was constructed
     */
    public ServerRole getRole() {
        return role;
    }
}

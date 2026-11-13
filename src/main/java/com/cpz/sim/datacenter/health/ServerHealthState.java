package com.cpz.sim.datacenter.health;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable health state for one installed server.
 *
 * <p>The state belongs to {@code ServerHealthSystem}. It stores the active
 * alert reasons required to apply hysteresis independently to every monitored
 * condition.
 *
 * @author CPZ
 */
public final class ServerHealthState {

    private final String serverCode;
    private boolean criticalTemperature;
    private final EnumSet<ServerAlertReason> alertReasons = EnumSet.noneOf(ServerAlertReason.class);

    public ServerHealthState(String serverCode) {
        this.serverCode = requireNonBlank(serverCode, "serverCode");
    }

    public String getServerCode() {
        return serverCode;
    }

    /**
     * Returns an immutable copy of the currently active alert reasons.
     */
    public Set<ServerAlertReason> getAlertReasons() {
        if (alertReasons.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(alertReasons));
    }

    public boolean hasAlertReason(ServerAlertReason reason) {
        return alertReasons.contains(Objects.requireNonNull(reason, "reason must not be null."));
    }

    public boolean hasAlertReasons() {
        return !alertReasons.isEmpty();
    }

    public void updateAlertReason(ServerAlertReason reason, double value, HealthThreshold threshold) {
        Objects.requireNonNull(reason, "reason must not be null.");
        Objects.requireNonNull(threshold, "threshold must not be null.");
        boolean active = threshold.evaluate(value, alertReasons.contains(reason));
        if (active) alertReasons.add(reason);
        else alertReasons.remove(reason);
        if (reason == ServerAlertReason.HIGH_TEMPERATURE && !active) criticalTemperature = false;
    }

    public ThermalAlertLevel getThermalAlertLevel() {
        if (criticalTemperature) return ThermalAlertLevel.CRITICAL;
        return hasAlertReason(ServerAlertReason.HIGH_TEMPERATURE)
                ? ThermalAlertLevel.ELEVATED : ThermalAlertLevel.NORMAL;
    }

    /** Evaluates both thermal hysteresis bands using the same tick temperature. */
    public void updateTemperature(double value, ServerHealthOptions options) {
        Objects.requireNonNull(options, "options must not be null.");
        updateAlertReason(ServerAlertReason.HIGH_TEMPERATURE, value, options.temperatureThreshold());
        HealthThreshold critical = options.criticalTemperatureThreshold();
        criticalTemperature = critical != null && critical.evaluate(value, criticalTemperature);
    }

    public void clearAlertReasons() {
        criticalTemperature = false;
        alertReasons.clear();
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be null or blank.");
        return value;
    }
}

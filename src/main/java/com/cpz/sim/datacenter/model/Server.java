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
        if (status == HardwareStatus.OFFLINE) {
            currentPowerWatts = 0.0f;
            return;
        }
        currentPowerWatts = (float) (config.idlePowerWatts() + utilization * (config.maxPowerWatts() - config.idlePowerWatts()));
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
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public double getUtilization() {
        return utilization;
    }

    public void setUtilization(double utilization) {
        if (!Double.isFinite(utilization) || utilization < 0.0f || utilization > 1.0f)
            throw new IllegalArgumentException("utilization must be finite and within [0, 1]");
        this.utilization = utilization;
    }

    public float getCurrentPowerWatts() {
        return currentPowerWatts;
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

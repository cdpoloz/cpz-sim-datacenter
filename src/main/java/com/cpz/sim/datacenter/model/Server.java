package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * @author CPZ
 */
public class Server {

    private final ServerLocation location;
    private final ServerConfig config;

    private HardwareStatus status;
    private float utilization;
    private float currentPowerWatts;

    public Server(ServerLocation location, ServerConfig config, HardwareStatus initialStatus) {
        this.location = Objects.requireNonNull(location, "location cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.status = Objects.requireNonNull(initialStatus, "initialStatus cannot be null");
        updatePowerConsumption();
    }

    public void updatePowerConsumption() {
        if (status == HardwareStatus.OFFLINE) {
            currentPowerWatts = 0.0f;
            return;
        }
        currentPowerWatts = config.idlePowerWatts() + utilization * (config.maxPowerWatts() - config.idlePowerWatts());
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

    public float getUtilization() {
        return utilization;
    }

    public void setUtilization(float utilization) {
        if (!Float.isFinite(utilization) || utilization < 0.0f || utilization > 1.0f) {
            throw new IllegalArgumentException("utilization must be finite and within [0, 1]");
        }
        this.utilization = utilization;
    }

    public float getCurrentPowerWatts() {
        return currentPowerWatts;
    }
}

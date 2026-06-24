package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * @author CPZ
 */
public record ServerConfig(
        String modelCode,
        String manufacturer,
        String model,
        float idlePowerWatts,
        float maxPowerWatts
) {

    public ServerConfig {
        Objects.requireNonNull(modelCode);
        Objects.requireNonNull(manufacturer);
        Objects.requireNonNull(model);
        if (!Float.isFinite(idlePowerWatts) || idlePowerWatts < 0.0f)
            throw new IllegalArgumentException("invalid idlePowerWatts");
        if (!Float.isFinite(maxPowerWatts) || maxPowerWatts <= idlePowerWatts)
            throw new IllegalArgumentException("maxPowerWatts must be higher than idlePowerWatts");
    }
}

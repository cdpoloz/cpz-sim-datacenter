package com.cpz.sim.datacenter.config.definition;

/**
 * @author CPZ
 */
public record ServerModelDefinition(
        String modelCode,
        String manufacturer,
        String model,
        float idlePowerWatts,
        float maxPowerWatts
) {
}

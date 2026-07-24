package com.cpz.sim.datacenter.config.definition;

/**
 * JSON definition for server health system options.
 *
 * @author CPZ
 */
public record HealthSystemOptionsDefinition(
        HealthThresholdDefinition utilization,
        HealthThresholdDefinition temperatureCelsius
) {
}

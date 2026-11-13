package com.cpz.sim.datacenter.config.definition;

/**
 * JSON definition for server health system options.
 *
 * @author CPZ
 */
public record HealthSystemOptionsDefinition(
        HealthThresholdDefinition utilization,
        HealthThresholdDefinition temperatureCelsius,
        HealthThresholdDefinition criticalTemperatureCelsius
) {
    public HealthSystemOptionsDefinition(HealthThresholdDefinition utilization,
            HealthThresholdDefinition temperatureCelsius) {
        this(utilization, temperatureCelsius, null);
    }
}

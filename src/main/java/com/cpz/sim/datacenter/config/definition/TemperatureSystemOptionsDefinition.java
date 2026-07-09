package com.cpz.sim.datacenter.config.definition;

/**
 * JSON definition for temperature system options.
 */
public record TemperatureSystemOptionsDefinition(
        double ambientTemperatureCelsius,
        double defaultInitialTemperatureCelsius,
        double thermalCapacityJoulesPerCelsius,
        double heatDissipationWattsPerCelsius
) {
}

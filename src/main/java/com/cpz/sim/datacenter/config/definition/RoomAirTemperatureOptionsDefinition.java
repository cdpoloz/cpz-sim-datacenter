package com.cpz.sim.datacenter.config.definition;

/** Optional configuration for a single, well-mixed room-air thermal state. */
public record RoomAirTemperatureOptionsDefinition(
        double initialTemperatureCelsius,
        double effectiveThermalCapacityJoulesPerCelsius,
        double environmentTemperatureCelsius,
        double environmentHeatTransferWattsPerCelsius
) {
}

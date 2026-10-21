package com.cpz.sim.datacenter.temperature;

/**
 * Physical parameters for a single, well-mixed room-air approximation.
 * Effective capacity may include thermally coupled contents; it is independent
 * of the per-server thermal capacity. No physical defaults are assumed.
 */
public record RoomAirTemperatureOptions(
        double initialTemperatureCelsius,
        double effectiveThermalCapacityJoulesPerCelsius,
        double environmentTemperatureCelsius,
        double environmentHeatTransferWattsPerCelsius
) {

    public RoomAirTemperatureOptions {
        requireFinite(initialTemperatureCelsius, "initialTemperatureCelsius");
        requireFinite(environmentTemperatureCelsius, "environmentTemperatureCelsius");
        if (!Double.isFinite(effectiveThermalCapacityJoulesPerCelsius)
                || effectiveThermalCapacityJoulesPerCelsius <= 0.0)
            throw new IllegalArgumentException("effectiveThermalCapacityJoulesPerCelsius must be finite and > 0");
        if (!Double.isFinite(environmentHeatTransferWattsPerCelsius)
                || environmentHeatTransferWattsPerCelsius < 0.0)
            throw new IllegalArgumentException("environmentHeatTransferWattsPerCelsius must be finite and >= 0");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}

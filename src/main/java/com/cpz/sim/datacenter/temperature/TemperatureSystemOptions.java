package com.cpz.sim.datacenter.temperature;

/**
 * Configuration for the simplified server temperature model.
 *
 * <p>The values describe a first server-level thermal approximation driven by
 * server power, an ambient reference temperature, a lumped thermal capacity,
 * and a linear heat dissipation coefficient. Thermal capacity and heat
 * dissipation act as global fallback values when a server configuration does
 * not provide model-specific properties.</p>
 */
public record TemperatureSystemOptions(
        double ambientTemperatureCelsius,
        double defaultInitialTemperatureCelsius,
        double thermalCapacityJoulesPerCelsius,
        double heatDissipationWattsPerCelsius
) {

    public TemperatureSystemOptions {
        requireFinite(ambientTemperatureCelsius, "ambientTemperatureCelsius");
        requireFinite(defaultInitialTemperatureCelsius, "defaultInitialTemperatureCelsius");
        requirePositive(thermalCapacityJoulesPerCelsius, "thermalCapacityJoulesPerCelsius");
        requireNonNegative(heatDissipationWattsPerCelsius, "heatDissipationWattsPerCelsius");
    }

    /**
     * Returns baseline values for the initial server temperature model.
     */
    public static TemperatureSystemOptions defaults() {
        return new TemperatureSystemOptions(
                25.0,
                25.0,
                5000.0,
                8.0
        );
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite.");
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) throw new IllegalArgumentException(name + " must be finite and greater than zero.");
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException(name + " must be finite and greater than or equal to zero.");
    }
}

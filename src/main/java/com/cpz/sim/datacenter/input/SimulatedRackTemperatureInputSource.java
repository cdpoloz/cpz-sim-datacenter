package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;

/**
 * Generates deterministic simulated rack temperatures for rack-level
 * temperature-driven simulations.
 *
 * <p>Each rack receives a stable spatial base temperature derived from its
 * {@link RackLocation}. A smooth sinusoidal component based on the simulation
 * tick index is then added and clamped to the configured temperature range.</p>
 *
 * @author CPZ
 */
public final class SimulatedRackTemperatureInputSource implements RackTemperatureInputSource {

    public static final double DEFAULT_MIN_TEMPERATURE_CELSIUS = 30.0;
    public static final double DEFAULT_MAX_TEMPERATURE_CELSIUS = 65.0;
    public static final double DEFAULT_TEMPORAL_AMPLITUDE_CELSIUS = 3.0;
    public static final double DEFAULT_TEMPORAL_FREQUENCY = 0.05;

    private final double minTemperatureCelsius;
    private final double maxTemperatureCelsius;
    private final double temporalAmplitudeCelsius;
    private final double temporalFrequency;

    public SimulatedRackTemperatureInputSource() {
        this(
                DEFAULT_MIN_TEMPERATURE_CELSIUS,
                DEFAULT_MAX_TEMPERATURE_CELSIUS,
                DEFAULT_TEMPORAL_AMPLITUDE_CELSIUS,
                DEFAULT_TEMPORAL_FREQUENCY
        );
    }

    public SimulatedRackTemperatureInputSource(
            double minTemperatureCelsius,
            double maxTemperatureCelsius,
            double temporalAmplitudeCelsius,
            double temporalFrequency
    ) {
        validateFinite(minTemperatureCelsius, "minTemperatureCelsius");
        validateFinite(maxTemperatureCelsius, "maxTemperatureCelsius");
        if (minTemperatureCelsius >= maxTemperatureCelsius)
            throw new IllegalArgumentException("minTemperatureCelsius must be lower than maxTemperatureCelsius");
        validateFiniteNonNegative(temporalAmplitudeCelsius, "temporalAmplitudeCelsius");
        validateFiniteNonNegative(temporalFrequency, "temporalFrequency");
        this.minTemperatureCelsius = minTemperatureCelsius;
        this.maxTemperatureCelsius = maxTemperatureCelsius;
        this.temporalAmplitudeCelsius = temporalAmplitudeCelsius;
        this.temporalFrequency = temporalFrequency;
    }

    @Override
    public double temperatureCelsius(RackLocation rackLocation, SimulationTick tick) {
        Objects.requireNonNull(rackLocation, "rackLocation must not be null");
        Objects.requireNonNull(tick, "tick must not be null");
        double baseTemperatureCelsius = baseTemperatureFor(rackLocation);
        double temporalOscillation = temporalAmplitudeCelsius
                * Math.sin(tick.index() * temporalFrequency + phaseFor(rackLocation));
        return clamp(
                baseTemperatureCelsius + temporalOscillation,
                minTemperatureCelsius,
                maxTemperatureCelsius
        );
    }

    private double baseTemperatureFor(RackLocation rackLocation) {
        double effectiveAmplitude = Math.min(
                temporalAmplitudeCelsius,
                (maxTemperatureCelsius - minTemperatureCelsius) / 2.0
        );
        double baseMin = minTemperatureCelsius + effectiveAmplitude;
        double baseMax = maxTemperatureCelsius - effectiveAmplitude;
        if (baseMin >= baseMax) return (minTemperatureCelsius + maxTemperatureCelsius) / 2.0;
        return baseMin + normalizedRackValue(rackLocation, 0) * (baseMax - baseMin);
    }

    private static double phaseFor(RackLocation rackLocation) {
        return normalizedRackValue(rackLocation, 1) * Math.PI * 2.0;
    }

    private static double normalizedRackValue(RackLocation rackLocation, int salt) {
        int hash = stableHash(rackLocation.code(), salt);
        return Math.floorMod(hash, 10_000) / 9_999.0;
    }

    private static int stableHash(String value, int salt) {
        int hash = 17 + salt * 131;
        for (int i = 0; i < value.length(); i++) hash = 31 * hash + value.charAt(i);
        return hash;
    }

    private static void validateFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static void validateFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0)
            throw new IllegalArgumentException(name + " must be finite and non-negative");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

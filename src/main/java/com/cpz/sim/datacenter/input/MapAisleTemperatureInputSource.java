package com.cpz.sim.datacenter.input;

import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Map;
import java.util.Objects;

/**
 * Supplies aisle temperatures from an immutable map with a default fallback.
 *
 * @author CPZ
 */
public final class MapAisleTemperatureInputSource implements AisleTemperatureInputSource {

    private final Map<String, Double> temperatureByAisleCode;
    private final double defaultTemperatureCelsius;

    public MapAisleTemperatureInputSource(
            Map<String, Double> temperatureByAisleCode,
            double defaultTemperatureCelsius
    ) {
        this.temperatureByAisleCode = Map.copyOf(
                Objects.requireNonNull(temperatureByAisleCode, "temperatureByAisleCode must not be null")
        );
        validateTemperature(defaultTemperatureCelsius, "defaultTemperatureCelsius");
        for (Map.Entry<String, Double> entry : this.temperatureByAisleCode.entrySet()) {
            String aisleCode = entry.getKey();
            Objects.requireNonNull(aisleCode, "aisleCode must not be null");
            if (aisleCode.isBlank()) throw new IllegalArgumentException("aisleCode must not be blank");
            if (entry.getValue() == null)
                throw new IllegalArgumentException("temperatureCelsius must not be null for aisle: " + aisleCode);
            validateTemperature(entry.getValue(), "temperatureCelsius for aisle: " + aisleCode);
        }
        this.defaultTemperatureCelsius = defaultTemperatureCelsius;
    }

    @Override
    public double temperatureCelsius(String aisleCode, SimulationTick tick) {
        Objects.requireNonNull(aisleCode, "aisleCode must not be null");
        if (aisleCode.isBlank()) throw new IllegalArgumentException("aisleCode must not be blank");
        Objects.requireNonNull(tick, "tick must not be null");
        return temperatureByAisleCode.getOrDefault(aisleCode, defaultTemperatureCelsius);
    }

    private static void validateTemperature(double temperatureCelsius, String name) {
        if (!Double.isFinite(temperatureCelsius))
            throw new IllegalArgumentException(name + " must be finite");
    }
}

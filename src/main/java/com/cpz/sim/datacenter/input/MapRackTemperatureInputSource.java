package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Map;
import java.util.Objects;

/**
 * Supplies rack temperatures from an immutable map with a default fallback.
 *
 * @author CPZ
 */
public final class MapRackTemperatureInputSource implements RackTemperatureInputSource {

    private final Map<RackLocation, Double> temperatureByRackLocation;
    private final double defaultTemperatureCelsius;

    public MapRackTemperatureInputSource(
            Map<RackLocation, Double> temperatureByRackLocation,
            double defaultTemperatureCelsius
    ) {
        this.temperatureByRackLocation = Map.copyOf(
                Objects.requireNonNull(temperatureByRackLocation, "temperatureByRackLocation must not be null")
        );
        validateTemperature(defaultTemperatureCelsius, "defaultTemperatureCelsius");
        for (Map.Entry<RackLocation, Double> entry : this.temperatureByRackLocation.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "rackLocation must not be null");
            if (entry.getValue() == null)
                throw new IllegalArgumentException("temperatureCelsius must not be null for rack: " + entry.getKey().code());
            validateTemperature(entry.getValue(), "temperatureCelsius for rack: " + entry.getKey().code());
        }
        this.defaultTemperatureCelsius = defaultTemperatureCelsius;
    }

    @Override
    public double temperatureCelsius(RackLocation rackLocation, SimulationTick tick) {
        Objects.requireNonNull(rackLocation, "rackLocation must not be null");
        Objects.requireNonNull(tick, "tick must not be null");
        return temperatureByRackLocation.getOrDefault(rackLocation, defaultTemperatureCelsius);
    }

    private static void validateTemperature(double temperatureCelsius, String name) {
        if (!Double.isFinite(temperatureCelsius))
            throw new IllegalArgumentException(name + " must be finite");
    }
}

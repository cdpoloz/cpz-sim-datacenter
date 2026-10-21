package com.cpz.sim.datacenter.temperature;

import java.util.Objects;

/**
 * Mutable representative room-air temperature, independent of server states.
 * This state does not advance itself and is not yet coupled to the pipeline.
 */
public final class RoomAirThermalState {

    private double temperatureCelsius;

    public RoomAirThermalState(RoomAirTemperatureOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        temperatureCelsius = options.initialTemperatureCelsius();
    }

    public double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(double temperatureCelsius) {
        if (!Double.isFinite(temperatureCelsius))
            throw new IllegalArgumentException("temperatureCelsius must be finite");
        this.temperatureCelsius = temperatureCelsius;
    }
}

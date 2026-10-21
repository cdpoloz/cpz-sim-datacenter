package com.cpz.sim.datacenter.snapshot;

/** Immutable representative room-air temperature after an explicit thermal step. */
public record RoomAirTemperatureSnapshot(
        long tickIndex,
        double elapsedSeconds,
        double temperatureCelsius
) {

    public RoomAirTemperatureSnapshot {
        if (tickIndex < 0L) throw new IllegalArgumentException("tickIndex must be >= 0");
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0)
            throw new IllegalArgumentException("elapsedSeconds must be finite and >= 0");
        if (!Double.isFinite(temperatureCelsius))
            throw new IllegalArgumentException("temperatureCelsius must be finite");
    }
}

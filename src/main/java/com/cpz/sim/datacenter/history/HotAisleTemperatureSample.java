package com.cpz.sim.datacenter.history;

import java.util.Objects;

/**
 * Immutable average temperature observed for one hot aisle on one simulation
 * tick.
 *
 * @author CPZ
 */
public record HotAisleTemperatureSample(
        String hotAisleCode,
        long tickIndex,
        double averageTemperatureCelsius
) {

    public HotAisleTemperatureSample {
        Objects.requireNonNull(hotAisleCode, "hotAisleCode must not be null");
        if (hotAisleCode.isBlank()) throw new IllegalArgumentException("hotAisleCode must not be blank");
        if (tickIndex < 0L) throw new IllegalArgumentException("tickIndex must be >= 0");
        if (!Double.isFinite(averageTemperatureCelsius))
            throw new IllegalArgumentException("averageTemperatureCelsius must be finite");
    }
}

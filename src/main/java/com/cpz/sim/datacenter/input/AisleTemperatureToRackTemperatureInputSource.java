package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;
import java.util.function.Function;

/**
 * Adapts aisle-level temperature observations to rack-level observations.
 *
 * <p>The datacenter core does not assign a fixed physical meaning to an aisle.
 * Consumers provide a resolver that maps each rack location to the stable aisle
 * code whose observation should be used for that rack.</p>
 *
 * @author CPZ
 */
public final class AisleTemperatureToRackTemperatureInputSource implements RackTemperatureInputSource {

    private final AisleTemperatureInputSource aisleTemperatureInputSource;
    private final Function<RackLocation, String> aisleCodeResolver;

    public AisleTemperatureToRackTemperatureInputSource(
            AisleTemperatureInputSource aisleTemperatureInputSource,
            Function<RackLocation, String> aisleCodeResolver
    ) {
        this.aisleTemperatureInputSource =
                Objects.requireNonNull(aisleTemperatureInputSource, "aisleTemperatureInputSource must not be null");
        this.aisleCodeResolver = Objects.requireNonNull(aisleCodeResolver, "aisleCodeResolver must not be null");
    }

    @Override
    public double temperatureCelsius(RackLocation rackLocation, SimulationTick tick) {
        Objects.requireNonNull(rackLocation, "rackLocation must not be null");
        Objects.requireNonNull(tick, "tick must not be null");
        String aisleCode = aisleCodeResolver.apply(rackLocation);
        Objects.requireNonNull(aisleCode, "resolved aisleCode must not be null");
        if (aisleCode.isBlank()) throw new IllegalArgumentException("resolved aisleCode must not be blank");
        return aisleTemperatureInputSource.temperatureCelsius(aisleCode, tick);
    }
}

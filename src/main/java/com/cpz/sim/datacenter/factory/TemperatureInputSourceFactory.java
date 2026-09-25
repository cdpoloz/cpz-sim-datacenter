package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.input.AisleTemperatureToRackTemperatureInputSource;
import com.cpz.sim.datacenter.input.RackTemperatureInputSource;
import com.cpz.sim.datacenter.input.SimulatedAisleTemperatureInputSource;
import com.cpz.sim.datacenter.input.SimulatedRackTemperatureInputSource;
import com.cpz.sim.datacenter.input.StandardHotAisleCodeResolver;

import java.util.Objects;

/**
 * Creates simulated rack-temperature input sources for temperature-driven
 * simulations.
 *
 * @author CPZ
 */
public final class TemperatureInputSourceFactory {

    private final DatacenterConfigValidator validator;

    public TemperatureInputSourceFactory() {
        this(new DatacenterConfigValidator());
    }

    public TemperatureInputSourceFactory(DatacenterConfigValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
    }

    public RackTemperatureInputSource create(DatacenterDefinition definition) {
        validator.validate(definition);
        return switch (definition.temperatureInputGranularity()) {
            case RACK -> new SimulatedRackTemperatureInputSource();
            case AISLE -> new AisleTemperatureToRackTemperatureInputSource(
                    new SimulatedAisleTemperatureInputSource(),
                    new StandardHotAisleCodeResolver()
            );
        };
    }
}

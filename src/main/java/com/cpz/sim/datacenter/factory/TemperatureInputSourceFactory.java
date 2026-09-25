package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.HotAisleDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.input.AisleTemperatureToRackTemperatureInputSource;
import com.cpz.sim.datacenter.input.ConfiguredHotAisleCodeResolver;
import com.cpz.sim.datacenter.input.RackTemperatureInputSource;
import com.cpz.sim.datacenter.input.SimulatedAisleTemperatureInputSource;
import com.cpz.sim.datacenter.input.SimulatedRackTemperatureInputSource;
import com.cpz.sim.datacenter.input.StandardHotAisleCodeResolver;
import com.cpz.sim.datacenter.model.RackLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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
                    createHotAisleCodeResolver(definition)
            );
        };
    }

    private static Function<RackLocation, String> createHotAisleCodeResolver(
            DatacenterDefinition definition
    ) {
        List<HotAisleDefinition> hotAisles = definition.layout().hotAisles();
        if (hotAisles == null) return new StandardHotAisleCodeResolver();
        return new ConfiguredHotAisleCodeResolver(hotAisles);
    }
}

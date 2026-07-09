package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.TemperatureSystemOptionsDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;

import java.util.Objects;

/**
 * Creates runtime temperature options from config definitions.
 */
public final class TemperatureSystemOptionsFactory {

    private final DatacenterConfigValidator validator;

    public TemperatureSystemOptionsFactory() {
        this(new DatacenterConfigValidator());
    }

    public TemperatureSystemOptionsFactory(DatacenterConfigValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
    }

    public TemperatureSystemOptions create(DatacenterDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        validator.validate(definition);
        return create(definition.temperature());
    }

    public TemperatureSystemOptions create(TemperatureSystemOptionsDefinition definition) {
        if (definition == null) return TemperatureSystemOptions.defaults();
        return new TemperatureSystemOptions(
                definition.ambientTemperatureCelsius(),
                definition.defaultInitialTemperatureCelsius(),
                definition.thermalCapacityJoulesPerCelsius(),
                definition.heatDissipationWattsPerCelsius()
        );
    }
}

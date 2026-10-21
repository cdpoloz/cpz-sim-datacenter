package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.RoomAirTemperatureOptionsDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.temperature.RoomAirTemperatureOptions;

import java.util.Objects;
import java.util.Optional;

/** Creates room-air options only when explicitly configured. */
public final class RoomAirTemperatureOptionsFactory {

    private final DatacenterConfigValidator validator;

    public RoomAirTemperatureOptionsFactory() {
        this(new DatacenterConfigValidator());
    }

    public RoomAirTemperatureOptionsFactory(DatacenterConfigValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    public Optional<RoomAirTemperatureOptions> create(DatacenterDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        validator.validate(definition);
        return create(definition.roomAirTemperature());
    }

    public Optional<RoomAirTemperatureOptions> create(RoomAirTemperatureOptionsDefinition definition) {
        if (definition == null) return Optional.empty();
        return Optional.of(new RoomAirTemperatureOptions(
                definition.initialTemperatureCelsius(),
                definition.effectiveThermalCapacityJoulesPerCelsius(),
                definition.environmentTemperatureCelsius(),
                definition.environmentHeatTransferWattsPerCelsius()
        ));
    }
}

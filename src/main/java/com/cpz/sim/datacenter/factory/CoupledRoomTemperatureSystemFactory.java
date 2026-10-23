package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.system.CoolingSystem;
import com.cpz.sim.datacenter.system.CoupledRoomTemperatureSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;

import java.util.Objects;
import java.util.Optional;

/** Creates an opt-in conservative room/server model for power-derived modes. */
public final class CoupledRoomTemperatureSystemFactory {

    public Optional<CoupledRoomTemperatureSystem> create(
            DatacenterDefinition definition,
            Datacenter datacenter,
            TemperatureSystem temperatureSystem,
            Optional<CoolingSystem> coolingSystem
    ) {
        Objects.requireNonNull(datacenter, "datacenter must not be null");
        Objects.requireNonNull(temperatureSystem, "temperatureSystem must not be null");
        Objects.requireNonNull(coolingSystem, "coolingSystem must not be null");
        new DatacenterConfigValidator().validate(definition);
        if (definition.rackCooling() != null)
            throw new UnsupportedOperationException("rackCooling thermal runtime is not implemented yet");
        var roomOptions = new RoomAirTemperatureOptionsFactory().create(definition);
        if (roomOptions.isEmpty()) return Optional.empty();
        if (definition.dataInputMode() == DatacenterDataInputMode.TEMPERATURE_DRIVEN)
            throw new IllegalArgumentException("coupled room model does not yet support TEMPERATURE_DRIVEN telemetry");
        var configuredCooling = new CoolingConfigurationFactory().create(definition, datacenter);
        if (!configuredCooling.equals(coolingSystem.map(CoolingSystem::configuration)))
            throw new IllegalArgumentException("coupled cooling system must match the datacenter definition");
        return Optional.of(new CoupledRoomTemperatureSystem(datacenter, temperatureSystem,
                new TemperatureSystemOptionsFactory().create(definition), roomOptions.orElseThrow(), coolingSystem));
    }
}

package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.input.RackTemperatureInputSource;
import com.cpz.sim.datacenter.input.ServerPowerInputSource;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.CoupledRoomTemperatureSystem;
import com.cpz.sim.datacenter.system.PowerInputSystem;
import com.cpz.sim.datacenter.system.RackTemperatureInputSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.engine.SimulationEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Assembles the authoritative input systems for the configured data-input
 * mode.
 *
 * @author CPZ
 */
public final class DatacenterInputPipelineFactory {

    private final DatacenterConfigValidator validator;
    private final TemperatureInputSourceFactory temperatureInputSourceFactory;

    public DatacenterInputPipelineFactory() {
        this(new DatacenterConfigValidator(), new TemperatureInputSourceFactory());
    }

    public DatacenterInputPipelineFactory(
            DatacenterConfigValidator validator,
            TemperatureInputSourceFactory temperatureInputSourceFactory
    ) {
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
        this.temperatureInputSourceFactory =
                Objects.requireNonNull(temperatureInputSourceFactory, "temperatureInputSourceFactory cannot be null");
    }

    public List<Simulatable> createInputSystems(
            DatacenterDefinition definition,
            Datacenter datacenter,
            TemperatureSystem temperatureSystem,
            TemperatureSystemOptions temperatureSystemOptions,
            WorkloadSource workloadSource,
            ServerPowerInputSource serverPowerInputSource
    ) {
        return createInputSystems(definition, datacenter, temperatureSystem, temperatureSystemOptions,
                workloadSource, serverPowerInputSource, null);
    }

    /**
     * Uses a supplied coupled system in place of the legacy temperature update.
     * Callers must not additionally register cooling thermal adapters, the
     * legacy TemperatureSystem, or the independent RoomAirTemperatureSystem.
     */
    public List<Simulatable> createInputSystems(
            DatacenterDefinition definition,
            Datacenter datacenter,
            TemperatureSystem temperatureSystem,
            TemperatureSystemOptions temperatureSystemOptions,
            WorkloadSource workloadSource,
            ServerPowerInputSource serverPowerInputSource,
            CoupledRoomTemperatureSystem coupledRoomSystem
    ) {
        validator.validate(definition);
        Objects.requireNonNull(datacenter, "datacenter must not be null");
        Objects.requireNonNull(temperatureSystem, "temperatureSystem must not be null");
        Objects.requireNonNull(temperatureSystemOptions, "temperatureSystemOptions must not be null");
        if ((definition.roomAirTemperature() != null) != (coupledRoomSystem != null))
            throw new IllegalArgumentException("roomAirTemperature configuration requires the coupled pipeline overload and matching system");
        if (coupledRoomSystem != null && (!coupledRoomSystem.uses(datacenter, temperatureSystem, temperatureSystemOptions,
                new RoomAirTemperatureOptionsFactory().create(definition).orElseThrow())
                || definition.dataInputMode() == com.cpz.sim.datacenter.input.DatacenterDataInputMode.TEMPERATURE_DRIVEN))
            throw new IllegalArgumentException("coupled room system must match the power-derived pipeline");
        if (coupledRoomSystem != null && !coupledRoomSystem.usesRackCooling(
                new RackCoolingConfigurationFactory().create(definition)))
            throw new IllegalArgumentException("coupled rack cooling must match the pipeline configuration");
        Simulatable thermalSystem = coupledRoomSystem == null ? temperatureSystem : coupledRoomSystem;
        List<Simulatable> systems = new ArrayList<>();
        switch (definition.dataInputMode()) {
            case UTILIZATION_DRIVEN -> {
                Objects.requireNonNull(workloadSource, "workloadSource must not be null for UTILIZATION_DRIVEN");
                systems.add(new WorkloadSystem(datacenter, workloadSource));
                systems.add(new PowerConsumptionSystem(datacenter));
                systems.add(thermalSystem);
            }
            case POWER_DRIVEN -> {
                Objects.requireNonNull(serverPowerInputSource, "serverPowerInputSource must not be null for POWER_DRIVEN");
                systems.add(new PowerInputSystem(datacenter, serverPowerInputSource));
                systems.add(thermalSystem);
            }
            case TEMPERATURE_DRIVEN -> {
                RackTemperatureInputSource rackTemperatureInputSource =
                        temperatureInputSourceFactory.create(definition);
                systems.add(new RackTemperatureInputSystem(
                        datacenter,
                        temperatureSystem,
                        rackTemperatureInputSource,
                        temperatureSystemOptions
                ));
            }
        }
        return List.copyOf(systems);
    }

    public void registerInputSystems(
            SimulationEngine engine,
            DatacenterDefinition definition,
            Datacenter datacenter,
            TemperatureSystem temperatureSystem,
            TemperatureSystemOptions temperatureSystemOptions,
            WorkloadSource workloadSource,
            ServerPowerInputSource serverPowerInputSource
    ) {
        registerInputSystems(engine, definition, datacenter, temperatureSystem, temperatureSystemOptions,
                workloadSource, serverPowerInputSource, null);
    }

    public void registerInputSystems(
            SimulationEngine engine,
            DatacenterDefinition definition,
            Datacenter datacenter,
            TemperatureSystem temperatureSystem,
            TemperatureSystemOptions temperatureSystemOptions,
            WorkloadSource workloadSource,
            ServerPowerInputSource serverPowerInputSource,
            CoupledRoomTemperatureSystem coupledRoomSystem
    ) {
        Objects.requireNonNull(engine, "engine must not be null");
        for (Simulatable system : createInputSystems(
                definition,
                datacenter,
                temperatureSystem,
                temperatureSystemOptions,
                workloadSource,
                serverPowerInputSource,
                coupledRoomSystem
        )) {
            engine.register(system);
        }
    }
}

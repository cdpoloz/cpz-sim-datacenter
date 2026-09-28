package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.input.RackTemperatureInputSource;
import com.cpz.sim.datacenter.input.ServerPowerInputSource;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
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
        validator.validate(definition);
        Objects.requireNonNull(datacenter, "datacenter must not be null");
        Objects.requireNonNull(temperatureSystem, "temperatureSystem must not be null");
        Objects.requireNonNull(temperatureSystemOptions, "temperatureSystemOptions must not be null");
        List<Simulatable> systems = new ArrayList<>();
        switch (definition.dataInputMode()) {
            case UTILIZATION_DRIVEN -> {
                Objects.requireNonNull(workloadSource, "workloadSource must not be null for UTILIZATION_DRIVEN");
                systems.add(new WorkloadSystem(datacenter, workloadSource));
                systems.add(new PowerConsumptionSystem(datacenter));
                systems.add(temperatureSystem);
            }
            case POWER_DRIVEN -> {
                Objects.requireNonNull(serverPowerInputSource, "serverPowerInputSource must not be null for POWER_DRIVEN");
                systems.add(new PowerInputSystem(datacenter, serverPowerInputSource));
                systems.add(temperatureSystem);
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
        Objects.requireNonNull(engine, "engine must not be null");
        for (Simulatable system : createInputSystems(
                definition,
                datacenter,
                temperatureSystem,
                temperatureSystemOptions,
                workloadSource,
                serverPowerInputSource
        )) {
            engine.register(system);
        }
    }
}

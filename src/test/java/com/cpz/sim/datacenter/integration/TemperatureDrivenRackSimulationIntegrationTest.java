package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.input.MapRackTemperatureInputSource;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshot;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshotProvider;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.RackTemperatureInputSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemperatureDrivenRackSimulationIntegrationTest {

    private static final RackCode RACK_CODE = new RackCode("RACK-A01-R01");
    private static final RackLocation RACK_LOCATION = new RackLocation("A01", RACK_CODE);
    private static final TemperatureSystemOptions OPTIONS =
            new TemperatureSystemOptions(25.0, 25.0, 5000.0, 8.0);

    @Test
    void shouldRunTemperatureDrivenRackPipelineAndExposeSnapshots() {
        Datacenter datacenter = datacenter();
        TemperatureSystem temperatureSystem =
                new TemperatureSystem(datacenter, OPTIONS, new SimpleServerTemperatureModel());
        RackTemperatureInputSystem inputSystem =
                new RackTemperatureInputSystem(
                        datacenter,
                        temperatureSystem,
                        new MapRackTemperatureInputSource(Map.of(RACK_LOCATION, 55.0), 25.0),
                        OPTIONS
                );
        EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(1)));
        engine.register(inputSystem);
        engine.register(energySystem);

        SimulationTick tick = engine.step();

        TemperatureSnapshot temperatureSnapshot =
                new TemperatureSnapshotProvider(datacenter, temperatureSystem, OPTIONS).snapshot(tick);
        EnergyConsumptionSnapshot energySnapshot =
                new EnergyConsumptionSnapshotProvider(datacenter, energySystem).snapshot(tick);
        assertEquals(1L, tick.index());
        assertEquals(55.0, temperatureSnapshot.servers().getFirst().temperatureCelsius(), 0.0001);
        assertEquals(200.0, temperatureSnapshot.servers().getFirst().currentPowerWatts(), 0.0001);
        assertEquals(0.5, temperatureSnapshot.servers().getFirst().utilization(), 0.0001);
        assertEquals(200.0f, energySnapshot.totalItPowerWatts());
        assertEquals(200.0 / 60.0, energySnapshot.consumedEnergyWh(), 0.0001);
    }

    private static Datacenter datacenter() {
        Rack rack = new Rack(RACK_CODE, RACK_LOCATION, List.of("U01"));
        Server server =
                new Server(
                        new ServerLocation("A01", RACK_CODE, "U01"),
                        new ServerConfig("MODEL-01", "CPZ", "Temperature Test Server", 100.0f, 300.0f),
                        HardwareStatus.OK,
                        ServerRole.GENERAL_PURPOSE
                );
        return new Datacenter(List.of(rack), List.of(server));
    }
}

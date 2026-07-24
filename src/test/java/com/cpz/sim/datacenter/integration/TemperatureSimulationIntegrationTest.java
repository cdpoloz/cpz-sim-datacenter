package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshot;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.ServerTemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshotProvider;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * @author CPZ
 */
class TemperatureSimulationIntegrationTest {

    private static final double EPSILON = 0.000001;

    @Test
    void shouldCaptureEnergyAndTemperatureSnapshotsFromSameSimulationTick() {
        Datacenter datacenter = createDatacenter();
        TemperatureSystemOptions temperatureOptions = new TemperatureSystemOptions(
                24.0,
                30.0,
                5000.0,
                8.0
        );
        WorkloadSystem workloadSystem = new WorkloadSystem(
                datacenter,
                fixedWorkload(0.75f)
        );
        PowerConsumptionSystem powerConsumptionSystem = new PowerConsumptionSystem(datacenter);
        TemperatureSystem temperatureSystem = new TemperatureSystem(
                datacenter,
                temperatureOptions,
                new SimpleServerTemperatureModel()
        );
        EnergyConsumptionSystem energyConsumptionSystem =
                new EnergyConsumptionSystem(datacenter);
        SimulationEngine engine = new SimulationEngine(
                new SimulationClock(Duration.ofMinutes(5))
        );
        engine.register(workloadSystem);
        engine.register(powerConsumptionSystem);
        engine.register(temperatureSystem);
        engine.register(energyConsumptionSystem);
        SimulationTick tick = engine.step();
        EnergyConsumptionSnapshotProvider energySnapshotProvider =
                new EnergyConsumptionSnapshotProvider(datacenter, energyConsumptionSystem);
        TemperatureSnapshotProvider temperatureSnapshotProvider =
                new TemperatureSnapshotProvider(datacenter, temperatureSystem, temperatureOptions);
        EnergyConsumptionSnapshot energySnapshot =
                energySnapshotProvider.snapshot(tick);
        TemperatureSnapshot temperatureSnapshot =
                temperatureSnapshotProvider.snapshot(tick);
        assertEquals(tick.index(), energySnapshot.tickIndex());
        assertEquals(tick.index(), temperatureSnapshot.tickIndex());
        assertEquals(tick.elapsedSeconds(), energySnapshot.elapsedSeconds(), EPSILON);
        assertEquals(tick.elapsedSeconds(), temperatureSnapshot.elapsedSeconds(), EPSILON);
        assertEquals(3, energySnapshot.serverCount());
        assertEquals(3, temperatureSnapshot.serverCount());
        /*
         * Active servers:
         * idle = 100 W
         * max = 500 W
         * utilization = 0.75
         * power = 100 + 0.75 * (500 - 100) = 400 W
         *
         * OFFLINE server:
         * power = 0 W
         *
         * Total IT power = 400 + 400 + 0 = 800 W
         * Energy for 5 minutes = 800 W * (5 / 60) h = 66.666666 Wh
         */
        assertEquals(800.0, energySnapshot.totalItPowerWatts(), EPSILON);
        assertEquals(66.66666666666667, energySnapshot.consumedEnergyWh(), EPSILON);
        assertEquals(0.06666666666666667, energySnapshot.consumedEnergyKWh(), EPSILON);
        ServerTemperatureSnapshot firstServer =
                findServerTemperature(temperatureSnapshot, "A01-RACK-A01-R01-U01");
        ServerTemperatureSnapshot secondServer =
                findServerTemperature(temperatureSnapshot, "A01-RACK-A01-R01-U02");
        ServerTemperatureSnapshot offlineServer =
                findServerTemperature(temperatureSnapshot, "A01-RACK-A01-R01-U03");
        assertEquals(HardwareStatus.OK, firstServer.status());
        assertEquals(HardwareStatus.OK, secondServer.status());
        assertEquals(HardwareStatus.OFFLINE, offlineServer.status());
        assertEquals(0.75f, firstServer.utilization(), EPSILON);
        assertEquals(0.75f, secondServer.utilization(), EPSILON);
        assertEquals(0.0f, offlineServer.utilization(), EPSILON);
        assertEquals(400.0, firstServer.currentPowerWatts(), EPSILON);
        assertEquals(400.0, secondServer.currentPowerWatts(), EPSILON);
        assertEquals(0.0, offlineServer.currentPowerWatts(), EPSILON);
        /*
         * Active server temperature:
         * initial = 30 °C
         * ambient = 24 °C
         * heat loss = 8 * (30 - 24) = 48 W
         * net thermal power = 400 - 48 = 352 W
         * delta = 352 / 5000 * 300 = 21.12 °C
         * next = 51.12 °C
         */
        assertEquals(51.12, firstServer.temperatureCelsius(), EPSILON);
        assertEquals(51.12, secondServer.temperatureCelsius(), EPSILON);
        /*
         * OFFLINE server temperature:
         * initial = 30 °C
         * power = 0 W
         * heat loss = 8 * (30 - 24) = 48 W
         * delta = -48 / 5000 * 300 = -2.88 °C
         * next = 27.12 °C
         */
        assertEquals(27.12, offlineServer.temperatureCelsius(), EPSILON);
        assertTrue(offlineServer.temperatureCelsius() < 30.0);
        assertTrue(offlineServer.temperatureCelsius() > 24.0);
        assertEquals(43.12, temperatureSnapshot.averageTemperatureCelsius(), EPSILON);
        assertEquals(51.12, temperatureSnapshot.maxTemperatureCelsius(), EPSILON);
    }

    @Test
    void shouldRunFullSystemChainWithExplicitSxxSlots() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(
                rackCode,
                new RackLocation("A01", "R01"),
                List.of("S01", "S02", "S03", "S04")
        );
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );
        Datacenter datacenter = new Datacenter(
                List.of(rack),
                List.of(
                        new Server(new ServerLocation("A01", rackCode, "S01"), config, HardwareStatus.OK),
                        new Server(new ServerLocation("A01", rackCode, "S02"), config, HardwareStatus.OFFLINE)
                )
        );
        TemperatureSystemOptions temperatureOptions = new TemperatureSystemOptions(
                24.0,
                30.0,
                5000.0,
                8.0
        );
        TemperatureSystem temperatureSystem = new TemperatureSystem(
                datacenter,
                temperatureOptions,
                new SimpleServerTemperatureModel()
        );
        EnergyConsumptionSystem energyConsumptionSystem = new EnergyConsumptionSystem(datacenter);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(5)));
        engine.register(new WorkloadSystem(datacenter, fixedWorkload(0.5f)));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.register(temperatureSystem);
        engine.register(energyConsumptionSystem);
        SimulationTick tick = engine.step();
        TemperatureSnapshot temperatureSnapshot = new TemperatureSnapshotProvider(
                datacenter,
                temperatureSystem,
                temperatureOptions
        ).snapshot(tick);
        EnergyConsumptionSnapshot energySnapshot = new EnergyConsumptionSnapshotProvider(
                datacenter,
                energyConsumptionSystem
        ).snapshot(tick);
        assertEquals(2, temperatureSnapshot.serverCount());
        assertEquals(2, energySnapshot.serverCount());
        assertEquals("S01", findServerTemperature(temperatureSnapshot, "A01-RACK-A01-R01-S01").slot());
        assertEquals("S02", findServerTemperature(temperatureSnapshot, "A01-RACK-A01-R01-S02").slot());
        assertEquals("S01", energySnapshot.servers().getFirst().slot());
        assertEquals("S02", energySnapshot.servers().get(1).slot());
        assertEquals(300.0, energySnapshot.totalItPowerWatts(), EPSILON);
    }

    @Test
    void shouldRunFullSystemChainWithRepeatedRackCodesInDifferentColumns() {
        RackCode rackCode = new RackCode("R01");
        Rack firstRack = new Rack(rackCode, "C01", "R01", List.of("S01", "S02", "S03", "S04"));
        Rack secondRack = new Rack(rackCode, "C02", "R01", List.of("S01", "S02", "S03", "S04"));
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );
        Server firstServer = new Server(new ServerLocation("C01", rackCode, "S01"), config, HardwareStatus.OK);
        Server secondServer = new Server(new ServerLocation("C02", rackCode, "S01"), config, HardwareStatus.ALERT);
        Server offlineServer = new Server(new ServerLocation("C02", rackCode, "S02"), config, HardwareStatus.OFFLINE);
        Datacenter datacenter = new Datacenter(
                List.of(firstRack, secondRack),
                List.of(firstServer, secondServer, offlineServer)
        );
        TemperatureSystemOptions temperatureOptions = new TemperatureSystemOptions(
                24.0,
                30.0,
                5000.0,
                8.0
        );
        TemperatureSystem temperatureSystem = new TemperatureSystem(
                datacenter,
                temperatureOptions,
                new SimpleServerTemperatureModel()
        );
        EnergyConsumptionSystem energyConsumptionSystem = new EnergyConsumptionSystem(datacenter);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(5)));
        engine.register(new WorkloadSystem(datacenter, fixedWorkload(0.5f)));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.register(temperatureSystem);
        engine.register(energyConsumptionSystem);
        SimulationTick tick = engine.step();
        EnergyConsumptionSnapshot energySnapshot = new EnergyConsumptionSnapshotProvider(
                datacenter,
                energyConsumptionSystem
        ).snapshot(tick);
        TemperatureSnapshot temperatureSnapshot = new TemperatureSnapshotProvider(
                datacenter,
                temperatureSystem,
                temperatureOptions
        ).snapshot(tick);
        assertEquals(List.of(firstServer), datacenter.getServers("C01", "R01"));
        assertEquals(List.of(secondServer, offlineServer), datacenter.getServers("C02", "R01"));
        assertEquals(true, datacenter.getServer("C01", "R01", "S02").isEmpty());
        assertEquals(List.of("S01", "S02", "S03", "S04"), firstRack.getSlotCodes());
        assertEquals(3, energySnapshot.serverCount());
        assertEquals(3, temperatureSnapshot.serverCount());
        assertEquals("C01", energySnapshot.servers().getFirst().column());
        assertEquals("C02", energySnapshot.servers().get(1).column());
        assertEquals("C01", findServerTemperature(temperatureSnapshot, "C01-R01-S01").column());
        assertEquals("C02", findServerTemperature(temperatureSnapshot, "C02-R01-S01").column());
        assertEquals(HardwareStatus.OFFLINE, findServerTemperature(temperatureSnapshot, "C02-R01-S02").status());
        assertEquals(600.0, energySnapshot.totalItPowerWatts(), EPSILON);
        assertEquals(0.0, offlineServer.getCurrentPowerWatts(), EPSILON);
    }

    private static Datacenter createDatacenter() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(
                rackCode,
                new RackLocation("A01", "R01"),
                42
        );
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );
        Server firstServer = new Server(
                new ServerLocation("A01", rackCode, "U01"),
                config,
                HardwareStatus.OK
        );
        Server secondServer = new Server(
                new ServerLocation("A01", rackCode, "U02"),
                config,
                HardwareStatus.OK
        );
        Server offlineServer = new Server(
                new ServerLocation("A01", rackCode, "U03"),
                config,
                HardwareStatus.OFFLINE
        );
        return new Datacenter(
                List.of(rack),
                List.of(firstServer, secondServer, offlineServer)
        );
    }

    private static WorkloadSource fixedWorkload(double utilization) {
        return (server, tick) -> utilization;
    }

    private static ServerTemperatureSnapshot findServerTemperature(
            TemperatureSnapshot snapshot,
            String serverCode
    ) {
        return snapshot.servers()
                .stream()
                .filter(server -> server.serverCode().equals(serverCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing server snapshot: " + serverCode));
    }

}

package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author CPZ
 */
class EnergyConsumptionSnapshotProviderTest {

    private static final double EPSILON = 0.000001;

    private static Datacenter createDatacenter() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(rackCode, new RackLocation("A01", "R01"), 42);
        ServerConfig config = new ServerConfig(
                "SRV-DEMO-001",
                "CPZ",
                "Demo Server",
                100.0f,
                300.0f
        );
        Server firstServer = new Server(
                new ServerLocation("A01", rackCode, "U01"),
                config,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );
        Server secondServer = new Server(
                new ServerLocation("A01", rackCode, "U02"),
                config,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );
        return new Datacenter(List.of(rack), List.of(firstServer, secondServer));
    }

    @Test
    void shouldCreateEnergyConsumptionSnapshotAfterSimulationStep() {
        Datacenter datacenter = createDatacenter();
        WorkloadSystem workloadSystem = new WorkloadSystem(datacenter, new ConstantWorkloadSource(0.5f));
        PowerConsumptionSystem powerConsumptionSystem = new PowerConsumptionSystem(datacenter);
        EnergyConsumptionSystem energyConsumptionSystem = new EnergyConsumptionSystem(datacenter);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
        engine.register(workloadSystem);
        engine.register(powerConsumptionSystem);
        engine.register(energyConsumptionSystem);
        SimulationTick tick = engine.step();
        EnergyConsumptionSnapshotProvider provider = new EnergyConsumptionSnapshotProvider(datacenter, energyConsumptionSystem);
        EnergyConsumptionSnapshot snapshot = provider.snapshot(tick);
        assertEquals(1, snapshot.tickIndex());
        assertEquals(1800.0, snapshot.elapsedSeconds(), EPSILON);
        assertEquals(400.0, snapshot.totalItPowerWatts(), EPSILON);
        assertEquals(200.0, snapshot.consumedEnergyWh(), EPSILON);
        assertEquals(0.2, snapshot.consumedEnergyKWh(), EPSILON);
        assertEquals(2, snapshot.serverCount());
        ServerEnergySnapshot firstServer = snapshot.servers().getFirst();
        assertEquals("A01-RACK-A01-R01-U01", firstServer.serverCode());
        assertEquals("A01", firstServer.column());
        assertEquals(new RackCode("RACK-A01-R01"), firstServer.rackCode());
        assertEquals("U01", firstServer.slot());
        assertEquals(HardwareStatus.OK, firstServer.status());
        assertEquals(0.5f, firstServer.utilization(), EPSILON);
        assertEquals(100.0f, firstServer.idlePowerWatts(), EPSILON);
        assertEquals(300.0f, firstServer.maxPowerWatts(), EPSILON);
        assertEquals(new ServerLocation("A01", new RackCode("RACK-A01-R01"), "U01"), firstServer.location());
        assertEquals(200.0f, firstServer.currentPowerWatts(), EPSILON);
        ServerEnergySnapshot secondServer = snapshot.servers().get(1);
        assertEquals("A01-RACK-A01-R01-U02", secondServer.serverCode());
        assertEquals("A01", secondServer.column());
        assertEquals(new RackCode("RACK-A01-R01"), secondServer.rackCode());
        assertEquals("U02", secondServer.slot());
        assertEquals(HardwareStatus.OK, secondServer.status());
        assertEquals(0.5f, secondServer.utilization(), EPSILON);
        assertEquals(100.0f, secondServer.idlePowerWatts(), EPSILON);
        assertEquals(300.0f, secondServer.maxPowerWatts(), EPSILON);
        assertEquals(new ServerLocation("A01", new RackCode("RACK-A01-R01"), "U02"), secondServer.location());
        assertEquals(200.0f, secondServer.currentPowerWatts(), EPSILON);
    }

    @Test
    void shouldDistinguishSameRackCodeAndSlotInDifferentColumns() {
        RackCode rackCode = new RackCode("R01");
        Rack firstRack = new Rack(rackCode, "C01", "R01", List.of("S01"));
        Rack secondRack = new Rack(rackCode, "C02", "R01", List.of("S01"));
        ServerConfig config = new ServerConfig(
                "SRV-DEMO-001",
                "CPZ",
                "Demo Server",
                100.0f,
                300.0f
        );
        Server firstServer = new Server(
                new ServerLocation("C01", rackCode, "S01"),
                config,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );
        Server secondServer = new Server(
                new ServerLocation("C02", rackCode, "S01"),
                config,
                HardwareStatus.ALERT,
                ServerRole.GENERAL_PURPOSE
        );
        Datacenter datacenter = new Datacenter(List.of(firstRack, secondRack), List.of(firstServer, secondServer));
        EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);
        EnergyConsumptionSnapshot snapshot = new EnergyConsumptionSnapshotProvider(datacenter, energySystem)
                .snapshot(new SimulationTick(1, Duration.ZERO, Duration.ofSeconds(1)));
        assertEquals("C01-R01-S01", snapshot.servers().getFirst().serverCode());
        assertEquals("C01", snapshot.servers().getFirst().column());
        assertEquals(new RackCode("R01"), snapshot.servers().getFirst().rackCode());
        assertEquals("S01", snapshot.servers().getFirst().slot());
        assertEquals("C02-R01-S01", snapshot.servers().get(1).serverCode());
        assertEquals("C02", snapshot.servers().get(1).column());
        assertEquals(new RackCode("R01"), snapshot.servers().get(1).rackCode());
        assertEquals("S01", snapshot.servers().get(1).slot());
    }


}

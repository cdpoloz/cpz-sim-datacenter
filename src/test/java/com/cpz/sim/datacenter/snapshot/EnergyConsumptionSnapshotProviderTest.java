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
        Server firstServer = new Server(new ServerLocation(rackCode, "U01"), config, HardwareStatus.OK);
        Server secondServer = new Server(new ServerLocation(rackCode, "U02"), config, HardwareStatus.OK);
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
        assertEquals("RACK-A01-R01-U01", firstServer.serverCode());
        assertEquals("RACK-A01-R01", firstServer.rackCode());
        assertEquals("U01", firstServer.slot());
        assertEquals("OK", firstServer.status());
        assertEquals(0.5f, firstServer.utilization(), EPSILON);
        assertEquals(200.0f, firstServer.currentPowerWatts(), EPSILON);
        ServerEnergySnapshot secondServer = snapshot.servers().get(1);
        assertEquals("RACK-A01-R01-U02", secondServer.serverCode());
        assertEquals("RACK-A01-R01", secondServer.rackCode());
        assertEquals("U02", secondServer.slot());
        assertEquals("OK", secondServer.status());
        assertEquals(0.5f, secondServer.utilization(), EPSILON);
        assertEquals(200.0f, secondServer.currentPowerWatts(), EPSILON);
    }
}

package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author CPZ
 */
class EnergyConsumptionSystemTest {

    private static final double TOLERANCE = 1.0e-9;
    private Datacenter datacenter;
    private SimulationEngine engine;
    private EnergyConsumptionSystem energySystem;

    private static Server createServer(ServerConfig config, Slot slot) {
        return new Server(new ServerLocation(Column.A01, Row.R01, slot), config, HardwareStatus.OK);
    }

    @BeforeEach
    void setUp() {
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                300.0f
        );
        Server serverA = createServer(config, Slot.S01);
        Server serverB = createServer(config, Slot.S02);
        datacenter = new Datacenter(List.of(serverA, serverB));
        WorkloadSource source = (server, tick) -> 0.5f;
        energySystem = new EnergyConsumptionSystem(datacenter);
        engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
        engine.register(new WorkloadSystem(datacenter, source));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.register(energySystem);
    }

    @Test
    void shouldAccumulateEnergyFromCurrentPower() {
        engine.step();
        // Two servers at 200 W = 400 W for 0.5 h = 200 Wh.
        assertEquals(200.0, energySystem.getConsumedEnergyWh(), TOLERANCE);
    }

    @Test
    void shouldAccumulateEnergyAcrossMultipleSteps() {
        engine.step(4);
        // 400 W for 2 h = 800 Wh.
        assertEquals(800.0, energySystem.getConsumedEnergyWh(), TOLERANCE);
        assertEquals(0.8, energySystem.getConsumedEnergyKWh(), TOLERANCE);
    }

    @Test
    void shouldUseWorkloadAndPowerFromSameTick() {
        WorkloadSource source = (server, tick) -> tick.index() == 1 ? 0.0f : 1.0f;
        EnergyConsumptionSystem system = new EnergyConsumptionSystem(datacenter);
        SimulationEngine customEngine = new SimulationEngine(new SimulationClock(Duration.ofHours(1)));
        customEngine.register(new WorkloadSystem(datacenter, source));
        customEngine.register(new PowerConsumptionSystem(datacenter));
        customEngine.register(system);
        customEngine.step(); // 2 × 100 W × 1 h = 200 Wh
        customEngine.step(); // 2 × 300 W × 1 h = 600 Wh
        assertEquals(800.0, system.getConsumedEnergyWh(), TOLERANCE);
    }

    @Test
    void shouldResetAccumulatedEnergy() {
        engine.step(3);
        assertTrue(energySystem.getConsumedEnergyWh() > 0.0);
        engine.reset();
        assertEquals(0.0, energySystem.getConsumedEnergyWh(), TOLERANCE);
        assertEquals(0L, engine.currentTick().index());
    }

    @Test
    void shouldRejectNullDatacenter() {
        assertThrows(NullPointerException.class, () -> new EnergyConsumptionSystem(null));
    }

}

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
class WorkloadSystemTest {

    private Server serverA, serverB;
    private Datacenter datacenter;

    private static Server createServer(
            ServerConfig config,
            RackCode rackCode,
            String slot
    ) {
        return new Server(
                new ServerLocation(rackCode, slot),
                config,
                HardwareStatus.OK
        );
    }

    private SimulationEngine createEngine() {
        return new SimulationEngine(new SimulationClock(Duration.ofSeconds(1)));
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
        Rack rack = new Rack(new RackCode("RACK-A01-R01"), new RackLocation("A01", "R01"), 42);
        serverA = createServer(config, rack.getCode(), "U01");
        serverB = createServer(config, rack.getCode(), "U02");
        datacenter = new Datacenter(List.of(rack), List.of(serverA, serverB));
    }

    @Test
    void shouldAssignUtilizationFromSource() {
        WorkloadSource source = (server, tick) -> server == serverA ? 0.25f : 0.75f;
        WorkloadSystem system = new WorkloadSystem(datacenter, source);
        SimulationEngine engine = createEngine();
        engine.register(system);
        engine.step();
        assertEquals(0.25f, serverA.getUtilization());
        assertEquals(0.75f, serverB.getUtilization());
    }

    @Test
    void shouldProvideCurrentTickToSource() {
        WorkloadSource source = (server, tick) -> tick.index() == 1 ? 0.25f : 0.75f;
        SimulationEngine engine = createEngine();
        engine.register(new WorkloadSystem(datacenter, source));
        engine.step();
        assertEquals(0.25f, serverA.getUtilization());
        engine.step();
        assertEquals(0.75f, serverA.getUtilization());
    }

    @Test
    void shouldRejectInvalidUtilizationFromSource() {
        WorkloadSource source = (server, tick) -> 1.5f;
        SimulationEngine engine = createEngine();
        engine.register(new WorkloadSystem(datacenter, source));
        assertThrows(IllegalArgumentException.class, engine::step);
    }

    @Test
    void shouldRejectNullDependencies() {
        WorkloadSource source = (server, tick) -> 0.5f;
        assertAll(() -> assertThrows(
                        NullPointerException.class,
                        () -> new WorkloadSystem(null, source)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new WorkloadSystem(datacenter, null)
                )
        );
    }

    @Test
    void shouldApplyWorkloadBeforeCalculatingPower() {
        WorkloadSource source = (server, tick) -> server == serverA ? 0.5f : 1.0f;
        SimulationEngine engine = createEngine();
        engine.register(new WorkloadSystem(datacenter, source));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.step();
        assertEquals(0.5f, serverA.getUtilization());
        assertEquals(200.0f, serverA.getCurrentPowerWatts());
        assertEquals(1.0f, serverB.getUtilization());
        assertEquals(300.0f, serverB.getCurrentPowerWatts());
        assertEquals(500.0f, datacenter.getTotalItPowerWatts());
    }

    @Test
    void shouldRestoreZeroWorkloadAndIdlePowerOnReset() {
        WorkloadSource source = (server, tick) -> 1.0f;
        SimulationEngine engine = createEngine();
        engine.register(new WorkloadSystem(datacenter, source));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.step();
        assertEquals(600.0f, datacenter.getTotalItPowerWatts());
        engine.reset();
        assertEquals(0.0f, serverA.getUtilization());
        assertEquals(0.0f, serverB.getUtilization());
        assertEquals(200.0f, datacenter.getTotalItPowerWatts());
        assertEquals(0L, engine.currentTick().index());
    }

}

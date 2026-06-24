package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class PowerConsumptionSystemTest {

    private Server serverA;
    private Server serverB;
    private Datacenter datacenter;
    private SimulationEngine engine;

    private static Server createServer(
            ServerConfig config,
            Column column,
            Row row,
            Slot slot
    ) {
        return new Server(
                new ServerLocation(column, row, slot),
                config,
                HardwareStatus.OK
        );
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
        serverA = createServer(config, Column.A01, Row.R01, Slot.S01);
        serverB = createServer(config, Column.A01, Row.R01, Slot.S02);
        datacenter = new Datacenter(List.of(serverA, serverB));
        SimulationClock clock = new SimulationClock(Duration.ofSeconds(1));
        engine = new SimulationEngine(clock);
        engine.register(new PowerConsumptionSystem(datacenter));
    }

    @Test
    void shouldUpdateServerPowerWhenEngineAdvances() {
        serverA.setUtilization(0.5f);
        serverB.setUtilization(1.0f);
        // setUtilization() alone does not recalculate power
        assertEquals(100.0f, serverA.getCurrentPowerWatts());
        assertEquals(100.0f, serverB.getCurrentPowerWatts());
        engine.step();
        assertEquals(200.0f, serverA.getCurrentPowerWatts());
        assertEquals(300.0f, serverB.getCurrentPowerWatts());
        assertEquals(500.0f, datacenter.getTotalItPowerWatts());
    }

    @Test
    void shouldRecalculatePowerOnEveryStep() {
        serverA.setUtilization(0.25f);
        engine.step();
        assertEquals(150.0f, serverA.getCurrentPowerWatts());
        serverA.setUtilization(0.75f);
        engine.step();
        assertEquals(250.0f, serverA.getCurrentPowerWatts());
    }

    @Test
    void shouldSetOfflineServerPowerToZero() {
        serverA.setUtilization(1.0f);
        serverA.setStatus(HardwareStatus.OFFLINE);
        engine.step();
        assertEquals(0.0f, serverA.getCurrentPowerWatts());
    }

    @Test
    void shouldRejectNullDatacenter() {
        assertThrows(NullPointerException.class, () -> new PowerConsumptionSystem(null));
    }

}

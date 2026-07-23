package com.cpz.sim.datacenter.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class ServerTest {

    private Server server;

    @BeforeEach
    void setUp() {
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                300.0f
        );
        ServerLocation location = new ServerLocation("A01", new RackCode("RACK-A01-R01"), "U01");
        server = new Server(location, config, HardwareStatus.OK);
    }

    @Test
    void shouldStartWithIdlePowerCOnsumption() {
        assertEquals(0.0f, server.getUtilization());
        assertEquals(100.0f, server.getCurrentPowerWatts());
    }

    @Test
    void shouldCalculatePowerFromUtilization() {
        server.setUtilization(0.5f);
        server.updatePowerConsumption();
        assertEquals(200.0f, server.getCurrentPowerWatts());
    }

    @Test
    void shouldConsumeMaximumPowerAtFullUtilization() {
        server.setUtilization(1.0f);
        server.updatePowerConsumption();
        assertEquals(300.0f, server.getCurrentPowerWatts());
    }

    @Test
    void shouldConsumeNoPowerWhenOffline() {
        server.setUtilization(0.75f);
        server.setStatus(HardwareStatus.OFFLINE);
        server.updatePowerConsumption();
        assertEquals(0.0f, server.getCurrentPowerWatts());
    }

    @Test
    void shouldRejectUtilizationBelowZero() {
        assertThrows(IllegalArgumentException.class, () -> server.setUtilization(-0.01f));
    }

    @Test
    void shouldRejectUtilizationAboveOne() {
        assertThrows(IllegalArgumentException.class, () -> server.setUtilization(1.01f));
    }

    @Test
    void shouldExposeLocationCode() {
        assertEquals("A01-RACK-A01-R01-U01", server.getCode());
    }
}

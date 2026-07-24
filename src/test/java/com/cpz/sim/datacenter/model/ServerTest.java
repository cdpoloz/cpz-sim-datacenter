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
        server = new Server(location, config, HardwareStatus.OK, ServerRole.GENERAL_PURPOSE);
    }

    @Test
    void shouldStartWithIdlePowerCOnsumption() {
        assertEquals(0.0, server.getUtilization());
        assertEquals(100.0f, server.getCurrentPowerWatts());
    }

    @Test
    void shouldCalculatePowerFromUtilization() {
        server.setUtilization(0.5);
        server.updatePowerConsumption();
        assertEquals(200.0f, server.getCurrentPowerWatts());
    }

    @Test
    void shouldConsumeMaximumPowerAtFullUtilization() {
        server.setUtilization(1.0);
        server.updatePowerConsumption();
        assertEquals(300.0f, server.getCurrentPowerWatts());
    }

    @Test
    void shouldConsumeNoPowerWhenOffline() {
        server.setUtilization(0.75);
        server.setStatus(HardwareStatus.OFFLINE);
        server.updatePowerConsumption();
        assertEquals(0.0f, server.getCurrentPowerWatts());
    }

    @Test
    void shouldRejectUtilizationBelowZero() {
        assertThrows(IllegalArgumentException.class, () -> server.setUtilization(-0.01));
    }

    @Test
    void shouldRejectUtilizationAboveOne() {
        assertThrows(IllegalArgumentException.class, () -> server.setUtilization(1.01));
    }

    @Test
    void shouldExposeLocationCode() {
        assertEquals("A01-RACK-A01-R01-U01", server.getCode());
    }

    @Test
    void shouldExposeExplicitRole() {
        Server aiServer = new Server(
                server.getLocation(),
                server.getConfig(),
                HardwareStatus.OK,
                ServerRole.AI
        );

        assertEquals(ServerRole.AI, aiServer.getRole());
    }

    @Test
    void shouldRejectNullRole() {
        assertThrows(
                NullPointerException.class,
                () -> new Server(
                        server.getLocation(),
                        server.getConfig(),
                        HardwareStatus.OK,
                        null
                )
        );
    }
}

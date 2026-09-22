package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PowerInputSystemTest {

    @Test
    void shouldUpdateServerPowerFromInputSource() {
        Datacenter datacenter = datacenter(HardwareStatus.OK);
        Server server = datacenter.getServers().getFirst();
        PowerInputSystem system =
                new PowerInputSystem(datacenter, (ignored, tick) -> 250.0 + tick.index());

        system.update(tick(7L));

        assertEquals(257.0f, server.getCurrentPowerWatts());
    }

    @Test
    void shouldForceOfflineServerPowerToZero() {
        Datacenter datacenter = datacenter(HardwareStatus.OFFLINE);
        Server server = datacenter.getServers().getFirst();
        PowerInputSystem system =
                new PowerInputSystem(datacenter, (ignored, tick) -> 250.0);

        system.update(tick(1L));

        assertEquals(0.0f, server.getCurrentPowerWatts());
    }

    @Test
    void shouldRejectInvalidPowerFromInputSource() {
        Datacenter datacenter = datacenter(HardwareStatus.OK);
        PowerInputSystem system =
                new PowerInputSystem(datacenter, (ignored, tick) -> Double.NaN);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> system.update(tick(1L))
        );

        assertEquals("currentPowerWatts must be finite and >= 0", exception.getMessage());
    }

    @Test
    void shouldRejectNullDependencies() {
        Datacenter datacenter = datacenter(HardwareStatus.OK);

        assertThrows(
                NullPointerException.class,
                () -> new PowerInputSystem(null, (server, tick) -> 0.0)
        );
        assertThrows(
                NullPointerException.class,
                () -> new PowerInputSystem(datacenter, null)
        );
    }

    private static Datacenter datacenter(HardwareStatus status) {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(rackCode, new RackLocation("A01", "R01"), 42);
        ServerConfig config =
                new ServerConfig("model-01", "Example", "Server X", 100.0f, 300.0f);
        Server server =
                new Server(
                        new ServerLocation("A01", rackCode, "U01"),
                        config,
                        status,
                        ServerRole.GENERAL_PURPOSE
                );
        return new Datacenter(List.of(rack), List.of(server));
    }

    private static SimulationTick tick(long index) {
        return new SimulationTick(
                index,
                Duration.ofMinutes(index),
                Duration.ofMinutes(1)
        );
    }
}

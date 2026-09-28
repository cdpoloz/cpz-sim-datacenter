package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.input.RackPowerInputSource;
import com.cpz.sim.datacenter.input.RackPowerToServerPowerInputSource;
import com.cpz.sim.datacenter.input.ServerPowerInputSource;
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
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PowerInputSystemTest {

    private static final RackCode RACK_CODE = new RackCode("RACK-A01-R01");
    private static final RackLocation RACK_LOCATION = new RackLocation("A01", RACK_CODE);

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
    void shouldConsumeRackPowerAdapterAsServerPowerInputSource() {
        Datacenter datacenter = rackPowerDatacenter(
                server("U01", config("small", 100.0f, 300.0f), HardwareStatus.OK),
                server("U02", config("large", 100.0f, 500.0f), HardwareStatus.OK)
        );
        ServerPowerInputSource serverPowerInputSource =
                new RackPowerToServerPowerInputSource(
                        datacenter,
                        new MapRackPowerInputSource(Map.of(RACK_LOCATION, 450.0))
                );
        PowerInputSystem system = new PowerInputSystem(datacenter, serverPowerInputSource);

        system.update(tick(1L));

        Server small = datacenter.getServers().get(0);
        Server large = datacenter.getServers().get(1);
        assertEquals(150.0f, small.getCurrentPowerWatts());
        assertEquals(300.0f, large.getCurrentPowerWatts());
        assertEquals(0.25, small.getUtilization(), 0.0001);
        assertEquals(0.5, large.getUtilization(), 0.0001);
        assertEquals(450.0f, datacenter.getTotalItPowerWatts());
    }

    @Test
    void shouldClampRackPowerAdapterOutputToServerPhysicalCapacity() {
        Datacenter datacenter = rackPowerDatacenter(
                server("U01", config("small", 100.0f, 300.0f), HardwareStatus.OK),
                server("U02", config("large", 100.0f, 500.0f), HardwareStatus.OK),
                server("U03", config("offline", 100.0f, 400.0f), HardwareStatus.OFFLINE)
        );
        ServerPowerInputSource serverPowerInputSource =
                new RackPowerToServerPowerInputSource(
                        datacenter,
                        new MapRackPowerInputSource(Map.of(RACK_LOCATION, 1_200.0))
                );
        PowerInputSystem system = new PowerInputSystem(datacenter, serverPowerInputSource);

        system.update(tick(1L));

        Server small = datacenter.getServers().get(0);
        Server large = datacenter.getServers().get(1);
        Server offline = datacenter.getServers().get(2);
        assertEquals(300.0f, small.getCurrentPowerWatts());
        assertEquals(500.0f, large.getCurrentPowerWatts());
        assertEquals(0.0f, offline.getCurrentPowerWatts());
        assertEquals(1.0, small.getUtilization(), 0.0001);
        assertEquals(1.0, large.getUtilization(), 0.0001);
        assertEquals(0.0, offline.getUtilization(), 0.0001);
        // Rack input is 1_200 W, but server max power clamps usable power to 800 W.
        assertEquals(800.0f, datacenter.getTotalItPowerWatts());
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
        ServerConfig config =
                new ServerConfig("model-01", "Example", "Server X", 100.0f, 300.0f);
        Server server =
                new Server(
                        new ServerLocation("A01", RACK_CODE, "U01"),
                        config,
                        status,
                        ServerRole.GENERAL_PURPOSE
                );
        return rackPowerDatacenter(server);
    }

    private static Datacenter rackPowerDatacenter(Server... servers) {
        Rack rack = new Rack(RACK_CODE, RACK_LOCATION, List.of("U01", "U02", "U03"));
        return new Datacenter(List.of(rack), List.of(servers));
    }

    private static Server server(String slot, ServerConfig config, HardwareStatus status) {
        return new Server(
                new ServerLocation("A01", RACK_CODE, slot),
                config,
                status,
                ServerRole.GENERAL_PURPOSE
        );
    }

    private static ServerConfig config(String modelCode, float idlePowerWatts, float maxPowerWatts) {
        return new ServerConfig(modelCode, "Example", "Server X", idlePowerWatts, maxPowerWatts);
    }

    private static SimulationTick tick(long index) {
        return new SimulationTick(
                index,
                Duration.ofMinutes(index),
                Duration.ofMinutes(1)
        );
    }

    private record MapRackPowerInputSource(
            Map<RackLocation, Double> powerWattsByRackLocation
    ) implements RackPowerInputSource {

        private MapRackPowerInputSource {
            powerWattsByRackLocation = Map.copyOf(
                    Objects.requireNonNull(powerWattsByRackLocation, "powerWattsByRackLocation must not be null")
            );
        }

        @Override
        public double currentPowerWatts(RackLocation rackLocation, SimulationTick tick) {
            Objects.requireNonNull(rackLocation, "rackLocation must not be null");
            Objects.requireNonNull(tick, "tick must not be null");
            return powerWattsByRackLocation.getOrDefault(rackLocation, 0.0);
        }
    }
}

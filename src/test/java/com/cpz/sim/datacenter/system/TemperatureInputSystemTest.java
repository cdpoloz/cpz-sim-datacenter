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
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemperatureInputSystemTest {

    private static final double EPSILON = 0.000001;

    @Test
    void shouldUpdateServerTemperatureFromInputSource() {
        Datacenter datacenter = datacenter();
        Server server = datacenter.getServers().getFirst();
        TemperatureSystem temperatureSystem =
                new TemperatureSystem(
                        datacenter,
                        TemperatureSystemOptions.defaults(),
                        new SimpleServerTemperatureModel()
                );
        TemperatureInputSystem inputSystem =
                new TemperatureInputSystem(datacenter, temperatureSystem, (ignored, tick) -> 40.0 + tick.index());

        inputSystem.update(tick(2L));

        assertEquals(
                42.0,
                temperatureSystem.getThermalState(server.getCode()).getTemperatureCelsius(),
                EPSILON
        );
    }

    @Test
    void shouldRejectInvalidTemperatureFromInputSource() {
        Datacenter datacenter = datacenter();
        TemperatureSystem temperatureSystem =
                new TemperatureSystem(
                        datacenter,
                        TemperatureSystemOptions.defaults(),
                        new SimpleServerTemperatureModel()
                );
        TemperatureInputSystem inputSystem =
                new TemperatureInputSystem(datacenter, temperatureSystem, (ignored, tick) -> Double.POSITIVE_INFINITY);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> inputSystem.update(tick(1L))
        );

        assertEquals("temperatureCelsius must be finite", exception.getMessage());
    }

    @Test
    void shouldRejectNullDependencies() {
        Datacenter datacenter = datacenter();
        TemperatureSystem temperatureSystem =
                new TemperatureSystem(
                        datacenter,
                        TemperatureSystemOptions.defaults(),
                        new SimpleServerTemperatureModel()
                );

        assertThrows(
                NullPointerException.class,
                () -> new TemperatureInputSystem(null, temperatureSystem, (server, tick) -> 24.0)
        );
        assertThrows(
                NullPointerException.class,
                () -> new TemperatureInputSystem(datacenter, null, (server, tick) -> 24.0)
        );
        assertThrows(
                NullPointerException.class,
                () -> new TemperatureInputSystem(datacenter, temperatureSystem, null)
        );
    }

    private static Datacenter datacenter() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(rackCode, new RackLocation("A01", "R01"), 42);
        ServerConfig config =
                new ServerConfig("model-01", "Example", "Server X", 100.0f, 300.0f);
        Server server =
                new Server(
                        new ServerLocation("A01", rackCode, "U01"),
                        config,
                        HardwareStatus.OK,
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

package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.input.MapRackTemperatureInputSource;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RackTemperatureInputSystemTest {

    private static final RackCode RACK_CODE = new RackCode("RACK-A01-R01");
    private static final RackLocation RACK_LOCATION = new RackLocation("A01", RACK_CODE);
    private static final TemperatureSystemOptions OPTIONS =
            new TemperatureSystemOptions(25.0, 25.0, 5000.0, 8.0);

    @Test
    void shouldInferPowerAndUtilizationFromNominalRackTemperature() {
        Datacenter datacenter = datacenter(
                server("U01", HardwareStatus.OK),
                server("U02", HardwareStatus.OK)
        );
        TemperatureSystem temperatureSystem = temperatureSystem(datacenter);
        RackTemperatureInputSystem system = system(datacenter, temperatureSystem, 55.0, 25.0);

        system.update(tick());

        for (Server server : datacenter.getServers()) {
            assertEquals(55.0, temperatureSystem.getThermalState(server.getCode()).getTemperatureCelsius(), 0.0001);
            assertEquals(200.0f, server.getCurrentPowerWatts());
            assertEquals(0.5, server.getUtilization(), 0.0001);
        }
    }

    @Test
    void shouldClampTemperatureAtOrBelowAmbientToIdlePowerAndZeroUtilization() {
        Datacenter datacenter = datacenter(server("U01", HardwareStatus.OK));
        TemperatureSystem temperatureSystem = temperatureSystem(datacenter);
        RackTemperatureInputSystem system = system(datacenter, temperatureSystem, 20.0, 25.0);

        system.update(tick());

        Server server = datacenter.getServers().getFirst();
        assertEquals(20.0, temperatureSystem.getThermalState(server.getCode()).getTemperatureCelsius(), 0.0001);
        assertEquals(100.0f, server.getCurrentPowerWatts());
        assertEquals(0.0, server.getUtilization(), 0.0001);
    }

    @Test
    void shouldClampTemperatureAboveReferenceToMaxPowerAndMaxUtilization() {
        Datacenter datacenter = datacenter(server("U01", HardwareStatus.OK));
        TemperatureSystem temperatureSystem = temperatureSystem(datacenter);
        RackTemperatureInputSystem system = system(datacenter, temperatureSystem, 120.0, 25.0);

        system.update(tick());

        Server server = datacenter.getServers().getFirst();
        assertEquals(120.0, temperatureSystem.getThermalState(server.getCode()).getTemperatureCelsius(), 0.0001);
        assertEquals(300.0f, server.getCurrentPowerWatts());
        assertEquals(1.0, server.getUtilization(), 0.0001);
    }

    @Test
    void shouldUseDefaultTemperatureWhenRackHasNoExplicitValue() {
        Datacenter datacenter = datacenter(server("U01", HardwareStatus.OK));
        TemperatureSystem temperatureSystem = temperatureSystem(datacenter);
        RackTemperatureInputSystem system = new RackTemperatureInputSystem(
                datacenter,
                temperatureSystem,
                new MapRackTemperatureInputSource(Map.of(), 55.0),
                OPTIONS
        );

        system.update(tick());

        Server server = datacenter.getServers().getFirst();
        assertEquals(55.0, temperatureSystem.getThermalState(server.getCode()).getTemperatureCelsius(), 0.0001);
        assertEquals(200.0f, server.getCurrentPowerWatts());
        assertEquals(0.5, server.getUtilization(), 0.0001);
    }

    @Test
    void shouldNotInferDynamicPowerOrUtilizationForOfflineServers() {
        Datacenter datacenter = datacenter(
                server("U01", HardwareStatus.OK),
                server("U02", HardwareStatus.OFFLINE)
        );
        TemperatureSystem temperatureSystem = temperatureSystem(datacenter);
        RackTemperatureInputSystem system = system(datacenter, temperatureSystem, 55.0, 25.0);

        system.update(tick());

        Server online = datacenter.getServers().get(0);
        Server offline = datacenter.getServers().get(1);
        assertEquals(200.0f, online.getCurrentPowerWatts());
        assertEquals(0.5, online.getUtilization(), 0.0001);
        assertEquals(0.0f, offline.getCurrentPowerWatts());
        assertEquals(0.0, offline.getUtilization(), 0.0001);
        assertEquals(25.0, temperatureSystem.getThermalState(offline.getCode()).getTemperatureCelsius(), 0.0001);
    }

    @Test
    void shouldRejectInvalidObservedRackTemperature() {
        Datacenter datacenter = datacenter(server("U01", HardwareStatus.OK));
        TemperatureSystem temperatureSystem = temperatureSystem(datacenter);
        RackTemperatureInputSystem system = new RackTemperatureInputSystem(
                datacenter,
                temperatureSystem,
                (rackLocation, tick) -> Double.NaN,
                OPTIONS
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> system.update(tick())
        );

        assertEquals("observedRackTemperatureCelsius must be finite", exception.getMessage());
    }

    private static RackTemperatureInputSystem system(
            Datacenter datacenter,
            TemperatureSystem temperatureSystem,
            double observedTemperatureCelsius,
            double defaultTemperatureCelsius
    ) {
        return new RackTemperatureInputSystem(
                datacenter,
                temperatureSystem,
                new MapRackTemperatureInputSource(Map.of(RACK_LOCATION, observedTemperatureCelsius), defaultTemperatureCelsius),
                OPTIONS
        );
    }

    private static TemperatureSystem temperatureSystem(Datacenter datacenter) {
        return new TemperatureSystem(datacenter, OPTIONS, new SimpleServerTemperatureModel());
    }

    private static Datacenter datacenter(Server... servers) {
        Rack rack = new Rack(RACK_CODE, RACK_LOCATION, List.of("U01", "U02"));
        return new Datacenter(List.of(rack), List.of(servers));
    }

    private static Server server(String slot, HardwareStatus status) {
        return new Server(
                new ServerLocation("A01", RACK_CODE, slot),
                new ServerConfig("MODEL-01", "CPZ", "Temperature Test Server", 100.0f, 300.0f),
                status,
                ServerRole.GENERAL_PURPOSE
        );
    }

    private static SimulationTick tick() {
        return new SimulationTick(1L, Duration.ofMinutes(1), Duration.ofMinutes(1));
    }
}

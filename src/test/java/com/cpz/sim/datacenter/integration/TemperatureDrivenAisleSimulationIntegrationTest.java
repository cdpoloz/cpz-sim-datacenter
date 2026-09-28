package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.input.AisleTemperatureToRackTemperatureInputSource;
import com.cpz.sim.datacenter.input.MapAisleTemperatureInputSource;
import com.cpz.sim.datacenter.input.StandardHotAisleCodeResolver;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshotProvider;
import com.cpz.sim.datacenter.system.RackTemperatureInputSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemperatureDrivenAisleSimulationIntegrationTest {

    private static final RackCode FIRST_RACK_CODE = new RackCode("RACK-C02-R01");
    private static final RackCode SECOND_RACK_CODE = new RackCode("RACK-C03-R01");
    private static final RackLocation FIRST_RACK_LOCATION = new RackLocation("C02", FIRST_RACK_CODE);
    private static final RackLocation SECOND_RACK_LOCATION = new RackLocation("C03", SECOND_RACK_CODE);
    private static final TemperatureSystemOptions OPTIONS =
            new TemperatureSystemOptions(25.0, 25.0, 5000.0, 8.0);

    @Test
    void shouldRunTemperatureDrivenAislePipelineAndExposeSnapshots() {
        Datacenter datacenter = datacenter();
        TemperatureSystem temperatureSystem =
                new TemperatureSystem(datacenter, OPTIONS, new SimpleServerTemperatureModel());
        RackTemperatureInputSystem inputSystem =
                new RackTemperatureInputSystem(
                        datacenter,
                        temperatureSystem,
                        new AisleTemperatureToRackTemperatureInputSource(
                                new MapAisleTemperatureInputSource(Map.of("HA01", 55.0), 25.0),
                                rackLocation -> "HA01"
                        ),
                        OPTIONS
                );
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(1)));
        engine.register(inputSystem);

        SimulationTick tick = engine.step();

        TemperatureSnapshot snapshot =
                new TemperatureSnapshotProvider(datacenter, temperatureSystem, OPTIONS).snapshot(tick);
        assertEquals(1L, tick.index());
        assertEquals(2, snapshot.servers().size());
        assertEquals(55.0, snapshot.servers().get(0).temperatureCelsius(), 0.0001);
        assertEquals(55.0, snapshot.servers().get(1).temperatureCelsius(), 0.0001);
        assertEquals(200.0, snapshot.servers().get(0).currentPowerWatts(), 0.0001);
        assertEquals(200.0, snapshot.servers().get(1).currentPowerWatts(), 0.0001);
        assertEquals(0.5, snapshot.servers().get(0).utilization(), 0.0001);
        assertEquals(0.5, snapshot.servers().get(1).utilization(), 0.0001);
    }

    @Test
    void shouldAllowAislesToDriveDifferentRackTemperatures() {
        Datacenter datacenter = datacenter();
        TemperatureSystem temperatureSystem =
                new TemperatureSystem(datacenter, OPTIONS, new SimpleServerTemperatureModel());
        RackTemperatureInputSystem inputSystem =
                new RackTemperatureInputSystem(
                        datacenter,
                        temperatureSystem,
                        new AisleTemperatureToRackTemperatureInputSource(
                                new MapAisleTemperatureInputSource(Map.of("HA01", 55.0, "HA02", 67.0), 25.0),
                                rackLocation -> rackLocation.equals(FIRST_RACK_LOCATION) ? "HA01" : "HA02"
                        ),
                        OPTIONS
                );

        inputSystem.update(tick());

        List<Server> servers = datacenter.getServers();
        assertEquals(55.0, temperatureSystem.getThermalState(servers.get(0).getCode()).getTemperatureCelsius(), 0.0001);
        assertEquals(67.0, temperatureSystem.getThermalState(servers.get(1).getCode()).getTemperatureCelsius(), 0.0001);
    }

    @Test
    void shouldApplySameObservedTemperatureToRacksSharingStandardHotAisle() {
        Datacenter datacenter = standardAisleDatacenter();
        TemperatureSystem temperatureSystem =
                new TemperatureSystem(datacenter, OPTIONS, new SimpleServerTemperatureModel());
        RackTemperatureInputSystem inputSystem =
                new RackTemperatureInputSystem(
                        datacenter,
                        temperatureSystem,
                        new AisleTemperatureToRackTemperatureInputSource(
                                new MapAisleTemperatureInputSource(
                                        Map.of("HA01", 44.0, "HA02", 52.0, "HA03", 58.0, "HA04", 61.0, "HA05", 49.0),
                                        25.0
                                ),
                                new StandardHotAisleCodeResolver()
                        ),
                        OPTIONS
                );

        inputSystem.update(tick());

        assertEquals(44.0, temperatureSystem.getThermalState(serverCode("C01")).getTemperatureCelsius(), 0.0001);
        assertEquals(52.0, temperatureSystem.getThermalState(serverCode("C02")).getTemperatureCelsius(), 0.0001);
        assertEquals(52.0, temperatureSystem.getThermalState(serverCode("C03")).getTemperatureCelsius(), 0.0001);
        assertEquals(58.0, temperatureSystem.getThermalState(serverCode("C04")).getTemperatureCelsius(), 0.0001);
        assertEquals(58.0, temperatureSystem.getThermalState(serverCode("C05")).getTemperatureCelsius(), 0.0001);
        assertEquals(61.0, temperatureSystem.getThermalState(serverCode("C06")).getTemperatureCelsius(), 0.0001);
        assertEquals(61.0, temperatureSystem.getThermalState(serverCode("C07")).getTemperatureCelsius(), 0.0001);
        assertEquals(49.0, temperatureSystem.getThermalState(serverCode("C08")).getTemperatureCelsius(), 0.0001);
    }

    private static Datacenter datacenter() {
        Rack firstRack = new Rack(FIRST_RACK_CODE, FIRST_RACK_LOCATION, List.of("U01"));
        Rack secondRack = new Rack(SECOND_RACK_CODE, SECOND_RACK_LOCATION, List.of("U01"));
        Server firstServer =
                new Server(
                        new ServerLocation("C02", FIRST_RACK_CODE, "U01"),
                        new ServerConfig("MODEL-01", "CPZ", "Temperature Test Server", 100.0f, 300.0f),
                        HardwareStatus.OK,
                        ServerRole.GENERAL_PURPOSE
                );
        Server secondServer =
                new Server(
                        new ServerLocation("C03", SECOND_RACK_CODE, "U01"),
                        new ServerConfig("MODEL-02", "CPZ", "Temperature Test Server", 100.0f, 300.0f),
                        HardwareStatus.OK,
                        ServerRole.GENERAL_PURPOSE
                );
        return new Datacenter(List.of(firstRack, secondRack), List.of(firstServer, secondServer));
    }

    private static Datacenter standardAisleDatacenter() {
        return new Datacenter(
                List.of(
                        rack("C01"),
                        rack("C02"),
                        rack("C03"),
                        rack("C04"),
                        rack("C05"),
                        rack("C06"),
                        rack("C07"),
                        rack("C08")
                ),
                List.of(
                        server("C01"),
                        server("C02"),
                        server("C03"),
                        server("C04"),
                        server("C05"),
                        server("C06"),
                        server("C07"),
                        server("C08")
                )
        );
    }

    private static Rack rack(String column) {
        RackCode rackCode = rackCode(column);
        return new Rack(rackCode, new RackLocation(column, rackCode), List.of("U01"));
    }

    private static Server server(String column) {
        return new Server(
                new ServerLocation(column, rackCode(column), "U01"),
                new ServerConfig("MODEL-" + column, "CPZ", "Temperature Test Server", 100.0f, 300.0f),
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );
    }

    private static RackCode rackCode(String column) {
        return new RackCode("RACK-" + column + "-R01");
    }

    private static String serverCode(String column) {
        return column + "-" + rackCode(column).value() + "-U01";
    }

    private static SimulationTick tick() {
        return new SimulationTick(1L, Duration.ofMinutes(1), Duration.ofMinutes(1));
    }
}

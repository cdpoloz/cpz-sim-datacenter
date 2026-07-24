package com.cpz.sim.datacenter.temperature;


import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author CPZ
 */
class TemperatureSystemTest {

    private static final double EPSILON = 0.000001;
    private static final String SERVER_CODE = "A01-RACK-A01-R01-U01";

    @Test
    void rejectsNullDatacenter() {
        assertThrows(
                NullPointerException.class,
                () -> new TemperatureSystem(
                        null,
                        TemperatureSystemOptions.defaults(),
                        new SimpleServerTemperatureModel()
                )
        );
    }

    @Test
    void rejectsNullOptions() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        assertThrows(
                NullPointerException.class,
                () -> new TemperatureSystem(
                        datacenter,
                        null,
                        new SimpleServerTemperatureModel()
                )
        );
    }

    @Test
    void rejectsNullTemperatureModel() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        assertThrows(
                NullPointerException.class,
                () -> new TemperatureSystem(
                        datacenter,
                        TemperatureSystemOptions.defaults(),
                        null
                )
        );
    }

    @Test
    void rejectsNullTick() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                TemperatureSystemOptions.defaults(),
                new SimpleServerTemperatureModel()
        );
        assertThrows(NullPointerException.class, () -> system.update(null));
    }

    @Test
    void initializesThermalStateForInstalledServer() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                27.0,
                5000.0,
                8.0
        );
        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                options,
                new SimpleServerTemperatureModel()
        );
        ServerThermalState state = system.getThermalState(SERVER_CODE);
        assertNotNull(state);
        assertEquals(SERVER_CODE, state.getServerCode());
        assertEquals(27.0, state.getTemperatureCelsius(), EPSILON);
    }

    @Test
    void increasesTemperatureWhenServerHasPowerConsumption() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                25.0,
                5000.0,
                8.0
        );
        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                options,
                new SimpleServerTemperatureModel()
        );
        system.update(tickAtSeconds(1, 60, 60));
        ServerThermalState state = system.getThermalState(SERVER_CODE);
        assertNotNull(state);
        // Power = 100 + 0.75 * (500 - 100) = 400 W
        // Heat loss = 8 * (25 - 25) = 0 W
        // Delta = 400 / 5000 * 60 = 4.8 °C
        assertEquals(29.8, state.getTemperatureCelsius(), EPSILON);
    }

    @Test
    void offlineServerWithZeroPowerTendsTowardAmbientTemperature() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OFFLINE, 1.0f);
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                40.0,
                5000.0,
                8.0
        );
        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                options,
                new SimpleServerTemperatureModel()
        );
        system.update(tickAtSeconds(1, 60, 60));
        ServerThermalState state = system.getThermalState(SERVER_CODE);
        assertNotNull(state);
        // Power = 0 W because the server is OFFLINE.
        // Heat loss = 8 * (40 - 25) = 120 W
        // Delta = -120 / 5000 * 60 = -1.44 °C
        assertEquals(38.56, state.getTemperatureCelsius(), EPSILON);
        assertTrue(state.getTemperatureCelsius() < 40.0);
        assertTrue(state.getTemperatureCelsius() > 25.0);
    }

    private static Datacenter createDatacenterWithOneServer(
            HardwareStatus status,
            double utilization
    ) {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(rackCode, new RackLocation("A01", "R01"), 42);
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );
        Server server = new Server(
                new ServerLocation("A01", rackCode, "U01"),
                config,
                status,
                ServerRole.GENERAL_PURPOSE
        );
        server.setUtilization(utilization);
        server.updatePowerConsumption();
        return new Datacenter(List.of(rack), List.of(server));
    }

    private static SimulationTick tickAtSeconds(
            long index,
            long elapsedSeconds,
            long deltaSeconds
    ) {
        return new SimulationTick(
                index,
                Duration.ofSeconds(elapsedSeconds),
                Duration.ofSeconds(deltaSeconds)
        );
    }
}

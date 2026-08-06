package com.cpz.sim.datacenter.temperature;


import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.system.TemperatureSystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

    @Test
    void modelSpecificThermalPropertiesOverrideGlobalPropertiesPerServer() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(rackCode, new RackLocation("A01", "R01"), 42);
        Server slowerServer = createServer(
                rackCode,
                "U01",
                new ServerThermalProperties(10000.0, 4.0),
                HardwareStatus.OK,
                0.75
        );
        Server fasterServer = createServer(
                rackCode,
                "U02",
                new ServerThermalProperties(2500.0, 16.0),
                HardwareStatus.OK,
                0.75
        );
        TemperatureSystem system = new TemperatureSystem(
                new Datacenter(List.of(rack), List.of(slowerServer, fasterServer)),
                new TemperatureSystemOptions(25.0, 35.0, 5000.0, 8.0),
                new SimpleServerTemperatureModel()
        );

        system.update(tickAtSeconds(1, 60, 60));

        // Both servers consume 400 W and start 10 °C above ambient.
        // Slow: heat loss = 4 * 10 = 40 W; delta = 360 / 10000 * 60 = 2.16 °C.
        // Fast: heat loss = 16 * 10 = 160 W; delta = 240 / 2500 * 60 = 5.76 °C.
        assertEquals(37.16, system.getThermalState(slowerServer.getCode()).getTemperatureCelsius(), EPSILON);
        assertEquals(40.76, system.getThermalState(fasterServer.getCode()).getTemperatureCelsius(), EPSILON);
    }

    @Test
    void serverWithoutModelSpecificPropertiesUsesGlobalThermalProperties() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                new TemperatureSystemOptions(25.0, 25.0, 10000.0, 4.0),
                new SimpleServerTemperatureModel()
        );

        system.update(tickAtSeconds(1, 60, 60));

        assertEquals(
                27.4,
                system.getThermalState(SERVER_CODE).getTemperatureCelsius(),
                EPSILON
        );
    }

    @Test
    void offlineServerKeepsCoolingBehaviorWithModelSpecificProperties() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(rackCode, new RackLocation("A01", "R01"), 42);
        Server server = createServer(
                rackCode,
                "U01",
                new ServerThermalProperties(10000.0, 4.0),
                HardwareStatus.OFFLINE,
                1.0
        );
        TemperatureSystem system = new TemperatureSystem(
                new Datacenter(List.of(rack), List.of(server)),
                new TemperatureSystemOptions(25.0, 40.0, 5000.0, 8.0),
                new SimpleServerTemperatureModel()
        );

        system.update(tickAtSeconds(1, 60, 60));

        // OFFLINE still means 0 W. The model-specific loss is 4 * (40 - 25)
        // = 60 W, so delta = -60 / 10000 * 60 = -0.36 °C.
        assertEquals(
                39.64,
                system.getThermalState(server.getCode()).getTemperatureCelsius(),
                EPSILON
        );
    }

    @Test
    void usesDifferentReferenceTemperatureForEachServer() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(
                rackCode,
                new RackLocation("A01", "R01"),
                42
        );

        Server firstServer = createServer(
                rackCode,
                "U01",
                null,
                HardwareStatus.OFFLINE,
                0.0
        );
        Server secondServer = createServer(
                rackCode,
                "U02",
                null,
                HardwareStatus.OFFLINE,
                0.0
        );

        ServerTemperatureReferenceProvider referenceProvider =
                server -> server == firstServer ? 20.0 : 25.0;

        TemperatureSystem system = new TemperatureSystem(
                new Datacenter(
                        List.of(rack),
                        List.of(firstServer, secondServer)
                ),
                new TemperatureSystemOptions(
                        24.0,
                        30.0,
                        5000.0,
                        8.0
                ),
                new SimpleServerTemperatureModel(),
                referenceProvider
        );

        system.update(tickAtSeconds(1, 60, 60));

        // Both servers are OFFLINE, so their power is 0 W.
        //
        // First server:
        // Heat loss = 8 * (30 - 20) = 80 W
        // Delta = -80 / 5000 * 60 = -0.96 °C
        //
        // Second server:
        // Heat loss = 8 * (30 - 25) = 40 W
        // Delta = -40 / 5000 * 60 = -0.48 °C
        assertEquals(
                29.04,
                system.getThermalState(firstServer.getCode())
                        .getTemperatureCelsius(),
                EPSILON
        );
        assertEquals(
                29.52,
                system.getThermalState(secondServer.getCode())
                        .getTemperatureCelsius(),
                EPSILON
        );
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

    private static Server createServer(
            RackCode rackCode,
            String slot,
            ServerThermalProperties thermalProperties,
            HardwareStatus status,
            double utilization
    ) {
        ServerConfig config = new ServerConfig(
                "model-" + slot,
                "Example",
                "Server " + slot,
                100.0f,
                500.0f,
                thermalProperties
        );
        Server server = new Server(
                new ServerLocation("A01", rackCode, slot),
                config,
                status,
                ServerRole.GENERAL_PURPOSE
        );
        server.setUtilization(utilization);
        server.updatePowerConsumption();
        return server;
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

    @Test
    void rejectsNullTemperatureReferenceProvider() {
        Datacenter datacenter =
                createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new TemperatureSystem(
                        datacenter,
                        TemperatureSystemOptions.defaults(),
                        new SimpleServerTemperatureModel(),
                        null
                )
        );

        assertEquals(
                "temperatureReferenceProvider must not be null.",
                exception.getMessage()
        );
    }

    @Test
    void usesUpdatedReferenceTemperatureOnEachTick() {
        Datacenter datacenter =
                createDatacenterWithOneServer(HardwareStatus.OFFLINE, 0.0);

        Server server = datacenter.getServers().getFirst();

        AtomicReference<Double> referenceTemperature =
                new AtomicReference<>(20.0);

        ServerTemperatureReferenceProvider referenceProvider =
                ignored -> referenceTemperature.get();

        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                new TemperatureSystemOptions(
                        24.0,
                        30.0,
                        5000.0,
                        8.0
                ),
                new SimpleServerTemperatureModel(),
                referenceProvider
        );

        system.update(tickAtSeconds(1, 60, 60));

        assertEquals(
                29.04,
                system.getThermalState(server.getCode())
                        .getTemperatureCelsius(),
                EPSILON
        );

        referenceTemperature.set(25.0);

        system.update(tickAtSeconds(2, 120, 60));

        assertEquals(
                28.65216,
                system.getThermalState(server.getCode())
                        .getTemperatureCelsius(),
                EPSILON
        );
    }

    @Test
    void consultsReferenceProviderOncePerServerAndTick() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(
                rackCode,
                new RackLocation("A01", "R01"),
                42
        );

        Server firstServer = createServer(
                rackCode,
                "U01",
                null,
                HardwareStatus.OFFLINE,
                0.0
        );
        Server secondServer = createServer(
                rackCode,
                "U02",
                null,
                HardwareStatus.OFFLINE,
                0.0
        );

        AtomicInteger invocationCount = new AtomicInteger();

        ServerTemperatureReferenceProvider referenceProvider = server -> {
            invocationCount.incrementAndGet();
            return 24.0;
        };

        TemperatureSystem system = new TemperatureSystem(
                new Datacenter(
                        List.of(rack),
                        List.of(firstServer, secondServer)
                ),
                TemperatureSystemOptions.defaults(),
                new SimpleServerTemperatureModel(),
                referenceProvider
        );

        assertEquals(0, invocationCount.get());

        system.update(tickAtSeconds(1, 60, 60));

        assertEquals(2, invocationCount.get());

        system.update(tickAtSeconds(2, 120, 60));

        assertEquals(4, invocationCount.get());
    }

    @Test
    void rejectsNonFiniteReferenceTemperature() {
        List<Double> nonFiniteTemperatures = List.of(
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        );

        for (double nonFiniteTemperature : nonFiniteTemperatures) {
            Datacenter datacenter =
                    createDatacenterWithOneServer(
                            HardwareStatus.OFFLINE,
                            0.0
                    );

            Server server = datacenter.getServers().getFirst();

            ServerTemperatureReferenceProvider referenceProvider =
                    ignored -> nonFiniteTemperature;

            TemperatureSystem system = new TemperatureSystem(
                    datacenter,
                    TemperatureSystemOptions.defaults(),
                    new SimpleServerTemperatureModel(),
                    referenceProvider
            );

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> system.update(
                            tickAtSeconds(1, 60, 60)
                    )
            );

            assertEquals(
                    "temperatureReferenceProvider returned a non-finite "
                            + "temperature for server: "
                            + server.getCode(),
                    exception.getMessage()
            );
        }
    }
}

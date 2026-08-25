package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.system.CoolingSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class CoolingSnapshotCoordinatorTest {

    private static final double EPSILON = 0.000001;

    private static final ServerLocation SERVER_LOCATION =
            new ServerLocation(
                    "A01",
                    new RackCode("RACK-A01-R01"),
                    "U01"
            );

    @Test
    void rejectsNullInputProvider() {
        CoolingConfiguration configuration =
                createCoolingConfiguration();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new CoolingSnapshotCoordinator(
                        null,
                        new CoolingSystem(configuration),
                        new CoolingSnapshotTemperatureReferenceProvider(
                                configuration
                        )
                )
        );

        assertEquals(
                "inputProvider must not be null",
                exception.getMessage()
        );
    }

    @Test
    void rejectsNullCoolingSystem() {
        Datacenter datacenter = createDatacenter();
        CoolingConfiguration configuration =
                createCoolingConfiguration();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new CoolingSnapshotCoordinator(
                        new DatacenterCoolingTickInputProvider(datacenter),
                        null,
                        new CoolingSnapshotTemperatureReferenceProvider(
                                configuration
                        )
                )
        );

        assertEquals(
                "coolingSystem must not be null",
                exception.getMessage()
        );
    }

    @Test
    void rejectsNullTemperatureReferenceProvider() {
        Datacenter datacenter = createDatacenter();
        CoolingConfiguration configuration =
                createCoolingConfiguration();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new CoolingSnapshotCoordinator(
                        new DatacenterCoolingTickInputProvider(datacenter),
                        new CoolingSystem(configuration),
                        null
                )
        );

        assertEquals(
                "temperatureReferenceProvider must not be null",
                exception.getMessage()
        );
    }

    @Test
    void rejectsNullTick() {
        CoolingSnapshotCoordinator coordinator =
                createCoordinator(createDatacenter());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> coordinator.update(null)
        );

        assertEquals(
                "tick must not be null",
                exception.getMessage()
        );
    }

    @Test
    void executesCoolingAndPropagatesGeneratedSnapshot() {
        Datacenter datacenter = createDatacenter();
        Server server = datacenter.getServers().getFirst();

        server.setUtilization(0.75);

        CoolingConfiguration configuration =
                createCoolingConfiguration();

        CoolingSnapshotTemperatureReferenceProvider referenceProvider =
                new CoolingSnapshotTemperatureReferenceProvider(
                        configuration
                );

        CoolingSnapshotCoordinator coordinator =
                new CoolingSnapshotCoordinator(
                        new DatacenterCoolingTickInputProvider(datacenter),
                        new CoolingSystem(configuration),
                        referenceProvider
                );

        SimulationTick tick = new SimulationTick(
                7L,
                Duration.ofMinutes(7),
                Duration.ofMinutes(1)
        );

        new PowerConsumptionSystem(datacenter).update(tick);

        CoolingSnapshot snapshot = coordinator.update(tick);
        CoolingZoneSnapshot zoneSnapshot =
                snapshot.zones().getFirst();

        assertEquals(7L, snapshot.tickIndex());

        assertEquals(
                400.0,
                zoneSnapshot.generatedHeatWatts(),
                EPSILON
        );

        assertEquals(
                18.6,
                zoneSnapshot.inletAirTemperatureCelsius(),
                EPSILON
        );

        assertEquals(
                18.6,
                referenceProvider.temperatureCelsiusFor(server),
                EPSILON
        );
    }

    private static CoolingSnapshotCoordinator createCoordinator(
            Datacenter datacenter
    ) {
        CoolingConfiguration configuration =
                createCoolingConfiguration();

        return new CoolingSnapshotCoordinator(
                new DatacenterCoolingTickInputProvider(datacenter),
                new CoolingSystem(configuration),
                new CoolingSnapshotTemperatureReferenceProvider(
                        configuration
                )
        );
    }

    private static Datacenter createDatacenter() {
        RackCode rackCode =
                new RackCode("RACK-A01-R01");

        Rack rack = new Rack(
                rackCode,
                new RackLocation("A01", "R01"),
                42
        );

        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );

        Server server = new Server(
                SERVER_LOCATION,
                config,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );

        return new Datacenter(
                List.of(rack),
                List.of(server)
        );
    }

    private static CoolingConfiguration createCoolingConfiguration() {
        CoolingZoneDefinition zone =
                new CoolingZoneDefinition(
                        "ZONE-01",
                        Set.of(SERVER_LOCATION)
                );

        SupplyCoolingUnitDefinition supply =
                new SupplyCoolingUnitDefinition(
                        "SUPPLY-01",
                        4.0,
                        12_000.0,
                        18.6,
                        List.of(
                                new CoolingZoneInfluence(
                                        zone.code(),
                                        1.0
                                )
                        ),
                        true
                );

        ExhaustCoolingUnitDefinition exhaust =
                new ExhaustCoolingUnitDefinition(
                        "EXHAUST-01",
                        4.0,
                        List.of(
                                new CoolingZoneInfluence(
                                        zone.code(),
                                        1.0
                                )
                        ),
                        true
                );

        return new CoolingConfiguration(
                List.of(zone),
                List.of(supply, exhaust),
                CoolingSystemOptions.defaults()
        );
    }
}
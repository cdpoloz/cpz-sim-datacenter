package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSystemOptions;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cpz.sim.datacenter.cooling.CoolingUnitType;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingUnitSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author CPZ
 */
class CoolingSnapshotTemperatureReferenceProviderTest {

    private static final ServerLocation SERVER_LOCATION =
            new ServerLocation(
                    "A01",
                    new RackCode("RACK-A01-R01"),
                    "U01"
            );

    @Test
    void rejectsNullConfiguration() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new CoolingSnapshotTemperatureReferenceProvider(null)
        );

        assertEquals(
                "configuration must not be null",
                exception.getMessage()
        );
    }

    @Test
    void startsWithoutCoolingSnapshot() {
        CoolingSnapshotTemperatureReferenceProvider provider =
                new CoolingSnapshotTemperatureReferenceProvider(
                        createConfiguration()
                );

        assertNull(provider.currentSnapshot());
    }

    @Test
    void rejectsTemperatureRequestBeforeSnapshotIsInitialized() {
        CoolingSnapshotTemperatureReferenceProvider provider =
                new CoolingSnapshotTemperatureReferenceProvider(
                        createConfiguration()
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.temperatureCelsiusFor(createServer())
        );

        assertEquals(
                "cooling snapshot has not been initialized",
                exception.getMessage()
        );
    }

    private static CoolingConfiguration createConfiguration() {
        CoolingZoneDefinition zone = new CoolingZoneDefinition(
                "ZONE-01",
                Set.of(SERVER_LOCATION)
        );

        SupplyCoolingUnitDefinition supply =
                new SupplyCoolingUnitDefinition(
                        "SUPPLY-01",
                        10.0,
                        20_000.0,
                        18.0,
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
                List.of(supply),
                CoolingSystemOptions.defaults()
        );
    }

    private static Server createServer() {
        return createServer(SERVER_LOCATION);
    }

    private static Server createServer(ServerLocation location) {
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );

        return new Server(
                location,
                config,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );
    }

    @Test
    void returnsInletAirTemperatureFromAssignedCoolingZone() {
        CoolingSnapshotTemperatureReferenceProvider provider =
                new CoolingSnapshotTemperatureReferenceProvider(
                        createConfiguration()
                );

        CoolingSnapshot snapshot = createSnapshot(
                "ZONE-01",
                19.5
        );

        provider.updateSnapshot(snapshot);

        assertSame(snapshot, provider.currentSnapshot());

        assertEquals(
                19.5,
                provider.temperatureCelsiusFor(createServer())
        );
    }


    private static CoolingSnapshot createSnapshot(
            String zoneCode,
            double inletAirTemperatureCelsius
    ) {
        CoolingUnitSnapshot unitSnapshot =
                new CoolingUnitSnapshot(
                        "SUPPLY-01",
                        CoolingUnitType.SUPPLY,
                        true,
                        10.0,
                        20_000.0
                );

        CoolingZoneSnapshot zoneSnapshot =
                new CoolingZoneSnapshot(
                        zoneCode,
                        5_000.0,
                        20_000.0,
                        5_000.0,
                        0.0,
                        10.0,
                        8.0,
                        inletAirTemperatureCelsius,
                        inletAirTemperatureCelsius + 5.0,
                        0.1
                );

        return new CoolingSnapshot(
                1L,
                List.of(unitSnapshot),
                List.of(zoneSnapshot)
        );
    }

    @Test
    void rejectsNullSnapshot() {
        CoolingSnapshotTemperatureReferenceProvider provider =
                new CoolingSnapshotTemperatureReferenceProvider(
                        createConfiguration()
                );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> provider.updateSnapshot(null)
        );

        assertEquals(
                "snapshot must not be null",
                exception.getMessage()
        );

        assertNull(provider.currentSnapshot());
    }

    @Test
    void replacesCurrentSnapshotAndUsesItsTemperature() {
        CoolingSnapshotTemperatureReferenceProvider provider =
                new CoolingSnapshotTemperatureReferenceProvider(
                        createConfiguration()
                );

        CoolingSnapshot firstSnapshot = createSnapshot(
                "ZONE-01",
                19.5
        );

        CoolingSnapshot secondSnapshot = createSnapshot(
                "ZONE-01",
                22.0
        );

        provider.updateSnapshot(firstSnapshot);

        assertSame(firstSnapshot, provider.currentSnapshot());
        assertEquals(
                19.5,
                provider.temperatureCelsiusFor(createServer())
        );

        provider.updateSnapshot(secondSnapshot);

        assertSame(secondSnapshot, provider.currentSnapshot());
        assertEquals(
                22.0,
                provider.temperatureCelsiusFor(createServer())
        );
    }

    @Test
    void rejectsServerLocationNotAssignedToCoolingZone() {
        CoolingSnapshotTemperatureReferenceProvider provider =
                new CoolingSnapshotTemperatureReferenceProvider(
                        createConfiguration()
                );

        provider.updateSnapshot(
                createSnapshot(
                        "ZONE-01",
                        19.5
                )
        );

        ServerLocation unassignedLocation = new ServerLocation(
                "A01",
                new RackCode("RACK-A01-R01"),
                "U02"
        );

        Server unassignedServer = createServer(unassignedLocation);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.temperatureCelsiusFor(unassignedServer)
        );

        assertEquals(
                "server location is not assigned to a cooling zone: "
                        + unassignedLocation,
                exception.getMessage()
        );
    }
}
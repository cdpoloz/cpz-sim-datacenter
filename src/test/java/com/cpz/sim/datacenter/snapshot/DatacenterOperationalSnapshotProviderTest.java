package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author CPZ
 */
class DatacenterOperationalSnapshotProviderTest {

    private static final double EPSILON = 0.000001;
    private static final long TICK_INDEX = 10L;
    private static final double ELAPSED_SECONDS = 600.0;
    private static final RackCode POPULATED_RACK_CODE = new RackCode("R01");
    private static final RackCode EMPTY_RACK_CODE = new RackCode("R02");
    private static final RackLocation POPULATED_RACK_LOCATION = new RackLocation("C01", POPULATED_RACK_CODE);
    private static final RackLocation EMPTY_RACK_LOCATION = new RackLocation("C01", EMPTY_RACK_CODE);
    private static final ServerLocation OK_SERVER_LOCATION = new ServerLocation("C01", POPULATED_RACK_CODE, "S01");
    private static final ServerLocation ALERT_SERVER_LOCATION = new ServerLocation("C01", POPULATED_RACK_CODE, "S02");
    private static final ServerLocation OFFLINE_SERVER_LOCATION = new ServerLocation("C01", POPULATED_RACK_CODE, "S03");

    private static Datacenter createDatacenter() {
        Rack populatedRack = new Rack(
                POPULATED_RACK_CODE,
                "C01",
                "R01",
                List.of("S01", "S02", "S03")
        );

        Rack emptyRack = new Rack(
                EMPTY_RACK_CODE,
                "C01",
                "R02",
                List.of("S01", "S02", "S03")
        );

        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );

        Server okServer = new Server(
                OK_SERVER_LOCATION,
                config,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );

        Server alertServer = new Server(
                ALERT_SERVER_LOCATION,
                config,
                HardwareStatus.ALERT,
                ServerRole.AI
        );

        Server offlineServer = new Server(
                OFFLINE_SERVER_LOCATION,
                config,
                HardwareStatus.OFFLINE,
                ServerRole.GENERAL_PURPOSE
        );

        return new Datacenter(
                List.of(populatedRack, emptyRack),
                List.of(
                        okServer,
                        alertServer,
                        offlineServer
                )
        );
    }

    private static EnergyConsumptionSnapshot createEnergySnapshot() {
        ServerEnergySnapshot okServer =
                new ServerEnergySnapshot(
                        "C01-R01-S01",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S01",
                        HardwareStatus.OK.name(),
                        0.50,
                        100.0f,
                        500.0f,
                        300.0f
                );

        ServerEnergySnapshot alertServer =
                new ServerEnergySnapshot(
                        "C01-R01-S02",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S02",
                        HardwareStatus.ALERT.name(),
                        0.90,
                        100.0f,
                        500.0f,
                        460.0f
                );

        ServerEnergySnapshot offlineServer =
                new ServerEnergySnapshot(
                        "C01-R01-S03",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S03",
                        HardwareStatus.OFFLINE.name(),
                        0.0,
                        100.0f,
                        500.0f,
                        0.0f
                );

        return new EnergyConsumptionSnapshot(
                TICK_INDEX,
                ELAPSED_SECONDS,
                760.0,
                1000.0,
                List.of(
                        okServer,
                        alertServer,
                        offlineServer
                )
        );
    }

    private static TemperatureSnapshot createTemperatureSnapshot() {
        ServerTemperatureSnapshot okServer =
                new ServerTemperatureSnapshot(
                        "C01-R01-S01",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S01",
                        HardwareStatus.OK,
                        0.50,
                        300.0,
                        50.0
                );

        ServerTemperatureSnapshot alertServer =
                new ServerTemperatureSnapshot(
                        "C01-R01-S02",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S02",
                        HardwareStatus.ALERT,
                        0.90,
                        460.0,
                        70.0
                );

        ServerTemperatureSnapshot offlineServer =
                new ServerTemperatureSnapshot(
                        "C01-R01-S03",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S03",
                        HardwareStatus.OFFLINE,
                        0.0,
                        0.0,
                        25.0
                );

        return new TemperatureSnapshot(
                TICK_INDEX,
                ELAPSED_SECONDS,
                24.0,
                List.of(
                        okServer,
                        alertServer,
                        offlineServer
                )
        );
    }

    private static HealthSnapshot createHealthSnapshot() {
        ServerHealthSnapshot okServer =
                new ServerHealthSnapshot(
                        "C01-R01-S01",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S01",
                        HardwareStatus.OK,
                        Set.of(),
                        0.50,
                        50.0
                );

        ServerHealthSnapshot alertServer =
                new ServerHealthSnapshot(
                        "C01-R01-S02",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S02",
                        HardwareStatus.ALERT,
                        Set.of(
                                ServerAlertReason.HIGH_UTILIZATION
                        ),
                        0.90,
                        70.0
                );

        ServerHealthSnapshot offlineServer =
                new ServerHealthSnapshot(
                        "C01-R01-S03",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S03",
                        HardwareStatus.OFFLINE,
                        Set.of(),
                        0.0,
                        25.0
                );

        return new HealthSnapshot(
                TICK_INDEX,
                ELAPSED_SECONDS,
                List.of(
                        okServer,
                        alertServer,
                        offlineServer
                )
        );
    }

    @Test
    void shouldAggregateOperationalMetricsByRack() {
        Datacenter datacenter = createDatacenter();
        EnergyConsumptionSnapshot energySnapshot = createEnergySnapshot();
        TemperatureSnapshot temperatureSnapshot = createTemperatureSnapshot();
        HealthSnapshot healthSnapshot = createHealthSnapshot();
        DatacenterOperationalSnapshotProvider provider = new DatacenterOperationalSnapshotProvider(datacenter);
        DatacenterOperationalSnapshot snapshot = provider.snapshot(energySnapshot, temperatureSnapshot, healthSnapshot);
        RackOperationalSnapshot populatedRack = snapshot.getRack(POPULATED_RACK_LOCATION);
        RackOperationalSnapshot emptyRack = snapshot.getRack(EMPTY_RACK_LOCATION);
        assertAll(
                () -> assertEquals(TICK_INDEX, snapshot.tickIndex()),
                () -> assertEquals(ELAPSED_SECONDS, snapshot.elapsedSeconds(), EPSILON),
                () -> assertEquals(2, snapshot.rackCount()),
                () -> assertEquals(3, populatedRack.installedServerCount()),
                () -> assertEquals(2, populatedRack.onlineServerCount()),
                () -> assertEquals(300.0, populatedRack.idlePowerWatts(), EPSILON),
                () -> assertEquals(1500.0, populatedRack.maxPowerWatts(), EPSILON),
                () -> assertEquals(760.0, populatedRack.currentPowerWatts(), EPSILON),
                () -> assertEquals(60.0, populatedRack.averageOnlineTemperatureCelsius(), EPSILON),
                () -> assertEquals(0.70, populatedRack.averageOnlineUtilization(), EPSILON),
                () -> assertTrue(populatedRack.hasInstalledServers()),
                () -> assertTrue(populatedRack.hasOnlineServers())
        );
        assertAll(
                () -> assertEquals(0, emptyRack.installedServerCount()),
                () -> assertEquals(0, emptyRack.onlineServerCount()),
                () -> assertEquals(0.0, emptyRack.idlePowerWatts(), EPSILON),
                () -> assertEquals(0.0, emptyRack.maxPowerWatts(), EPSILON),
                () -> assertEquals(0.0, emptyRack.currentPowerWatts(), EPSILON),
                () -> assertTrue(Double.isNaN(emptyRack.averageOnlineTemperatureCelsius())),
                () -> assertTrue(Double.isNaN(emptyRack.averageOnlineUtilization())),
                () -> assertFalse(emptyRack.hasInstalledServers()),
                () -> assertFalse(emptyRack.hasOnlineServers())
        );
    }

    @Test
    void shouldRejectSnapshotsWithDifferentTickIndexes() {
        DatacenterOperationalSnapshotProvider provider = new DatacenterOperationalSnapshotProvider(createDatacenter());
        EnergyConsumptionSnapshot energySnapshot = createEnergySnapshot();
        TemperatureSnapshot originalTemperature = createTemperatureSnapshot();
        TemperatureSnapshot temperatureSnapshot =
                new TemperatureSnapshot(
                        TICK_INDEX + 1L,
                        originalTemperature.elapsedSeconds(),
                        originalTemperature.ambientTemperatureCelsius(),
                        originalTemperature.servers()
                );
        HealthSnapshot healthSnapshot = createHealthSnapshot();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> provider.snapshot(energySnapshot, temperatureSnapshot, healthSnapshot)
        );
        assertEquals("All snapshots must have the same tickIndex", exception.getMessage());
    }

    @Test
    void shouldRejectSnapshotsWithDifferentElapsedSeconds() {
        DatacenterOperationalSnapshotProvider provider = new DatacenterOperationalSnapshotProvider(createDatacenter());
        EnergyConsumptionSnapshot energySnapshot = createEnergySnapshot();
        TemperatureSnapshot temperatureSnapshot = createTemperatureSnapshot();
        HealthSnapshot originalHealth = createHealthSnapshot();
        HealthSnapshot healthSnapshot = new HealthSnapshot(originalHealth.tickIndex(), ELAPSED_SECONDS + 1.0, originalHealth.servers());
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> provider.snapshot(energySnapshot, temperatureSnapshot, healthSnapshot)
        );
        assertEquals("All snapshots must have the same elapsedSeconds", exception.getMessage());
    }

    @Test
    void shouldRejectMissingTemperatureSnapshotLocation() {
        DatacenterOperationalSnapshotProvider provider = new DatacenterOperationalSnapshotProvider(createDatacenter());
        EnergyConsumptionSnapshot energySnapshot = createEnergySnapshot();
        TemperatureSnapshot originalTemperature = createTemperatureSnapshot();
        TemperatureSnapshot temperatureSnapshot =
                new TemperatureSnapshot(
                        originalTemperature.tickIndex(),
                        originalTemperature.elapsedSeconds(),
                        originalTemperature.ambientTemperatureCelsius(),
                        originalTemperature.servers()
                                .stream()
                                .filter(server ->
                                        !server.location().equals(
                                                OFFLINE_SERVER_LOCATION
                                        )
                                )
                                .toList()
                );
        HealthSnapshot healthSnapshot = createHealthSnapshot();
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(energySnapshot, temperatureSnapshot, healthSnapshot)
        );
        assertEquals("Temperature snapshot locations do not match the datacenter topology", exception.getMessage());
    }

    @Test
    void shouldRejectExtraTemperatureSnapshotLocation() {
        DatacenterOperationalSnapshotProvider provider = new DatacenterOperationalSnapshotProvider(createDatacenter());
        EnergyConsumptionSnapshot energySnapshot = createEnergySnapshot();
        TemperatureSnapshot originalTemperature = createTemperatureSnapshot();
        ServerTemperatureSnapshot extraServer =
                new ServerTemperatureSnapshot(
                        "C01-R01-S04",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S04",
                        HardwareStatus.OK,
                        0.40,
                        260.0,
                        45.0
                );

        TemperatureSnapshot temperatureSnapshot =
                new TemperatureSnapshot(
                        originalTemperature.tickIndex(),
                        originalTemperature.elapsedSeconds(),
                        originalTemperature.ambientTemperatureCelsius(),
                        java.util.stream.Stream.concat(
                                        originalTemperature.servers().stream(),
                                        java.util.stream.Stream.of(extraServer)
                                ).toList()
                );
        HealthSnapshot healthSnapshot = createHealthSnapshot();
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(energySnapshot, temperatureSnapshot, healthSnapshot)
        );
        assertEquals("Temperature snapshot locations do not match the datacenter topology", exception.getMessage());
    }

    @Test
    void shouldRejectDuplicateTemperatureSnapshotLocation() {
        DatacenterOperationalSnapshotProvider provider = new DatacenterOperationalSnapshotProvider(createDatacenter());
        EnergyConsumptionSnapshot energySnapshot = createEnergySnapshot();
        TemperatureSnapshot originalTemperature = createTemperatureSnapshot();
        ServerTemperatureSnapshot duplicateServer = originalTemperature.servers().getFirst();
        TemperatureSnapshot temperatureSnapshot =
                new TemperatureSnapshot(
                        originalTemperature.tickIndex(),
                        originalTemperature.elapsedSeconds(),
                        originalTemperature.ambientTemperatureCelsius(),
                        java.util.stream.Stream.concat(
                                        originalTemperature.servers().stream(),
                                        java.util.stream.Stream.of(duplicateServer)
                                ).toList()
                );

        HealthSnapshot healthSnapshot = createHealthSnapshot();
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(energySnapshot, temperatureSnapshot, healthSnapshot)
        );
        assertEquals("Duplicate temperature snapshot for server: " + OK_SERVER_LOCATION, exception.getMessage());
    }
}
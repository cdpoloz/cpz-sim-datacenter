package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
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

    private record OperationalServerData(
            String serverId,
            String columnCode,
            RackCode rackCode,
            String slotCode,
            HardwareStatus status,
            double utilization,
            float currentPowerWatts,
            double temperatureCelsius
    ) {

        private ServerLocation location() {
            return new ServerLocation(
                    columnCode,
                    rackCode,
                    slotCode
            );
        }
    }

    private static ServerConfig createServerConfig() {
        return new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );
    }

    private static Server createServer(
            OperationalServerData data
    ) {
        return new Server(
                data.location(),
                createServerConfig(),
                data.status(),
                ServerRole.GENERAL_PURPOSE
        );
    }

    private static ServerEnergySnapshot createEnergyServer(
            OperationalServerData data
    ) {
        return new ServerEnergySnapshot(
                data.serverId(),
                data.columnCode(),
                data.rackCode(),
                data.slotCode(),
                data.status(),
                data.utilization(),
                100.0f,
                500.0f,
                data.currentPowerWatts()
        );
    }

    private static ServerTemperatureSnapshot
    createTemperatureServer(
            OperationalServerData data
    ) {

        return new ServerTemperatureSnapshot(
                data.serverId(),
                data.columnCode(),
                data.rackCode(),
                data.slotCode(),
                data.status(),
                data.utilization(),
                data.currentPowerWatts(),
                data.temperatureCelsius()
        );
    }

    private static ServerHealthSnapshot createHealthServer(
            OperationalServerData data
    ) {
        Set<ServerAlertReason> alertReasons =
                data.status() == HardwareStatus.ALERT
                        ? Set.of(
                        ServerAlertReason.HIGH_UTILIZATION
                )
                        : Set.of();

        return new ServerHealthSnapshot(
                data.serverId(),
                data.columnCode(),
                data.rackCode(),
                data.slotCode(),
                data.status(),
                alertReasons,
                data.utilization(),
                data.temperatureCelsius()
        );
    }

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
                        HardwareStatus.OK,
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
                        HardwareStatus.ALERT,
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
                        HardwareStatus.OFFLINE,
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
    void shouldAggregateApplicationDefinedServerGroups() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter(),
                        List.of(
                                new ServerGroupDefinition(
                                        "HA01",
                                        Set.of(
                                                OK_SERVER_LOCATION,
                                                ALERT_SERVER_LOCATION,
                                                OFFLINE_SERVER_LOCATION
                                        )
                                ),
                                new ServerGroupDefinition("EMPTY", Set.of())
                        )
                );

        DatacenterOperationalSnapshot snapshot = provider.snapshot(
                createEnergySnapshot(),
                createTemperatureSnapshot(),
                createHealthSnapshot()
        );
        ServerGroupOperationalSnapshot hotAisle = snapshot.getServerGroup("HA01");
        ServerGroupOperationalSnapshot empty = snapshot.getServerGroup("EMPTY");

        assertAll(
                () -> assertEquals(2, snapshot.serverGroupCount()),
                () -> assertEquals(3, hotAisle.installedServerCount()),
                () -> assertEquals(2, hotAisle.onlineServerCount()),
                () -> assertEquals(300.0, hotAisle.idlePowerWatts(), EPSILON),
                () -> assertEquals(1500.0, hotAisle.maxPowerWatts(), EPSILON),
                () -> assertEquals(760.0, hotAisle.currentPowerWatts(), EPSILON),
                () -> assertEquals(60.0, hotAisle.averageOnlineTemperatureCelsius(), EPSILON),
                () -> assertEquals(0.70, hotAisle.averageOnlineUtilization(), EPSILON),
                () -> assertEquals(70.0, hotAisle.maximumTemperatureCelsius(), EPSILON),
                () -> assertEquals(Optional.of(ALERT_SERVER_LOCATION), hotAisle.maximumTemperatureLocation()),
                () -> assertTrue(empty.maximumTemperatureLocation().isEmpty()),
                () -> assertTrue(Double.isNaN(empty.maximumTemperatureCelsius())),
                () -> assertTrue(Double.isNaN(empty.averageOnlineTemperatureCelsius())),
                () -> assertTrue(Double.isNaN(empty.averageOnlineUtilization()))
        );
    }

    @Test
    void shouldKeepExistingProviderConstructorCompatible() {
        DatacenterOperationalSnapshot snapshot =
                new DatacenterOperationalSnapshotProvider(createDatacenter()).snapshot(
                        createEnergySnapshot(),
                        createTemperatureSnapshot(),
                        createHealthSnapshot()
                );

        assertEquals(0, snapshot.serverGroupCount());
        assertTrue(snapshot.findServerGroup("HA01").isEmpty());
    }

    @Test
    void shouldRejectInvalidServerGroupDefinitions() {
        ServerGroupDefinition group =
                new ServerGroupDefinition("HA01", Set.of(OK_SERVER_LOCATION));
        ServerLocation unknown =
                new ServerLocation("C99", new RackCode("R99"), "S99");

        IllegalArgumentException duplicateCode = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshotProvider(
                        createDatacenter(),
                        List.of(group, group)
                )
        );
        IllegalArgumentException unknownLocation = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshotProvider(
                        createDatacenter(),
                        List.of(new ServerGroupDefinition("UNKNOWN", Set.of(unknown)))
                )
        );

        assertEquals("Duplicate server group code: HA01", duplicateCode.getMessage());
        assertTrue(unknownLocation.getMessage().contains("references unknown server location"));
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

    @Test
    void shouldAggregateOperationalMetricsByRackColumnAndDatacenter() {
        Datacenter datacenter = createDatacenter();

        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        datacenter
                );

        DatacenterOperationalSnapshot snapshot =
                provider.snapshot(
                        createEnergySnapshot(),
                        createTemperatureSnapshot(),
                        createHealthSnapshot()
                );

        RackOperationalSnapshot populatedRack =
                snapshot.getRack(POPULATED_RACK_LOCATION);

        RackOperationalSnapshot emptyRack =
                snapshot.getRack(EMPTY_RACK_LOCATION);

        ColumnOperationalSnapshot column =
                snapshot.getColumn("C01");

        assertAll(
                () -> assertEquals(
                        TICK_INDEX,
                        snapshot.tickIndex()
                ),
                () -> assertEquals(
                        ELAPSED_SECONDS,
                        snapshot.elapsedSeconds(),
                        EPSILON
                ),
                () -> assertEquals(2, snapshot.rackCount()),
                () -> assertEquals(1, snapshot.columnCount()),
                () -> assertEquals(
                        24.0,
                        snapshot.roomTemperatureCelsius(),
                        EPSILON
                )
        );

        assertAll(
                () -> assertEquals(
                        3,
                        populatedRack.installedServerCount()
                ),
                () -> assertEquals(
                        2,
                        populatedRack.onlineServerCount()
                ),
                () -> assertEquals(
                        300.0,
                        populatedRack.idlePowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        1500.0,
                        populatedRack.maxPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        760.0,
                        populatedRack.currentPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        60.0,
                        populatedRack
                                .averageOnlineTemperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        0.70,
                        populatedRack.averageOnlineUtilization(),
                        EPSILON
                ),
                () -> assertTrue(
                        populatedRack.hasInstalledServers()
                ),
                () -> assertTrue(
                        populatedRack.hasOnlineServers()
                )
        );

        assertAll(
                () -> assertEquals(
                        0,
                        emptyRack.installedServerCount()
                ),
                () -> assertEquals(
                        0,
                        emptyRack.onlineServerCount()
                ),
                () -> assertEquals(
                        0.0,
                        emptyRack.idlePowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        0.0,
                        emptyRack.maxPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        0.0,
                        emptyRack.currentPowerWatts(),
                        EPSILON
                ),
                () -> assertTrue(
                        Double.isNaN(
                                emptyRack
                                        .averageOnlineTemperatureCelsius()
                        )
                ),
                () -> assertTrue(
                        Double.isNaN(
                                emptyRack.averageOnlineUtilization()
                        )
                ),
                () -> assertFalse(
                        emptyRack.hasInstalledServers()
                ),
                () -> assertFalse(
                        emptyRack.hasOnlineServers()
                )
        );

        assertAll(
                () -> assertEquals("C01", column.columnCode()),
                () -> assertEquals(
                        3,
                        column.installedServerCount()
                ),
                () -> assertEquals(
                        2,
                        column.onlineServerCount()
                ),
                () -> assertEquals(
                        300.0,
                        column.idlePowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        1500.0,
                        column.maxPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        760.0,
                        column.currentPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        60.0,
                        column.averageOnlineTemperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        0.70,
                        column.averageOnlineUtilization(),
                        EPSILON
                ),
                () -> assertTrue(column.hasInstalledServers()),
                () -> assertTrue(column.hasOnlineServers())
        );

        assertAll(
                () -> assertEquals(
                        java.util.Optional.of(
                                POPULATED_RACK_LOCATION
                        ),
                        snapshot.hottestRackLocation()
                ),
                () -> assertEquals(
                        60.0,
                        snapshot
                                .hottestRackAverageTemperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        0.70,
                        snapshot.totalItUtilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        300.0,
                        snapshot.idleItPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        1500.0,
                        snapshot.maxItPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        760.0,
                        snapshot.currentItPowerWatts(),
                        EPSILON
                ),
                () -> assertTrue(snapshot.hasOnlineServers()),
                () -> assertFalse(snapshot.hasCoolingData()),
                () -> assertFalse(
                        snapshot.hasFacilityPowerData()
                ),
                () -> assertFalse(snapshot.hasPue()),
                () -> assertTrue(
                        Double.isNaN(
                                snapshot.coolingPowerWatts()
                        )
                ),
                () -> assertTrue(
                        Double.isNaN(
                                snapshot.totalFacilityPowerWatts()
                        )
                ),
                () -> assertTrue(
                        Double.isNaN(snapshot.pue())
                )
        );
    }

    @Test
    void shouldCalculateWeightedMetricsAcrossMultipleColumns() {
        RackCode c01Rack01Code = new RackCode("R01");
        RackCode c01Rack02Code = new RackCode("R02");
        RackCode c02Rack01Code = new RackCode("R01");

        RackLocation c01Rack01Location =
                new RackLocation("C01", c01Rack01Code);

        RackLocation c01Rack02Location =
                new RackLocation("C01", c01Rack02Code);

        RackLocation c02Rack01Location =
                new RackLocation("C02", c02Rack01Code);

        List<Rack> racks = List.of(
                new Rack(
                        c01Rack01Code,
                        "C01",
                        "R01",
                        List.of("S01", "S02")
                ),
                new Rack(
                        c01Rack02Code,
                        "C01",
                        "R02",
                        List.of("S01", "S02")
                ),
                new Rack(
                        c02Rack01Code,
                        "C02",
                        "R01",
                        List.of("S01", "S02", "S03")
                )
        );

        List<OperationalServerData> serverData = List.of(
                new OperationalServerData(
                        "C01-R01-S01",
                        "C01",
                        c01Rack01Code,
                        "S01",
                        HardwareStatus.OK,
                        0.20,
                        180.0f,
                        40.0
                ),
                new OperationalServerData(
                        "C01-R01-S02",
                        "C01",
                        c01Rack01Code,
                        "S02",
                        HardwareStatus.OK,
                        0.80,
                        420.0f,
                        60.0
                ),
                new OperationalServerData(
                        "C01-R02-S01",
                        "C01",
                        c01Rack02Code,
                        "S01",
                        HardwareStatus.ALERT,
                        0.90,
                        460.0f,
                        80.0
                ),
                new OperationalServerData(
                        "C01-R02-S02",
                        "C01",
                        c01Rack02Code,
                        "S02",
                        HardwareStatus.OFFLINE,
                        0.0,
                        0.0f,
                        24.0
                ),
                new OperationalServerData(
                        "C02-R01-S01",
                        "C02",
                        c02Rack01Code,
                        "S01",
                        HardwareStatus.OK,
                        0.40,
                        260.0f,
                        50.0
                ),
                new OperationalServerData(
                        "C02-R01-S02",
                        "C02",
                        c02Rack01Code,
                        "S02",
                        HardwareStatus.OK,
                        0.40,
                        260.0f,
                        50.0
                ),
                new OperationalServerData(
                        "C02-R01-S03",
                        "C02",
                        c02Rack01Code,
                        "S03",
                        HardwareStatus.OK,
                        0.40,
                        260.0f,
                        50.0
                )
        );

        Datacenter datacenter = new Datacenter(
                racks,
                serverData.stream()
                        .map(
                                DatacenterOperationalSnapshotProviderTest
                                        ::createServer
                        )
                        .toList()
        );

        EnergyConsumptionSnapshot energySnapshot =
                new EnergyConsumptionSnapshot(
                        TICK_INDEX,
                        ELAPSED_SECONDS,
                        1840.0,
                        2000.0,
                        serverData.stream()
                                .map(
                                        DatacenterOperationalSnapshotProviderTest
                                                ::createEnergyServer
                                )
                                .toList()
                );

        TemperatureSnapshot temperatureSnapshot =
                new TemperatureSnapshot(
                        TICK_INDEX,
                        ELAPSED_SECONDS,
                        24.0,
                        serverData.stream()
                                .map(
                                        DatacenterOperationalSnapshotProviderTest
                                                ::createTemperatureServer
                                )
                                .toList()
                );

        HealthSnapshot healthSnapshot =
                new HealthSnapshot(
                        TICK_INDEX,
                        ELAPSED_SECONDS,
                        serverData.stream()
                                .map(
                                        DatacenterOperationalSnapshotProviderTest
                                                ::createHealthServer
                                )
                                .toList()
                );

        DatacenterOperationalSnapshot snapshot =
                new DatacenterOperationalSnapshotProvider(
                        datacenter
                ).snapshot(
                        energySnapshot,
                        temperatureSnapshot,
                        healthSnapshot
                );

        RackOperationalSnapshot c01Rack01 =
                snapshot.getRack(c01Rack01Location);

        RackOperationalSnapshot c01Rack02 =
                snapshot.getRack(c01Rack02Location);

        RackOperationalSnapshot c02Rack01 =
                snapshot.getRack(c02Rack01Location);

        ColumnOperationalSnapshot columnC01 =
                snapshot.getColumn("C01");

        ColumnOperationalSnapshot columnC02 =
                snapshot.getColumn("C02");

        assertAll(
                () -> assertEquals(3, snapshot.rackCount()),
                () -> assertEquals(2, snapshot.columnCount()),
                () -> assertEquals(
                        24.0,
                        snapshot.roomTemperatureCelsius(),
                        EPSILON
                )
        );

        assertAll(
                () -> assertEquals(
                        50.0,
                        c01Rack01.averageOnlineTemperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        0.50,
                        c01Rack01.averageOnlineUtilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        600.0,
                        c01Rack01.currentPowerWatts(),
                        EPSILON
                )
        );

        assertAll(
                () -> assertEquals(
                        2,
                        c01Rack02.installedServerCount()
                ),
                () -> assertEquals(
                        1,
                        c01Rack02.onlineServerCount()
                ),
                () -> assertEquals(
                        80.0,
                        c01Rack02.averageOnlineTemperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        0.90,
                        c01Rack02.averageOnlineUtilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        460.0,
                        c01Rack02.currentPowerWatts(),
                        EPSILON
                )
        );

        assertAll(
                () -> assertEquals(
                        3,
                        c02Rack01.onlineServerCount()
                ),
                () -> assertEquals(
                        50.0,
                        c02Rack01.averageOnlineTemperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        0.40,
                        c02Rack01.averageOnlineUtilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        780.0,
                        c02Rack01.currentPowerWatts(),
                        EPSILON
                )
        );

        assertAll(
                () -> assertEquals(
                        4,
                        columnC01.installedServerCount()
                ),
                () -> assertEquals(
                        3,
                        columnC01.onlineServerCount()
                ),
                () -> assertEquals(
                        60.0,
                        columnC01
                                .averageOnlineTemperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        1.90 / 3.0,
                        columnC01.averageOnlineUtilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        400.0,
                        columnC01.idlePowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        2000.0,
                        columnC01.maxPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        1060.0,
                        columnC01.currentPowerWatts(),
                        EPSILON
                )
        );

        assertAll(
                () -> assertEquals(
                        3,
                        columnC02.installedServerCount()
                ),
                () -> assertEquals(
                        3,
                        columnC02.onlineServerCount()
                ),
                () -> assertEquals(
                        50.0,
                        columnC02
                                .averageOnlineTemperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        0.40,
                        columnC02.averageOnlineUtilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        780.0,
                        columnC02.currentPowerWatts(),
                        EPSILON
                )
        );

        assertAll(
                () -> assertEquals(
                        java.util.Optional.of(c01Rack02Location),
                        snapshot.hottestRackLocation()
                ),
                () -> assertEquals(
                        80.0,
                        snapshot
                                .hottestRackAverageTemperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        3.10 / 6.0,
                        snapshot.totalItUtilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        700.0,
                        snapshot.idleItPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        3500.0,
                        snapshot.maxItPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        1840.0,
                        snapshot.currentItPowerWatts(),
                        EPSILON
                )
        );
    }

    @Test
    void shouldPreserveTopologyOrderWhenHottestRacksAreTied() {
        RackCode firstRackCode = new RackCode("R01");
        RackCode secondRackCode = new RackCode("R02");

        RackLocation firstRackLocation =
                new RackLocation("C01", firstRackCode);

        List<Rack> racks = List.of(
                new Rack(
                        firstRackCode,
                        "C01",
                        "R01",
                        List.of("S01")
                ),
                new Rack(
                        secondRackCode,
                        "C01",
                        "R02",
                        List.of("S01")
                )
        );

        List<OperationalServerData> serverData = List.of(
                new OperationalServerData(
                        "C01-R01-S01",
                        "C01",
                        firstRackCode,
                        "S01",
                        HardwareStatus.OK,
                        0.40,
                        260.0f,
                        60.0
                ),
                new OperationalServerData(
                        "C01-R02-S01",
                        "C01",
                        secondRackCode,
                        "S01",
                        HardwareStatus.OK,
                        0.60,
                        340.0f,
                        60.0
                )
        );

        Datacenter datacenter = new Datacenter(
                racks,
                serverData.stream()
                        .map(
                                DatacenterOperationalSnapshotProviderTest
                                        ::createServer
                        )
                        .toList()
        );

        EnergyConsumptionSnapshot energySnapshot =
                new EnergyConsumptionSnapshot(
                        TICK_INDEX,
                        ELAPSED_SECONDS,
                        600.0,
                        1000.0,
                        serverData.stream()
                                .map(
                                        DatacenterOperationalSnapshotProviderTest
                                                ::createEnergyServer
                                )
                                .toList()
                );

        TemperatureSnapshot temperatureSnapshot =
                new TemperatureSnapshot(
                        TICK_INDEX,
                        ELAPSED_SECONDS,
                        24.0,
                        serverData.stream()
                                .map(
                                        DatacenterOperationalSnapshotProviderTest
                                                ::createTemperatureServer
                                )
                                .toList()
                );

        HealthSnapshot healthSnapshot =
                new HealthSnapshot(
                        TICK_INDEX,
                        ELAPSED_SECONDS,
                        serverData.stream()
                                .map(
                                        DatacenterOperationalSnapshotProviderTest
                                                ::createHealthServer
                                )
                                .toList()
                );

        DatacenterOperationalSnapshot snapshot =
                new DatacenterOperationalSnapshotProvider(
                        datacenter
                ).snapshot(
                        energySnapshot,
                        temperatureSnapshot,
                        healthSnapshot
                );

        assertAll(
                () -> assertEquals(
                        java.util.Optional.of(firstRackLocation),
                        snapshot.hottestRackLocation()
                ),
                () -> assertEquals(
                        60.0,
                        snapshot
                                .hottestRackAverageTemperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        0.50,
                        snapshot.totalItUtilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        600.0,
                        snapshot.currentItPowerWatts(),
                        EPSILON
                )
        );
    }

    @Test
    void shouldRejectMissingEnergySnapshotLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        EnergyConsumptionSnapshot originalEnergy =
                createEnergySnapshot();

        EnergyConsumptionSnapshot energySnapshot =
                new EnergyConsumptionSnapshot(
                        originalEnergy.tickIndex(),
                        originalEnergy.elapsedSeconds(),
                        760.0,
                        1000.0,
                        originalEnergy.servers()
                                .stream()
                                .filter(server ->
                                        !server.location().equals(
                                                OFFLINE_SERVER_LOCATION
                                        )
                                )
                                .toList()
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        energySnapshot,
                        createTemperatureSnapshot(),
                        createHealthSnapshot()
                )
        );

        assertEquals(
                "Energy snapshot locations do not match "
                        + "the datacenter topology",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectExtraEnergySnapshotLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        EnergyConsumptionSnapshot originalEnergy =
                createEnergySnapshot();

        ServerEnergySnapshot extraServer =
                new ServerEnergySnapshot(
                        "C01-R01-S04",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S04",
                        HardwareStatus.OK,
                        0.40,
                        100.0f,
                        500.0f,
                        260.0f
                );

        EnergyConsumptionSnapshot energySnapshot =
                new EnergyConsumptionSnapshot(
                        originalEnergy.tickIndex(),
                        originalEnergy.elapsedSeconds(),
                        1020.0,
                        1500.0,
                        java.util.stream.Stream.concat(
                                originalEnergy.servers().stream(),
                                java.util.stream.Stream.of(extraServer)
                        ).toList()
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        energySnapshot,
                        createTemperatureSnapshot(),
                        createHealthSnapshot()
                )
        );

        assertEquals(
                "Energy snapshot locations do not match "
                        + "the datacenter topology",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDuplicateEnergySnapshotLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        EnergyConsumptionSnapshot originalEnergy =
                createEnergySnapshot();

        ServerEnergySnapshot duplicateServer =
                originalEnergy.servers().getFirst();

        EnergyConsumptionSnapshot energySnapshot =
                new EnergyConsumptionSnapshot(
                        originalEnergy.tickIndex(),
                        originalEnergy.elapsedSeconds(),
                        1060.0,
                        1500.0,
                        java.util.stream.Stream.concat(
                                originalEnergy.servers().stream(),
                                java.util.stream.Stream.of(duplicateServer)
                        ).toList()
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        energySnapshot,
                        createTemperatureSnapshot(),
                        createHealthSnapshot()
                )
        );

        assertEquals(
                "Duplicate energy snapshot for server: "
                        + OK_SERVER_LOCATION,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingHealthSnapshotLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        HealthSnapshot originalHealth =
                createHealthSnapshot();

        HealthSnapshot healthSnapshot =
                new HealthSnapshot(
                        originalHealth.tickIndex(),
                        originalHealth.elapsedSeconds(),
                        originalHealth.servers()
                                .stream()
                                .filter(server ->
                                        !server.location().equals(
                                                OFFLINE_SERVER_LOCATION
                                        )
                                )
                                .toList()
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        createEnergySnapshot(),
                        createTemperatureSnapshot(),
                        healthSnapshot
                )
        );

        assertEquals(
                "Health snapshot locations do not match "
                        + "the datacenter topology",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectExtraHealthSnapshotLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        HealthSnapshot originalHealth =
                createHealthSnapshot();

        ServerHealthSnapshot extraServer =
                new ServerHealthSnapshot(
                        "C01-R01-S04",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S04",
                        HardwareStatus.OK,
                        Set.of(),
                        0.40,
                        45.0
                );

        HealthSnapshot healthSnapshot =
                new HealthSnapshot(
                        originalHealth.tickIndex(),
                        originalHealth.elapsedSeconds(),
                        java.util.stream.Stream.concat(
                                originalHealth.servers().stream(),
                                java.util.stream.Stream.of(extraServer)
                        ).toList()
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        createEnergySnapshot(),
                        createTemperatureSnapshot(),
                        healthSnapshot
                )
        );

        assertEquals(
                "Health snapshot locations do not match "
                        + "the datacenter topology",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDuplicateHealthSnapshotLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        HealthSnapshot originalHealth =
                createHealthSnapshot();

        ServerHealthSnapshot duplicateServer =
                originalHealth.servers().getFirst();

        HealthSnapshot healthSnapshot =
                new HealthSnapshot(
                        originalHealth.tickIndex(),
                        originalHealth.elapsedSeconds(),
                        java.util.stream.Stream.concat(
                                originalHealth.servers().stream(),
                                java.util.stream.Stream.of(duplicateServer)
                        ).toList()
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        createEnergySnapshot(),
                        createTemperatureSnapshot(),
                        healthSnapshot
                )
        );

        assertEquals(
                "Duplicate health snapshot for server: "
                        + OK_SERVER_LOCATION,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullDatacenter() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new DatacenterOperationalSnapshotProvider(null)
        );

        assertEquals(
                "datacenter must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullEnergySnapshot() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> provider.snapshot(
                        null,
                        createTemperatureSnapshot(),
                        createHealthSnapshot()
                )
        );

        assertEquals(
                "energySnapshot must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullTemperatureSnapshot() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> provider.snapshot(
                        createEnergySnapshot(),
                        null,
                        createHealthSnapshot()
                )
        );

        assertEquals(
                "temperatureSnapshot must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullHealthSnapshot() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> provider.snapshot(
                        createEnergySnapshot(),
                        createTemperatureSnapshot(),
                        null
                )
        );

        assertEquals(
                "healthSnapshot must not be null",
                exception.getMessage()
        );
    }

    private static TemperatureSnapshot
    createTemperatureSnapshotReplacingOkServer(
            ServerTemperatureSnapshot replacement
    ) {
        TemperatureSnapshot original =
                createTemperatureSnapshot();

        return new TemperatureSnapshot(
                original.tickIndex(),
                original.elapsedSeconds(),
                original.ambientTemperatureCelsius(),
                original.servers()
                        .stream()
                        .map(server ->
                                server.location().equals(
                                        OK_SERVER_LOCATION
                                )
                                        ? replacement
                                        : server
                        )
                        .toList()
        );
    }

    private static HealthSnapshot
    createHealthSnapshotReplacingOkServer(
            ServerHealthSnapshot replacement
    ) {
        HealthSnapshot original =
                createHealthSnapshot();

        return new HealthSnapshot(
                original.tickIndex(),
                original.elapsedSeconds(),
                original.servers()
                        .stream()
                        .map(server ->
                                server.location().equals(
                                        OK_SERVER_LOCATION
                                )
                                        ? replacement
                                        : server
                        )
                        .toList()
        );
    }

    @Test
    void shouldRejectInconsistentServerCodeForSameLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        ServerTemperatureSnapshot inconsistentServer =
                new ServerTemperatureSnapshot(
                        "different-server-code",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S01",
                        HardwareStatus.OK,
                        0.50,
                        300.0,
                        50.0
                );

        TemperatureSnapshot temperatureSnapshot =
                createTemperatureSnapshotReplacingOkServer(
                        inconsistentServer
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        createEnergySnapshot(),
                        temperatureSnapshot,
                        createHealthSnapshot()
                )
        );

        assertEquals(
                "Inconsistent serverCode for server: "
                        + OK_SERVER_LOCATION,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInconsistentStatusForSameLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        ServerTemperatureSnapshot inconsistentServer =
                new ServerTemperatureSnapshot(
                        "C01-R01-S01",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S01",
                        HardwareStatus.ALERT,
                        0.50,
                        300.0,
                        50.0
                );

        TemperatureSnapshot temperatureSnapshot =
                createTemperatureSnapshotReplacingOkServer(
                        inconsistentServer
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        createEnergySnapshot(),
                        temperatureSnapshot,
                        createHealthSnapshot()
                )
        );

        assertEquals(
                "Inconsistent status for server: "
                        + OK_SERVER_LOCATION,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInconsistentUtilizationForSameLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        ServerHealthSnapshot inconsistentServer =
                new ServerHealthSnapshot(
                        "C01-R01-S01",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S01",
                        HardwareStatus.OK,
                        Set.of(),
                        0.51,
                        50.0
                );

        HealthSnapshot healthSnapshot =
                createHealthSnapshotReplacingOkServer(
                        inconsistentServer
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        createEnergySnapshot(),
                        createTemperatureSnapshot(),
                        healthSnapshot
                )
        );

        assertEquals(
                "Inconsistent utilization for server: "
                        + OK_SERVER_LOCATION,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInconsistentCurrentPowerForSameLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        ServerTemperatureSnapshot inconsistentServer =
                new ServerTemperatureSnapshot(
                        "C01-R01-S01",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S01",
                        HardwareStatus.OK,
                        0.50,
                        301.0,
                        50.0
                );

        TemperatureSnapshot temperatureSnapshot =
                createTemperatureSnapshotReplacingOkServer(
                        inconsistentServer
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        createEnergySnapshot(),
                        temperatureSnapshot,
                        createHealthSnapshot()
                )
        );

        assertEquals(
                "Inconsistent currentPowerWatts for server: "
                        + OK_SERVER_LOCATION,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInconsistentTemperatureForSameLocation() {
        DatacenterOperationalSnapshotProvider provider =
                new DatacenterOperationalSnapshotProvider(
                        createDatacenter()
                );

        ServerHealthSnapshot inconsistentServer =
                new ServerHealthSnapshot(
                        "C01-R01-S01",
                        "C01",
                        POPULATED_RACK_CODE,
                        "S01",
                        HardwareStatus.OK,
                        Set.of(),
                        0.50,
                        51.0
                );

        HealthSnapshot healthSnapshot =
                createHealthSnapshotReplacingOkServer(
                        inconsistentServer
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.snapshot(
                        createEnergySnapshot(),
                        createTemperatureSnapshot(),
                        healthSnapshot
                )
        );

        assertEquals(
                "Inconsistent temperatureCelsius for server: "
                        + OK_SERVER_LOCATION,
                exception.getMessage()
        );
    }
}
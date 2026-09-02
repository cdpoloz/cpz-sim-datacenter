package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class DatacenterOperationalSnapshotTest {

    private static final RackLocation RACK_LOCATION =
            new RackLocation(
                    "C01",
                    new RackCode("R01")
            );

    private static RackOperationalSnapshot createRackSnapshot() {
        return new RackOperationalSnapshot(
                RACK_LOCATION,
                2,
                2,
                200.0,
                1000.0,
                640.0,
                60.0,
                60.0,
                0.60
        );
    }

    private static ColumnOperationalSnapshot
    createColumnSnapshot() {

        return new ColumnOperationalSnapshot(
                "C01",
                2,
                2,
                200.0,
                1000.0,
                640.0,
                60.0,
                0.60
        );
    }

    private static DatacenterOperationalSnapshot createSnapshot(
            Map<RackLocation, RackOperationalSnapshot> racks,
            Map<String, ColumnOperationalSnapshot> columns
    ) {
        return new DatacenterOperationalSnapshot(
                10L,
                600.0,
                racks,
                columns,
                24.0,
                Optional.of(RACK_LOCATION),
                60.0,
                0.60,
                200.0,
                1000.0,
                640.0,
                Double.NaN,
                Double.NaN,
                Double.NaN
        );
    }

    @Test
    void shouldCreateOperationalSnapshotWithAvailableItData() {
        DatacenterOperationalSnapshot snapshot =
                createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                );

        assertEquals(10L, snapshot.tickIndex());
        assertEquals(600.0, snapshot.elapsedSeconds());
        assertEquals(24.0, snapshot.roomTemperatureCelsius());

        assertEquals(
                Optional.of(RACK_LOCATION),
                snapshot.hottestRackLocation()
        );

        assertEquals(
                60.0,
                snapshot.hottestRackAverageTemperatureCelsius()
        );

        assertEquals(0.60, snapshot.totalItUtilization());
        assertEquals(200.0, snapshot.idleItPowerWatts());
        assertEquals(1000.0, snapshot.maxItPowerWatts());
        assertEquals(640.0, snapshot.currentItPowerWatts());

        assertTrue(snapshot.hasOnlineServers());
        assertFalse(snapshot.hasCoolingData());
        assertFalse(snapshot.hasFacilityPowerData());
        assertFalse(snapshot.hasPue());
    }

    @Test
    void shouldDefensivelyCopyMapsAndSupportLookup() {
        RackOperationalSnapshot rackSnapshot =
                createRackSnapshot();

        ColumnOperationalSnapshot columnSnapshot =
                createColumnSnapshot();

        Map<RackLocation, RackOperationalSnapshot> racks =
                new LinkedHashMap<>();

        racks.put(
                RACK_LOCATION,
                rackSnapshot
        );

        Map<String, ColumnOperationalSnapshot> columns =
                new LinkedHashMap<>();

        columns.put(
                "C01",
                columnSnapshot
        );

        DatacenterOperationalSnapshot snapshot =
                createSnapshot(racks, columns);

        racks.clear();
        columns.clear();

        assertEquals(1, snapshot.rackCount());
        assertEquals(1, snapshot.columnCount());

        assertTrue(
                snapshot.findRack(RACK_LOCATION).isPresent()
        );

        assertTrue(
                snapshot.findColumn("C01").isPresent()
        );

        assertSame(
                rackSnapshot,
                snapshot.getRack(RACK_LOCATION)
        );

        assertSame(
                columnSnapshot,
                snapshot.getColumn("C01")
        );
    }

    @Test
    void shouldReturnEmptyOptionalForUnknownRackAndColumn() {
        DatacenterOperationalSnapshot snapshot =
                createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                );

        RackLocation unknownLocation =
                new RackLocation(
                        "C01",
                        new RackCode("R02")
                );

        assertTrue(
                snapshot.findRack(unknownLocation).isEmpty()
        );

        assertTrue(
                snapshot.findColumn("C02").isEmpty()
        );
    }

    @Test
    void shouldRejectGettingUnknownRack() {
        DatacenterOperationalSnapshot snapshot =
                createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                );

        RackLocation unknownLocation =
                new RackLocation(
                        "C01",
                        new RackCode("R02")
                );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> snapshot.getRack(unknownLocation)
        );

        assertEquals(
                "No operational snapshot for rack: "
                        + unknownLocation,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectGettingUnknownColumn() {
        DatacenterOperationalSnapshot snapshot =
                createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> snapshot.getColumn("C02")
        );

        assertEquals(
                "No operational snapshot for column: C02",
                exception.getMessage()
        );
    }

    @Test
    void shouldCreateOperationalSnapshotWithoutOnlineServers() {
        RackOperationalSnapshot rackSnapshot =
                new RackOperationalSnapshot(
                        RACK_LOCATION,
                        2,
                        0,
                        200.0,
                        1000.0,
                        0.0,
                        Double.NaN,
                        24.0,
                        Double.NaN
                );

        ColumnOperationalSnapshot columnSnapshot =
                new ColumnOperationalSnapshot(
                        "C01",
                        2,
                        0,
                        200.0,
                        1000.0,
                        0.0,
                        Double.NaN,
                        Double.NaN
                );

        DatacenterOperationalSnapshot snapshot =
                new DatacenterOperationalSnapshot(
                        10L,
                        600.0,
                        Map.of(RACK_LOCATION, rackSnapshot),
                        Map.of("C01", columnSnapshot),
                        24.0,
                        Optional.empty(),
                        Double.NaN,
                        Double.NaN,
                        200.0,
                        1000.0,
                        0.0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                );

        assertFalse(snapshot.hasOnlineServers());
        assertTrue(snapshot.hottestRackLocation().isEmpty());
        assertTrue(
                Double.isNaN(
                        snapshot
                                .hottestRackAverageTemperatureCelsius()
                )
        );
        assertTrue(Double.isNaN(snapshot.totalItUtilization()));
    }

    @Test
    void shouldRejectNegativeTickIndex() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshot(
                        -1L,
                        600.0,
                        Map.of(RACK_LOCATION, createRackSnapshot()),
                        Map.of("C01", createColumnSnapshot()),
                        24.0,
                        Optional.of(RACK_LOCATION),
                        60.0,
                        0.60,
                        200.0,
                        1000.0,
                        640.0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                )
        );

        assertEquals(
                "tickIndex must be >= 0",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInvalidElapsedSeconds() {
        for (double invalidValue : new double[]{
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(
                            10L,
                            invalidValue,
                            Map.of(
                                    RACK_LOCATION,
                                    createRackSnapshot()
                            ),
                            Map.of(
                                    "C01",
                                    createColumnSnapshot()
                            ),
                            24.0,
                            Optional.of(RACK_LOCATION),
                            60.0,
                            0.60,
                            200.0,
                            1000.0,
                            640.0,
                            Double.NaN,
                            Double.NaN,
                            Double.NaN
                    )
            );

            assertEquals(
                    "elapsedSeconds must be finite and >= 0",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectInvalidRoomTemperature() {
        for (double invalidValue : new double[]{
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(
                            10L,
                            600.0,
                            Map.of(
                                    RACK_LOCATION,
                                    createRackSnapshot()
                            ),
                            Map.of(
                                    "C01",
                                    createColumnSnapshot()
                            ),
                            invalidValue,
                            Optional.of(RACK_LOCATION),
                            60.0,
                            0.60,
                            200.0,
                            1000.0,
                            640.0,
                            Double.NaN,
                            Double.NaN,
                            Double.NaN
                    )
            );

            assertEquals(
                    "roomTemperatureCelsius must be finite",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectNullRackMap() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshot(
                        null,
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                )
        );

        assertEquals(
                "racks must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullRackLocation() {
        Map<RackLocation, RackOperationalSnapshot> racks =
                new LinkedHashMap<>();

        racks.put(
                null,
                createRackSnapshot()
        );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshot(
                        racks,
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                )
        );

        assertEquals(
                "rack location must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullRackSnapshot() {
        Map<RackLocation, RackOperationalSnapshot> racks =
                new LinkedHashMap<>();

        racks.put(
                RACK_LOCATION,
                null
        );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshot(
                        racks,
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                )
        );

        assertEquals(
                "rack snapshot must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectRackMapKeyThatDoesNotMatchSnapshotLocation() {
        RackLocation differentLocation =
                new RackLocation(
                        "C01",
                        new RackCode("R02")
                );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createSnapshot(
                        Map.of(
                                differentLocation,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                )
        );

        assertEquals(
                "Rack map key does not match snapshot location: "
                        + differentLocation,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullColumnMap() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        null
                )
        );

        assertEquals(
                "columns must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullColumnCode() {
        Map<String, ColumnOperationalSnapshot> columns =
                new LinkedHashMap<>();

        columns.put(
                null,
                createColumnSnapshot()
        );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        columns
                )
        );

        assertEquals(
                "column code must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullColumnSnapshot() {
        Map<String, ColumnOperationalSnapshot> columns =
                new LinkedHashMap<>();

        columns.put(
                "C01",
                null
        );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        columns
                )
        );

        assertEquals(
                "column snapshot must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectColumnMapKeyThatDoesNotMatchSnapshotCode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C02",
                                createColumnSnapshot()
                        )
                )
        );

        assertEquals(
                "Column map key does not match snapshot code: C02",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullHottestRackLocation() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new DatacenterOperationalSnapshot(
                        10L,
                        600.0,
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        ),
                        24.0,
                        null,
                        60.0,
                        0.60,
                        200.0,
                        1000.0,
                        640.0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                )
        );

        assertEquals(
                "hottestRackLocation must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectHottestRackThatDoesNotExistInRackMap() {
        RackLocation unknownLocation =
                new RackLocation(
                        "C01",
                        new RackCode("R02")
                );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshot(
                        10L,
                        600.0,
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        ),
                        24.0,
                        Optional.of(unknownLocation),
                        60.0,
                        0.60,
                        200.0,
                        1000.0,
                        640.0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                )
        );

        assertEquals(
                "Hottest rack location does not exist in racks: "
                        + unknownLocation,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectOfflineRackAsHottestRack() {
        RackOperationalSnapshot offlineRackSnapshot =
                new RackOperationalSnapshot(
                        RACK_LOCATION,
                        2,
                        0,
                        200.0,
                        1000.0,
                        0.0,
                        Double.NaN,
                        24.0,
                        Double.NaN
                );

        ColumnOperationalSnapshot offlineColumnSnapshot =
                new ColumnOperationalSnapshot(
                        "C01",
                        2,
                        0,
                        200.0,
                        1000.0,
                        0.0,
                        Double.NaN,
                        Double.NaN
                );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshot(
                        10L,
                        600.0,
                        Map.of(
                                RACK_LOCATION,
                                offlineRackSnapshot
                        ),
                        Map.of(
                                "C01",
                                offlineColumnSnapshot
                        ),
                        24.0,
                        Optional.of(RACK_LOCATION),
                        60.0,
                        Double.NaN,
                        200.0,
                        1000.0,
                        0.0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                )
        );

        assertEquals(
                "Hottest rack must contain online servers",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInvalidHottestRackTemperatureWhenRackExists() {
        for (double invalidValue : new double[]{
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(
                            10L,
                            600.0,
                            Map.of(
                                    RACK_LOCATION,
                                    createRackSnapshot()
                            ),
                            Map.of(
                                    "C01",
                                    createColumnSnapshot()
                            ),
                            24.0,
                            Optional.of(RACK_LOCATION),
                            invalidValue,
                            0.60,
                            200.0,
                            1000.0,
                            640.0,
                            Double.NaN,
                            Double.NaN,
                            Double.NaN
                    )
            );

            assertEquals(
                    "hottestRackAverageTemperatureCelsius must be "
                            + "finite when a hottest rack exists",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectHottestRackTemperatureWhenLocationIsEmpty() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshot(
                        10L,
                        600.0,
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        ),
                        24.0,
                        Optional.empty(),
                        60.0,
                        0.60,
                        200.0,
                        1000.0,
                        640.0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                )
        );

        assertEquals(
                "hottestRackAverageTemperatureCelsius must be NaN "
                        + "when there is no hottest rack",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingHottestRackWhenOnlineServersExist() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshot(
                        10L,
                        600.0,
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        ),
                        24.0,
                        Optional.empty(),
                        Double.NaN,
                        0.60,
                        200.0,
                        1000.0,
                        640.0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                )
        );

        assertEquals(
                "hottestRackLocation must be present "
                        + "when online servers exist",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInvalidTotalItUtilizationWhenOnlineServersExist() {
        for (double invalidValue : new double[]{
                -0.01,
                1.01,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(
                            10L,
                            600.0,
                            Map.of(
                                    RACK_LOCATION,
                                    createRackSnapshot()
                            ),
                            Map.of(
                                    "C01",
                                    createColumnSnapshot()
                            ),
                            24.0,
                            Optional.of(RACK_LOCATION),
                            60.0,
                            invalidValue,
                            200.0,
                            1000.0,
                            640.0,
                            Double.NaN,
                            Double.NaN,
                            Double.NaN
                    )
            );

            assertEquals(
                    "totalItUtilization must be finite "
                            + "and between 0 and 1",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectTotalItUtilizationWhenNoOnlineServersExist() {
        RackOperationalSnapshot offlineRackSnapshot =
                new RackOperationalSnapshot(
                        RACK_LOCATION,
                        2,
                        0,
                        200.0,
                        1000.0,
                        0.0,
                        Double.NaN,
                        24.0,
                        Double.NaN
                );

        ColumnOperationalSnapshot offlineColumnSnapshot =
                new ColumnOperationalSnapshot(
                        "C01",
                        2,
                        0,
                        200.0,
                        1000.0,
                        0.0,
                        Double.NaN,
                        Double.NaN
                );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshot(
                        10L,
                        600.0,
                        Map.of(
                                RACK_LOCATION,
                                offlineRackSnapshot
                        ),
                        Map.of(
                                "C01",
                                offlineColumnSnapshot
                        ),
                        24.0,
                        Optional.empty(),
                        Double.NaN,
                        0.0,
                        200.0,
                        1000.0,
                        0.0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                )
        );

        assertEquals(
                "totalItUtilization must be NaN "
                        + "when there are no online servers",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInvalidIdleItPower() {
        for (double invalidValue : new double[]{
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(
                            10L,
                            600.0,
                            Map.of(
                                    RACK_LOCATION,
                                    createRackSnapshot()
                            ),
                            Map.of(
                                    "C01",
                                    createColumnSnapshot()
                            ),
                            24.0,
                            Optional.of(RACK_LOCATION),
                            60.0,
                            0.60,
                            invalidValue,
                            1000.0,
                            640.0,
                            Double.NaN,
                            Double.NaN,
                            Double.NaN
                    )
            );

            assertEquals(
                    "idleItPowerWatts must be finite and >= 0",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectInvalidMaxItPower() {
        for (double invalidValue : new double[]{
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(
                            10L,
                            600.0,
                            Map.of(
                                    RACK_LOCATION,
                                    createRackSnapshot()
                            ),
                            Map.of(
                                    "C01",
                                    createColumnSnapshot()
                            ),
                            24.0,
                            Optional.of(RACK_LOCATION),
                            60.0,
                            0.60,
                            200.0,
                            invalidValue,
                            100.0,
                            Double.NaN,
                            Double.NaN,
                            Double.NaN
                    )
            );

            assertEquals(
                    "maxItPowerWatts must be finite and >= 0",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectInvalidCurrentItPower() {
        for (double invalidValue : new double[]{
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(
                            10L,
                            600.0,
                            Map.of(
                                    RACK_LOCATION,
                                    createRackSnapshot()
                            ),
                            Map.of(
                                    "C01",
                                    createColumnSnapshot()
                            ),
                            24.0,
                            Optional.of(RACK_LOCATION),
                            60.0,
                            0.60,
                            200.0,
                            1000.0,
                            invalidValue,
                            Double.NaN,
                            Double.NaN,
                            Double.NaN
                    )
            );

            assertEquals(
                    "currentItPowerWatts must be finite and >= 0",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectIdleItPowerGreaterThanMaxItPower() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshot(
                        10L,
                        600.0,
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        ),
                        24.0,
                        Optional.of(RACK_LOCATION),
                        60.0,
                        0.60,
                        1001.0,
                        1000.0,
                        640.0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                )
        );

        assertEquals(
                "idleItPowerWatts must not exceed maxItPowerWatts",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectCurrentItPowerGreaterThanMaxItPower() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshot(
                        10L,
                        600.0,
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        ),
                        24.0,
                        Optional.of(RACK_LOCATION),
                        60.0,
                        0.60,
                        200.0,
                        1000.0,
                        1001.0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN
                )
        );

        assertEquals(
                "currentItPowerWatts must not exceed maxItPowerWatts",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectCoolingPowerUntilCoolingDataIsAvailable() {
        for (double invalidValue : new double[]{
                0.0,
                500.0,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(
                            10L,
                            600.0,
                            Map.of(
                                    RACK_LOCATION,
                                    createRackSnapshot()
                            ),
                            Map.of(
                                    "C01",
                                    createColumnSnapshot()
                            ),
                            24.0,
                            Optional.of(RACK_LOCATION),
                            60.0,
                            0.60,
                            200.0,
                            1000.0,
                            640.0,
                            invalidValue,
                            Double.NaN,
                            Double.NaN
                    )
            );

            assertEquals(
                    "coolingPowerWatts must be NaN "
                            + "until cooling data is available",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectTotalFacilityPowerUntilCoolingDataIsAvailable() {
        for (double invalidValue : new double[]{
                0.0,
                1140.0,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(
                            10L,
                            600.0,
                            Map.of(
                                    RACK_LOCATION,
                                    createRackSnapshot()
                            ),
                            Map.of(
                                    "C01",
                                    createColumnSnapshot()
                            ),
                            24.0,
                            Optional.of(RACK_LOCATION),
                            60.0,
                            0.60,
                            200.0,
                            1000.0,
                            640.0,
                            Double.NaN,
                            invalidValue,
                            Double.NaN
                    )
            );

            assertEquals(
                    "totalFacilityPowerWatts must be NaN "
                            + "until cooling data is available",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectPueUntilCoolingDataIsAvailable() {
        for (double invalidValue : new double[]{
                0.0,
                1.5,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(
                            10L,
                            600.0,
                            Map.of(
                                    RACK_LOCATION,
                                    createRackSnapshot()
                            ),
                            Map.of(
                                    "C01",
                                    createColumnSnapshot()
                            ),
                            24.0,
                            Optional.of(RACK_LOCATION),
                            60.0,
                            0.60,
                            200.0,
                            1000.0,
                            640.0,
                            Double.NaN,
                            Double.NaN,
                            invalidValue
                    )
            );

            assertEquals(
                    "pue must be NaN "
                            + "until cooling data is available",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldExposeUnmodifiableMaps() {
        DatacenterOperationalSnapshot snapshot =
                createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                );

        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.racks().clear()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.columns().clear()
        );
    }

    @Test
    void shouldRejectNullRackLookup() {
        DatacenterOperationalSnapshot snapshot =
                createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> snapshot.findRack(null)
        );

        assertEquals(
                "location must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullColumnLookup() {
        DatacenterOperationalSnapshot snapshot =
                createSnapshot(
                        Map.of(
                                RACK_LOCATION,
                                createRackSnapshot()
                        ),
                        Map.of(
                                "C01",
                                createColumnSnapshot()
                        )
                );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> snapshot.findColumn(null)
        );

        assertEquals(
                "columnCode must not be null",
                exception.getMessage()
        );
    }
}

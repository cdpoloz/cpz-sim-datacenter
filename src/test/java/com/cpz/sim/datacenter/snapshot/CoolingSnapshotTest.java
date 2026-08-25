package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.cooling.CoolingUnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class CoolingSnapshotTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void shouldExposeSnapshotData() {
        CoolingUnitSnapshot unit =
                new CoolingUnitSnapshot(
                        "SUPPLY-C01-C02",
                        CoolingUnitType.SUPPLY,
                        true,
                        12.0,
                        5000.0
                );

        CoolingZoneSnapshot zone =
                new CoolingZoneSnapshot(
                        "ZONE-C01-R01",
                        3000.0,
                        5000.0,
                        3000.0,
                        0.0,
                        8.0,
                        8.0,
                        24.0,
                        32.0,
                        0.10
                );

        CoolingSnapshot snapshot =
                new CoolingSnapshot(
                        10L,
                        List.of(unit),
                        List.of(zone)
                );

        assertAll(
                () -> assertEquals(10L, snapshot.tickIndex()),
                () -> assertEquals(List.of(unit), snapshot.units()),
                () -> assertEquals(List.of(zone), snapshot.zones()),
                () -> assertEquals(unit, snapshot.findUnit("SUPPLY-C01-C02").orElseThrow()),
                () -> assertEquals(zone, snapshot.findZone("ZONE-C01-R01").orElseThrow()),
                () -> assertEquals(3000.0, snapshot.totalGeneratedHeatWatts(), EPSILON),
                () -> assertEquals(0.0, snapshot.totalCoolingDeficitWatts(), EPSILON)
        );
    }

    @Test
    void shouldDetectCoolingDeficit() {
        CoolingSnapshot snapshot =
                new CoolingSnapshot(
                        1L,
                        List.of(supplyUnit()),
                        List.of(
                                zone(
                                        "ZONE-C01-R01",
                                        3000.0,
                                        2000.0,
                                        2000.0,
                                        1000.0,
                                        24.0,
                                        38.0,
                                        0.35
                                )
                        )
                );

        assertTrue(snapshot.hasCoolingDeficit());
    }

    @Test
    void shouldReturnTotalGeneratedHeatAndCoolingDeficit() {
        CoolingSnapshot snapshot =
                new CoolingSnapshot(
                        1L,
                        List.of(supplyUnit()),
                        List.of(
                                zone(
                                        "ZONE-C01-R01",
                                        3000.0,
                                        2500.0,
                                        2500.0,
                                        500.0,
                                        24.0,
                                        34.0,
                                        0.20
                                ),
                                zone(
                                        "ZONE-C01-R02",
                                        4000.0,
                                        3000.0,
                                        3000.0,
                                        1000.0,
                                        25.0,
                                        39.0,
                                        0.30
                                )
                        )
                );

        assertAll(
                () -> assertEquals(7000.0, snapshot.totalGeneratedHeatWatts(), EPSILON),
                () -> assertEquals(1500.0, snapshot.totalCoolingDeficitWatts(), EPSILON)
        );
    }

    @Test
    void shouldAggregateZonesIntoCoolingZoneGroupSnapshot() {
        CoolingSnapshot snapshot =
                new CoolingSnapshot(
                        20L,
                        List.of(supplyUnit()),
                        List.of(
                                zone(
                                        "ZONE-C01-R01",
                                        3000.0,
                                        5000.0,
                                        3000.0,
                                        0.0,
                                        24.0,
                                        32.0,
                                        0.10
                                ),
                                zone(
                                        "ZONE-C01-R02",
                                        5000.0,
                                        7000.0,
                                        5000.0,
                                        0.0,
                                        26.0,
                                        40.0,
                                        0.30
                                ),
                                zone(
                                        "ZONE-C02-R01",
                                        1000.0,
                                        2000.0,
                                        1000.0,
                                        0.0,
                                        25.0,
                                        31.0,
                                        0.20
                                )
                        )
                );

        CoolingZoneGroupSnapshot groupSnapshot =
                snapshot.aggregateZones(
                        "HOT-AISLE-C01",
                        List.of(
                                "ZONE-C01-R01",
                                "ZONE-C01-R02"
                        )
                );

        assertAll(
                () -> assertEquals("HOT-AISLE-C01", groupSnapshot.groupCode()),
                () -> assertEquals(
                        List.of(
                                "ZONE-C01-R01",
                                "ZONE-C01-R02"
                        ),
                        groupSnapshot.zoneCodes()
                ),
                () -> assertEquals(8000.0, groupSnapshot.generatedHeatWatts(), EPSILON),
                () -> assertEquals(12000.0, groupSnapshot.availableCoolingCapacityWatts(), EPSILON),
                () -> assertEquals(8000.0, groupSnapshot.usedCoolingCapacityWatts(), EPSILON),
                () -> assertEquals(0.0, groupSnapshot.coolingDeficitWatts(), EPSILON),
                () -> assertEquals(25.0, groupSnapshot.averageInletAirTemperatureCelsius(), EPSILON),
                () -> assertEquals(36.0, groupSnapshot.averageExhaustAirTemperatureCelsius(), EPSILON),
                () -> assertEquals(0.20, groupSnapshot.averageRecirculationFraction(), EPSILON),
                () -> assertEquals(8000.0 / 12000.0, groupSnapshot.coolingLoad(), EPSILON),
                () -> assertEquals(1.0, groupSnapshot.thermalCoverage(), EPSILON),
                () -> assertEquals(11.0, groupSnapshot.airTemperatureRiseCelsius(), EPSILON)
        );
    }

    @Test
    void shouldAggregateZonesUsingTotalCapacityForCoolingLoad() {
        CoolingSnapshot snapshot =
                new CoolingSnapshot(
                        1L,
                        List.of(supplyUnit()),
                        List.of(
                                zone(
                                        "ZONE-A",
                                        1000.0,
                                        1000.0,
                                        1000.0,
                                        0.0,
                                        24.0,
                                        30.0,
                                        0.10
                                ),
                                zone(
                                        "ZONE-B",
                                        5000.0,
                                        9000.0,
                                        5000.0,
                                        0.0,
                                        24.0,
                                        36.0,
                                        0.20
                                )
                        )
                );

        CoolingZoneGroupSnapshot groupSnapshot =
                snapshot.aggregateZones(
                        "GROUP-01",
                        List.of("ZONE-A", "ZONE-B")
                );

        assertEquals(
                6000.0 / 10000.0,
                groupSnapshot.coolingLoad(),
                EPSILON
        );
    }

    @Test
    void shouldPreserveRequestedZoneOrderAndRemoveDuplicatesWhenAggregatingZones() {
        CoolingSnapshot snapshot =
                new CoolingSnapshot(
                        1L,
                        List.of(supplyUnit()),
                        List.of(
                                zone(
                                        "ZONE-A",
                                        1000.0,
                                        2000.0,
                                        1000.0,
                                        0.0,
                                        24.0,
                                        30.0,
                                        0.10
                                ),
                                zone(
                                        "ZONE-B",
                                        2000.0,
                                        3000.0,
                                        2000.0,
                                        0.0,
                                        25.0,
                                        34.0,
                                        0.20
                                )
                        )
                );

        CoolingZoneGroupSnapshot groupSnapshot =
                snapshot.aggregateZones(
                        "GROUP-01",
                        List.of("ZONE-B", "ZONE-A", "ZONE-B")
                );

        assertAll(
                () -> assertEquals(
                        List.of("ZONE-B", "ZONE-A"),
                        groupSnapshot.zoneCodes()
                ),
                () -> assertEquals(3000.0, groupSnapshot.generatedHeatWatts(), EPSILON),
                () -> assertEquals(5000.0, groupSnapshot.availableCoolingCapacityWatts(), EPSILON),
                () -> assertEquals(3000.0, groupSnapshot.usedCoolingCapacityWatts(), EPSILON)
        );
    }

    @Test
    void shouldRejectNegativeTickIndex() {
        for (long invalidValue : new long[]{
                -1L,
                Long.MIN_VALUE
        }) {
            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new CoolingSnapshot(
                                    invalidValue,
                                    List.of(supplyUnit()),
                                    List.of(validZone())
                            )
                    );

            assertEquals(
                    "tickIndex must be greater than or equal to 0",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectNullUnits() {
        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> new CoolingSnapshot(
                                1L,
                                null,
                                List.of(validZone())
                        )
                );

        assertEquals(
                "units must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullZones() {
        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> new CoolingSnapshot(
                                1L,
                                List.of(supplyUnit()),
                                null
                        )
                );

        assertEquals(
                "zones must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectEmptyUnits() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new CoolingSnapshot(
                                1L,
                                List.of(),
                                List.of(validZone())
                        )
                );

        assertEquals(
                "units must not be empty",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectEmptyZones() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new CoolingSnapshot(
                                1L,
                                List.of(supplyUnit()),
                                List.of()
                        )
                );

        assertEquals(
                "zones must not be empty",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullUnitElement() {
        List<CoolingUnitSnapshot> units = new ArrayList<>();
        units.add(null);

        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> new CoolingSnapshot(
                                1L,
                                units,
                                List.of(validZone())
                        )
                );

        assertEquals(
                "units must not contain null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullZoneElement() {
        List<CoolingZoneSnapshot> zones = new ArrayList<>();
        zones.add(null);

        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> new CoolingSnapshot(
                                1L,
                                List.of(supplyUnit()),
                                zones
                        )
                );

        assertEquals(
                "zones must not contain null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDuplicateUnitCodes() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new CoolingSnapshot(
                                1L,
                                List.of(
                                        new CoolingUnitSnapshot(
                                                "SUPPLY-01",
                                                CoolingUnitType.SUPPLY,
                                                true,
                                                10.0,
                                                5000.0
                                        ),
                                        new CoolingUnitSnapshot(
                                                "SUPPLY-01",
                                                CoolingUnitType.SUPPLY,
                                                true,
                                                8.0,
                                                3000.0
                                        )
                                ),
                                List.of(validZone())
                        )
                );

        assertEquals(
                "duplicate cooling-unit snapshot code: SUPPLY-01",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDuplicateZoneCodes() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new CoolingSnapshot(
                                1L,
                                List.of(supplyUnit()),
                                List.of(
                                        zone(
                                                "ZONE-01",
                                                1000.0,
                                                2000.0,
                                                1000.0,
                                                0.0,
                                                24.0,
                                                30.0,
                                                0.10
                                        ),
                                        zone(
                                                "ZONE-01",
                                                2000.0,
                                                3000.0,
                                                2000.0,
                                                0.0,
                                                25.0,
                                                35.0,
                                                0.20
                                        )
                                )
                        )
                );

        assertEquals(
                "duplicate cooling-zone snapshot code: ZONE-01",
                exception.getMessage()
        );
    }

    @Test
    void shouldMakeDefensiveCopyOfUnitsAndZones() {
        List<CoolingUnitSnapshot> units = new ArrayList<>();
        List<CoolingZoneSnapshot> zones = new ArrayList<>();

        units.add(supplyUnit());
        zones.add(validZone());

        CoolingSnapshot snapshot =
                new CoolingSnapshot(
                        1L,
                        units,
                        zones
                );

        units.add(
                new CoolingUnitSnapshot(
                        "SUPPLY-02",
                        CoolingUnitType.SUPPLY,
                        true,
                        5.0,
                        2000.0
                )
        );

        zones.add(
                zone(
                        "ZONE-02",
                        2000.0,
                        3000.0,
                        2000.0,
                        0.0,
                        25.0,
                        35.0,
                        0.20
                )
        );

        assertAll(
                () -> assertEquals(1, snapshot.units().size()),
                () -> assertEquals(1, snapshot.zones().size()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> snapshot.units().clear()
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> snapshot.zones().clear()
                )
        );
    }

    @Test
    void shouldRejectNullGroupCodeWhenAggregatingZones() {
        CoolingSnapshot snapshot = validSnapshot();

        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> snapshot.aggregateZones(
                                null,
                                List.of("ZONE-01")
                        )
                );

        assertEquals(
                "groupCode must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectBlankGroupCodeWhenAggregatingZones() {
        CoolingSnapshot snapshot = validSnapshot();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> snapshot.aggregateZones(
                                " ",
                                List.of("ZONE-01")
                        )
                );

        assertEquals(
                "groupCode must not be blank",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullZoneCodesWhenAggregatingZones() {
        CoolingSnapshot snapshot = validSnapshot();

        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> snapshot.aggregateZones(
                                "GROUP-01",
                                null
                        )
                );

        assertEquals(
                "zoneCodes must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectEmptyZoneCodesWhenAggregatingZones() {
        CoolingSnapshot snapshot = validSnapshot();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> snapshot.aggregateZones(
                                "GROUP-01",
                                List.of()
                        )
                );

        assertEquals(
                "zoneCodes must not be empty",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullZoneCodeElementWhenAggregatingZones() {
        CoolingSnapshot snapshot = validSnapshot();

        List<String> zoneCodes = new ArrayList<>();
        zoneCodes.add(null);

        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> snapshot.aggregateZones(
                                "GROUP-01",
                                zoneCodes
                        )
                );

        assertEquals(
                "zoneCodes must not contain null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectBlankZoneCodeElementWhenAggregatingZones() {
        CoolingSnapshot snapshot = validSnapshot();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> snapshot.aggregateZones(
                                "GROUP-01",
                                List.of(" ")
                        )
                );

        assertEquals(
                "zoneCodes must not contain blank values",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectUnknownZoneCodeWhenAggregatingZones() {
        CoolingSnapshot snapshot = validSnapshot();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> snapshot.aggregateZones(
                                "GROUP-01",
                                List.of("UNKNOWN-ZONE")
                        )
                );

        assertEquals(
                "unknown cooling-zone code: UNKNOWN-ZONE",
                exception.getMessage()
        );
    }

    private CoolingSnapshot validSnapshot() {
        return new CoolingSnapshot(
                1L,
                List.of(supplyUnit()),
                List.of(validZone())
        );
    }

    private CoolingUnitSnapshot supplyUnit() {
        return new CoolingUnitSnapshot(
                "SUPPLY-01",
                CoolingUnitType.SUPPLY,
                true,
                10.0,
                5000.0
        );
    }

    private CoolingZoneSnapshot validZone() {
        return zone(
                "ZONE-01",
                1000.0,
                2000.0,
                1000.0,
                0.0,
                24.0,
                30.0,
                0.10
        );
    }

    private CoolingZoneSnapshot zone(
            String zoneCode,
            double generatedHeatWatts,
            double availableCoolingCapacityWatts,
            double usedCoolingCapacityWatts,
            double coolingDeficitWatts,
            double inletAirTemperatureCelsius,
            double exhaustAirTemperatureCelsius,
            double recirculationFraction
    ) {
        return new CoolingZoneSnapshot(
                zoneCode,
                generatedHeatWatts,
                availableCoolingCapacityWatts,
                usedCoolingCapacityWatts,
                coolingDeficitWatts,
                10.0,
                10.0,
                inletAirTemperatureCelsius,
                exhaustAirTemperatureCelsius,
                recirculationFraction
        );
    }

    @Test
    void shouldCalculateThermalCoverage() {
        CoolingZoneGroupSnapshot snapshot =
                new CoolingZoneGroupSnapshot(
                        "GROUP-01",
                        List.of("ZONE-01", "ZONE-02"),
                        8000.0,
                        12000.0,
                        6000.0,
                        2000.0,
                        24.0,
                        36.0,
                        0.25
                );

        assertEquals(
                0.75,
                snapshot.thermalCoverage(),
                EPSILON
        );
    }
}
package com.cpz.sim.datacenter.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author CPZ
 */
class ColumnOperationalSnapshotTest {

    @Test
    void shouldCreateSnapshotForColumnWithOnlineServers() {
        ColumnOperationalSnapshot snapshot =
                new ColumnOperationalSnapshot(
                        "C01",
                        5,
                        3,
                        500.0,
                        2500.0,
                        1400.0,
                        58.5,
                        0.72
                );
        assertEquals("C01", snapshot.columnCode());
        assertEquals(5, snapshot.installedServerCount());
        assertEquals(3, snapshot.onlineServerCount());
        assertEquals(500.0, snapshot.idlePowerWatts());
        assertEquals(2500.0, snapshot.maxPowerWatts());
        assertEquals(1400.0, snapshot.currentPowerWatts());
        assertEquals(58.5, snapshot.averageOnlineTemperatureCelsius());
        assertEquals(0.72, snapshot.averageOnlineUtilization());
        assertTrue(snapshot.hasInstalledServers());
        assertTrue(snapshot.hasOnlineServers());
    }

    @Test
    void shouldCreateSnapshotForColumnWithOnlyOfflineServers() {
        ColumnOperationalSnapshot snapshot =
                new ColumnOperationalSnapshot(
                        "C02",
                        4,
                        0,
                        400.0,
                        2000.0,
                        0.0,
                        Double.NaN,
                        Double.NaN
                );
        assertEquals("C02", snapshot.columnCode());
        assertEquals(4, snapshot.installedServerCount());
        assertEquals(0, snapshot.onlineServerCount());
        assertEquals(400.0, snapshot.idlePowerWatts());
        assertEquals(2000.0, snapshot.maxPowerWatts());
        assertEquals(0.0, snapshot.currentPowerWatts());
        assertTrue(Double.isNaN(snapshot.averageOnlineTemperatureCelsius()));
        assertTrue(Double.isNaN(snapshot.averageOnlineUtilization()));
        assertTrue(snapshot.hasInstalledServers());
        assertFalse(snapshot.hasOnlineServers());
    }

    @Test
    void shouldCreateSnapshotForEmptyColumn() {
        ColumnOperationalSnapshot snapshot =
                new ColumnOperationalSnapshot(
                        "C03",
                        0,
                        0,
                        0.0,
                        0.0,
                        0.0,
                        Double.NaN,
                        Double.NaN
                );
        assertEquals("C03", snapshot.columnCode());
        assertEquals(0, snapshot.installedServerCount());
        assertEquals(0, snapshot.onlineServerCount());
        assertEquals(0.0, snapshot.idlePowerWatts());
        assertEquals(0.0, snapshot.maxPowerWatts());
        assertEquals(0.0, snapshot.currentPowerWatts());
        assertTrue(Double.isNaN(snapshot.averageOnlineTemperatureCelsius()));
        assertTrue(Double.isNaN(snapshot.averageOnlineUtilization()));
        assertFalse(snapshot.hasInstalledServers());
        assertFalse(snapshot.hasOnlineServers());
    }

    @Test
    void shouldAcceptUtilizationBoundaryValues() {
        ColumnOperationalSnapshot zeroUtilization =
                new ColumnOperationalSnapshot(
                        "C04",
                        1,
                        1,
                        100.0,
                        500.0,
                        100.0,
                        30.0,
                        0.0
                );
        ColumnOperationalSnapshot fullUtilization =
                new ColumnOperationalSnapshot(
                        "C05",
                        1,
                        1,
                        100.0,
                        500.0,
                        500.0,
                        70.0,
                        1.0
                );
        assertEquals(0.0, zeroUtilization.averageOnlineUtilization());
        assertEquals(1.0, fullUtilization.averageOnlineUtilization());
    }

    @Test
    void shouldRejectNullColumnCode() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ColumnOperationalSnapshot(
                        null,
                        1,
                        1,
                        100.0,
                        500.0,
                        300.0,
                        45.0,
                        0.5
                )
        );
        assertEquals("columnCode must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectBlankColumnCode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ColumnOperationalSnapshot(
                        " ",
                        1,
                        1,
                        100.0,
                        500.0,
                        300.0,
                        45.0,
                        0.5
                )
        );
        assertEquals("columnCode must not be blank", exception.getMessage());
    }

    @Test
    void shouldRejectNegativeInstalledServerCount() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ColumnOperationalSnapshot(
                        "C01",
                        -1,
                        0,
                        0.0,
                        0.0,
                        0.0,
                        Double.NaN,
                        Double.NaN
                )
        );
        assertEquals("installedServerCount must be >= 0", exception.getMessage());
    }

    @Test
    void shouldRejectNegativeOnlineServerCount() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ColumnOperationalSnapshot(
                        "C01",
                        1,
                        -1,
                        100.0,
                        500.0,
                        0.0,
                        Double.NaN,
                        Double.NaN
                )
        );
        assertEquals("onlineServerCount must be >= 0", exception.getMessage());
    }

    @Test
    void shouldRejectOnlineServerCountGreaterThanInstalledCount() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ColumnOperationalSnapshot(
                        "C01",
                        1,
                        2,
                        100.0,
                        500.0,
                        300.0,
                        45.0,
                        0.5
                )
        );
        assertEquals("onlineServerCount must not exceed installedServerCount", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidIdlePower() {
        for (double invalidValue : new double[]{-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ColumnOperationalSnapshot(
                            "C01",
                            1,
                            1,
                            invalidValue,
                            500.0,
                            300.0,
                            45.0,
                            0.5
                    )
            );
            assertEquals("idlePowerWatts must be finite and >= 0", exception.getMessage());
        }
    }

    @Test
    void shouldRejectInvalidMaxPower() {
        for (double invalidValue : new double[]{-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ColumnOperationalSnapshot(
                            "C01",
                            1,
                            1,
                            100.0,
                            invalidValue,
                            300.0,
                            45.0,
                            0.5
                    )
            );
            assertEquals("maxPowerWatts must be finite and >= 0", exception.getMessage());
        }
    }

    @Test
    void shouldRejectInvalidCurrentPower() {
        for (double invalidValue : new double[]{-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ColumnOperationalSnapshot(
                            "C01",
                            1,
                            1,
                            100.0,
                            500.0,
                            invalidValue,
                            45.0,
                            0.5
                    )
            );
            assertEquals("currentPowerWatts must be finite and >= 0", exception.getMessage());
        }
    }

    @Test
    void shouldRejectIdlePowerGreaterThanMaxPower() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ColumnOperationalSnapshot(
                        "C01",
                        1,
                        1,
                        600.0,
                        500.0,
                        300.0,
                        45.0,
                        0.5
                )
        );
        assertEquals("idlePowerWatts must not exceed maxPowerWatts", exception.getMessage());
    }

    @Test
    void shouldRejectCurrentPowerGreaterThanMaxPower() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ColumnOperationalSnapshot(
                        "C01",
                        1,
                        1,
                        100.0,
                        500.0,
                        600.0,
                        45.0,
                        0.5
                )
        );
        assertEquals("currentPowerWatts must not exceed maxPowerWatts", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidAverageOnlineTemperatureWhenServersAreOnline() {
        for (double invalidValue : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ColumnOperationalSnapshot(
                            "C01",
                            1,
                            1,
                            100.0,
                            500.0,
                            300.0,
                            invalidValue,
                            0.5
                    )
            );
            assertEquals("averageOnlineTemperatureCelsius must be finite when online servers exist", exception.getMessage());
        }
    }

    @Test
    void shouldRejectInvalidAverageOnlineUtilizationWhenServersAreOnline() {
        for (double invalidValue : new double[]{-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ColumnOperationalSnapshot(
                            "C01",
                            1,
                            1,
                            100.0,
                            500.0,
                            300.0,
                            45.0,
                            invalidValue
                    )
            );
            assertEquals("averageOnlineUtilization must be finite and between 0 and 1", exception.getMessage());
        }
    }

    @Test
    void shouldRequireNaNAverageOnlineTemperatureWhenNoServersAreOnline() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ColumnOperationalSnapshot(
                        "C01",
                        1,
                        0,
                        100.0,
                        500.0,
                        0.0,
                        24.0,
                        Double.NaN
                )
        );
        assertEquals("averageOnlineTemperatureCelsius must be NaN when there are no online servers", exception.getMessage());
    }

    @Test
    void shouldRequireNaNAverageOnlineUtilizationWhenNoServersAreOnline() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ColumnOperationalSnapshot(
                        "C01",
                        1,
                        0,
                        100.0,
                        500.0,
                        0.0,
                        Double.NaN,
                        0.0
                )
        );
        assertEquals("averageOnlineUtilization must be NaN when there are no online servers", exception.getMessage());
    }
}
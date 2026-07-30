package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author CPZ
 */
class RackOperationalSnapshotTest {

    private static final RackLocation RACK_LOCATION = new RackLocation("C01", new RackCode("R01"));

    private static RackOperationalSnapshot createOnlineSnapshot() {
        return new RackOperationalSnapshot(RACK_LOCATION, 3, 2, 300.0, 1500.0, 760.0, 60.0, 0.70);
    }

    @Test
    void shouldCreateValidRackOperationalSnapshot() {
        RackOperationalSnapshot snapshot = createOnlineSnapshot();
        assertAll(() -> assertEquals(RACK_LOCATION, snapshot.location()), () -> assertEquals(3, snapshot.installedServerCount()), () -> assertEquals(2, snapshot.onlineServerCount()), () -> assertEquals(300.0, snapshot.idlePowerWatts()), () -> assertEquals(1500.0, snapshot.maxPowerWatts()), () -> assertEquals(760.0, snapshot.currentPowerWatts()), () -> assertEquals(60.0, snapshot.averageOnlineTemperatureCelsius()), () -> assertEquals(0.70, snapshot.averageOnlineUtilization()), () -> assertTrue(snapshot.hasInstalledServers()), () -> assertTrue(snapshot.hasOnlineServers()));
    }

    @Test
    void shouldReportRackWithoutInstalledOrOnlineServers() {
        RackOperationalSnapshot snapshot = new RackOperationalSnapshot(RACK_LOCATION, 0, 0, 0.0, 0.0, 0.0, Double.NaN, Double.NaN);
        assertFalse(snapshot.hasInstalledServers());
        assertFalse(snapshot.hasOnlineServers());
    }

    @Test
    void shouldReportInstalledButOfflineServers() {
        RackOperationalSnapshot snapshot = new RackOperationalSnapshot(RACK_LOCATION, 3, 0, 300.0, 1500.0, 0.0, Double.NaN, Double.NaN);
        assertTrue(snapshot.hasInstalledServers());
        assertFalse(snapshot.hasOnlineServers());
    }

    @Test
    void shouldRejectNullLocation() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> new RackOperationalSnapshot(null, 3, 2, 300.0, 1500.0, 760.0, 60.0, 0.70));
        assertEquals("location must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectNegativeInstalledServerCount() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, -1, 0, 0.0, 0.0, 0.0, Double.NaN, Double.NaN));
        assertEquals("installedServerCount must be >= 0", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidOnlineServerCount() {
        int[] invalidValues = {-1, 4};
        for (int invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, invalidValue, 300.0, 1500.0, 760.0, 60.0, 0.70));
            assertEquals("onlineServerCount must be between 0 and installedServerCount", exception.getMessage());
        }
    }

    @Test
    void shouldRejectInvalidIdlePowerWatts() {
        double[] invalidValues = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, 2, invalidValue, 1500.0, 760.0, 60.0, 0.70));

            assertEquals("idlePowerWatts must be finite and >= 0", exception.getMessage());
        }
    }

    @Test
    void shouldRejectInvalidMaxPowerWatts() {
        double[] invalidValues = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, 2, 300.0, invalidValue, 760.0, 60.0, 0.70));

            assertEquals("maxPowerWatts must be finite and >= 0", exception.getMessage());
        }
    }

    @Test
    void shouldRejectInvalidCurrentPowerWatts() {
        double[] invalidValues = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, 2, 300.0, 1500.0, invalidValue, 60.0, 0.70));

            assertEquals("currentPowerWatts must be finite and >= 0", exception.getMessage());
        }
    }

    @Test
    void shouldRejectIdlePowerGreaterThanMaxPower() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, 2, 1600.0, 1500.0, 760.0, 60.0, 0.70));

        assertEquals("idlePowerWatts must not exceed maxPowerWatts", exception.getMessage());
    }

    @Test
    void shouldRejectCurrentPowerGreaterThanMaxPower() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, 2, 300.0, 1500.0, 1501.0, 60.0, 0.70));

        assertEquals("currentPowerWatts must not exceed maxPowerWatts", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidAverageOnlineTemperatureWhenServersAreOnline() {
        double[] invalidValues = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new RackOperationalSnapshot(
                            RACK_LOCATION,
                            3,
                            2,
                            300.0,
                            1500.0,
                            760.0,
                            invalidValue,
                            0.70
                    )
            );

            assertEquals(
                    "averageOnlineTemperatureCelsius must be finite "
                            + "when online servers exist",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectInvalidAverageOnlineUtilizationWhenServersAreOnline() {
        double[] invalidValues = {
                -0.01,
                1.01,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new RackOperationalSnapshot(
                            RACK_LOCATION,
                            3,
                            2,
                            300.0,
                            1500.0,
                            760.0,
                            60.0,
                            invalidValue
                    )
            );

            assertEquals(
                    "averageOnlineUtilization must be finite "
                            + "and between 0 and 1",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRequireNaNAverageOnlineTemperatureWhenNoServersAreOnline() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RackOperationalSnapshot(
                        RACK_LOCATION,
                        3,
                        0,
                        300.0,
                        1500.0,
                        0.0,
                        60.0,
                        Double.NaN
                )
        );

        assertEquals(
                "averageOnlineTemperatureCelsius must be NaN "
                        + "when there are no online servers",
                exception.getMessage()
        );
    }

    @Test
    void shouldRequireNaNAverageOnlineUtilizationWhenNoServersAreOnline() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RackOperationalSnapshot(
                        RACK_LOCATION,
                        3,
                        0,
                        300.0,
                        1500.0,
                        0.0,
                        Double.NaN,
                        0.0
                )
        );

        assertEquals(
                "averageOnlineUtilization must be NaN "
                        + "when there are no online servers",
                exception.getMessage()
        );
    }
}
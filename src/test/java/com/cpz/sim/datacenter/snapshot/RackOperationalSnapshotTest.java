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
    private static final double AMBIENT_TEMPERATURE_CELSIUS = 24.0;

    private static RackOperationalSnapshot createOnlineSnapshot() {
        return new RackOperationalSnapshot(RACK_LOCATION, 3, 2, 300.0, 1500.0, 760.0, 60.0, 60.0, 0.70);
    }

    @Test
    void shouldCreateValidRackOperationalSnapshot() {
        RackOperationalSnapshot snapshot = createOnlineSnapshot();
        assertAll(() -> assertEquals(RACK_LOCATION, snapshot.location()), () -> assertEquals(3, snapshot.installedServerCount()), () -> assertEquals(2, snapshot.onlineServerCount()), () -> assertEquals(300.0, snapshot.idlePowerWatts()), () -> assertEquals(1500.0, snapshot.maxPowerWatts()), () -> assertEquals(760.0, snapshot.currentPowerWatts()), () -> assertEquals(60.0, snapshot.averageOnlineTemperatureCelsius()), () -> assertEquals(snapshot.averageOnlineTemperatureCelsius(), snapshot.representativeTemperatureCelsius()), () -> assertEquals(0.70, snapshot.averageOnlineUtilization()), () -> assertTrue(snapshot.hasInstalledServers()), () -> assertTrue(snapshot.hasOnlineServers()));
    }

    @Test
    void shouldReportRackWithoutInstalledOrOnlineServers() {
        RackOperationalSnapshot snapshot = new RackOperationalSnapshot(RACK_LOCATION, 0, 0, 0.0, 0.0, 0.0, Double.NaN, AMBIENT_TEMPERATURE_CELSIUS, Double.NaN);
        assertFalse(snapshot.hasInstalledServers());
        assertFalse(snapshot.hasOnlineServers());
        assertTrue(Double.isNaN(snapshot.averageOnlineTemperatureCelsius()));
        assertEquals(AMBIENT_TEMPERATURE_CELSIUS, snapshot.representativeTemperatureCelsius());
    }

    @Test
    void shouldReportInstalledButOfflineServers() {
        RackOperationalSnapshot snapshot = new RackOperationalSnapshot(RACK_LOCATION, 3, 0, 300.0, 1500.0, 0.0, Double.NaN, AMBIENT_TEMPERATURE_CELSIUS, Double.NaN);
        assertTrue(snapshot.hasInstalledServers());
        assertFalse(snapshot.hasOnlineServers());
        assertTrue(Double.isNaN(snapshot.averageOnlineTemperatureCelsius()));
        assertEquals(AMBIENT_TEMPERATURE_CELSIUS, snapshot.representativeTemperatureCelsius());
    }

    @Test
    void shouldRejectNullLocation() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> new RackOperationalSnapshot(null, 3, 2, 300.0, 1500.0, 760.0, 60.0, 60.0, 0.70));
        assertEquals("location must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectNegativeInstalledServerCount() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, -1, 0, 0.0, 0.0, 0.0, Double.NaN, AMBIENT_TEMPERATURE_CELSIUS, Double.NaN));
        assertEquals("installedServerCount must be >= 0", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidOnlineServerCount() {
        int[] invalidValues = {-1, 4};
        for (int invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, invalidValue, 300.0, 1500.0, 760.0, 60.0, 60.0, 0.70));
            assertEquals("onlineServerCount must be between 0 and installedServerCount", exception.getMessage());
        }
    }

    @Test
    void shouldRejectInvalidIdlePowerWatts() {
        double[] invalidValues = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, 2, invalidValue, 1500.0, 760.0, 60.0, 60.0, 0.70));

            assertEquals("idlePowerWatts must be finite and >= 0", exception.getMessage());
        }
    }

    @Test
    void shouldRejectInvalidMaxPowerWatts() {
        double[] invalidValues = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, 2, 300.0, invalidValue, 760.0, 60.0, 60.0, 0.70));

            assertEquals("maxPowerWatts must be finite and >= 0", exception.getMessage());
        }
    }

    @Test
    void shouldRejectInvalidCurrentPowerWatts() {
        double[] invalidValues = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, 2, 300.0, 1500.0, invalidValue, 60.0, 60.0, 0.70));

            assertEquals("currentPowerWatts must be finite and >= 0", exception.getMessage());
        }
    }

    @Test
    void shouldRejectIdlePowerGreaterThanMaxPower() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, 2, 1600.0, 1500.0, 760.0, 60.0, 60.0, 0.70));

        assertEquals("idlePowerWatts must not exceed maxPowerWatts", exception.getMessage());
    }

    @Test
    void shouldRejectCurrentPowerGreaterThanMaxPower() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new RackOperationalSnapshot(RACK_LOCATION, 3, 2, 300.0, 1500.0, 1501.0, 60.0, 60.0, 0.70));

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
                            60.0,
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
                        AMBIENT_TEMPERATURE_CELSIUS,
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
                        AMBIENT_TEMPERATURE_CELSIUS,
                        0.0
                )
        );

        assertEquals(
                "averageOnlineUtilization must be NaN "
                        + "when there are no online servers",
                exception.getMessage()
        );
    }

    @Test
    void shouldAllowAllInstalledServersToBeOnline() {
        RackOperationalSnapshot snapshot =
                new RackOperationalSnapshot(
                        RACK_LOCATION,
                        3,
                        3,
                        300.0,
                        1500.0,
                        900.0,
                        55.0,
                        55.0,
                        0.60
                );

        assertAll(
                () -> assertEquals(
                        3,
                        snapshot.installedServerCount()
                ),
                () -> assertEquals(
                        3,
                        snapshot.onlineServerCount()
                ),
                () -> assertTrue(snapshot.hasInstalledServers()),
                () -> assertTrue(snapshot.hasOnlineServers())
        );
    }

    @Test
    void shouldAllowAverageOnlineUtilizationBoundaries() {
        RackOperationalSnapshot zeroUtilization =
                new RackOperationalSnapshot(
                        RACK_LOCATION,
                        3,
                        2,
                        300.0,
                        1500.0,
                        300.0,
                        40.0,
                        40.0,
                        0.0
                );

        RackOperationalSnapshot fullUtilization =
                new RackOperationalSnapshot(
                        RACK_LOCATION,
                        3,
                        2,
                        300.0,
                        1500.0,
                        1500.0,
                        80.0,
                        80.0,
                        1.0
                );

        assertAll(
                () -> assertEquals(
                        0.0,
                        zeroUtilization.averageOnlineUtilization()
                ),
                () -> assertEquals(
                        1.0,
                        fullUtilization.averageOnlineUtilization()
                )
        );
    }

    @Test
    void shouldAllowNegativeFiniteAverageOnlineTemperature() {
        RackOperationalSnapshot snapshot =
                new RackOperationalSnapshot(
                        RACK_LOCATION,
                        3,
                        2,
                        300.0,
                        1500.0,
                        760.0,
                        -10.0,
                        -10.0,
                        0.70
                );

        assertEquals(
                -10.0,
                snapshot.averageOnlineTemperatureCelsius()
        );
    }

    @Test
    void shouldRejectInvalidRepresentativeTemperature() {
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
                            60.0,
                            invalidValue,
                            0.70
                    )
            );

            assertEquals(
                    "representativeTemperatureCelsius must be finite",
                    exception.getMessage()
            );
        }
    }
}

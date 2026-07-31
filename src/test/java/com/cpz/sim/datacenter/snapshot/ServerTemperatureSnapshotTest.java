package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ServerLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class ServerTemperatureSnapshotTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void shouldExposeSnapshotDataAndLocation() {
        RackCode rackCode = new RackCode("RACK-01");

        ServerTemperatureSnapshot snapshot =
                new ServerTemperatureSnapshot(
                        "server-01",
                        "C01",
                        rackCode,
                        "S01",
                        HardwareStatus.OK,
                        0.75,
                        250.0,
                        48.5
                );

        assertAll(
                () -> assertEquals(
                        "server-01",
                        snapshot.serverCode()
                ),
                () -> assertEquals(
                        "C01",
                        snapshot.column()
                ),
                () -> assertEquals(
                        rackCode,
                        snapshot.rackCode()
                ),
                () -> assertEquals(
                        "S01",
                        snapshot.slot()
                ),
                () -> assertEquals(
                        HardwareStatus.OK,
                        snapshot.status()
                ),
                () -> assertEquals(
                        0.75,
                        snapshot.utilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        250.0,
                        snapshot.currentPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        48.5,
                        snapshot.temperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        new ServerLocation(
                                "C01",
                                rackCode,
                                "S01"
                        ),
                        snapshot.location()
                )
        );
    }

    @Test
    void shouldRejectInvalidServerCode() {
        NullPointerException nullException = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithServerCode(null)
        );

        IllegalArgumentException blankException = assertThrows(
                IllegalArgumentException.class,
                () -> createSnapshotWithServerCode(" ")
        );

        assertAll(
                () -> assertEquals(
                        "serverCode must not be null",
                        nullException.getMessage()
                ),
                () -> assertEquals(
                        "serverCode must not be blank",
                        blankException.getMessage()
                )
        );
    }

    @Test
    void shouldRejectInvalidColumn() {
        NullPointerException nullException = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithColumn(null)
        );

        IllegalArgumentException blankException = assertThrows(
                IllegalArgumentException.class,
                () -> createSnapshotWithColumn(" ")
        );

        assertAll(
                () -> assertEquals(
                        "column must not be null",
                        nullException.getMessage()
                ),
                () -> assertEquals(
                        "column must not be blank",
                        blankException.getMessage()
                )
        );
    }

    @Test
    void shouldRejectNullRackCode() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithRackCode(null)
        );

        assertEquals(
                "rackCode must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInvalidSlot() {
        NullPointerException nullException = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithSlot(null)
        );

        IllegalArgumentException blankException = assertThrows(
                IllegalArgumentException.class,
                () -> createSnapshotWithSlot(" ")
        );

        assertAll(
                () -> assertEquals(
                        "slot must not be null",
                        nullException.getMessage()
                ),
                () -> assertEquals(
                        "slot must not be blank",
                        blankException.getMessage()
                )
        );
    }

    @Test
    void shouldRejectNullStatus() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithStatus(null)
        );

        assertEquals(
                "status must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInvalidUtilization() {
        for (double invalidValue : new double[]{
                -0.1,
                1.1,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> createSnapshotWithUtilization(invalidValue)
            );

            assertEquals(
                    "utilization must be finite and between 0 and 1",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectInvalidCurrentPowerWatts() {
        for (double invalidValue : new double[]{
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> createSnapshotWithCurrentPowerWatts(
                            invalidValue
                    )
            );

            assertEquals(
                    "currentPowerWatts must be finite and >= 0",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectNonFiniteTemperatureCelsius() {
        for (double invalidValue : new double[]{
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> createSnapshotWithTemperatureCelsius(
                            invalidValue
                    )
            );

            assertEquals(
                    "temperatureCelsius must be finite",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldAllowNegativeTemperatureCelsius() {
        ServerTemperatureSnapshot snapshot =
                createSnapshotWithTemperatureCelsius(-10.0);

        assertEquals(
                -10.0,
                snapshot.temperatureCelsius(),
                EPSILON
        );
    }

    private static ServerTemperatureSnapshot createSnapshotWithServerCode(
            String serverCode
    ) {
        return createSnapshot(
                serverCode,
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                0.50,
                200.0,
                40.0
        );
    }

    private static ServerTemperatureSnapshot createSnapshotWithColumn(
            String column
    ) {
        return createSnapshot(
                "server-01",
                column,
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                0.50,
                200.0,
                40.0
        );
    }

    private static ServerTemperatureSnapshot createSnapshotWithRackCode(
            RackCode rackCode
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                rackCode,
                "S01",
                HardwareStatus.OK,
                0.50,
                200.0,
                40.0
        );
    }

    private static ServerTemperatureSnapshot createSnapshotWithSlot(
            String slot
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                slot,
                HardwareStatus.OK,
                0.50,
                200.0,
                40.0
        );
    }

    private static ServerTemperatureSnapshot createSnapshotWithStatus(
            HardwareStatus status
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                status,
                0.50,
                200.0,
                40.0
        );
    }

    private static ServerTemperatureSnapshot createSnapshotWithUtilization(
            double utilization
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                utilization,
                200.0,
                40.0
        );
    }

    private static ServerTemperatureSnapshot
    createSnapshotWithCurrentPowerWatts(
            double currentPowerWatts
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                0.50,
                currentPowerWatts,
                40.0
        );
    }

    private static ServerTemperatureSnapshot
    createSnapshotWithTemperatureCelsius(
            double temperatureCelsius
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                0.50,
                200.0,
                temperatureCelsius
        );
    }

    private static ServerTemperatureSnapshot createSnapshot(
            String serverCode,
            String column,
            RackCode rackCode,
            String slot,
            HardwareStatus status,
            double utilization,
            double currentPowerWatts,
            double temperatureCelsius
    ) {
        return new ServerTemperatureSnapshot(
                serverCode,
                column,
                rackCode,
                slot,
                status,
                utilization,
                currentPowerWatts,
                temperatureCelsius
        );
    }
}
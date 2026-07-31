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
class ServerEnergySnapshotTest {

    private static final double EPSILON = 1.0e-9;
    private static final float FLOAT_EPSILON = 1.0e-6f;

    @Test
    void shouldExposeSnapshotDataAndLocation() {
        RackCode rackCode = new RackCode("RACK-01");

        ServerEnergySnapshot snapshot =
                new ServerEnergySnapshot(
                        "server-01",
                        "C01",
                        rackCode,
                        "S01",
                        HardwareStatus.OK,
                        0.75,
                        100.0f,
                        300.0f,
                        250.0f
                );

        assertAll(
                () -> assertEquals(
                        "server-01",
                        snapshot.serverCode()
                ),
                () -> assertEquals("C01", snapshot.column()),
                () -> assertEquals(rackCode, snapshot.rackCode()),
                () -> assertEquals("S01", snapshot.slot()),
                () -> assertEquals(HardwareStatus.OK, snapshot.status()),
                () -> assertEquals(
                        0.75,
                        snapshot.utilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        100.0f,
                        snapshot.idlePowerWatts(),
                        FLOAT_EPSILON
                ),
                () -> assertEquals(
                        300.0f,
                        snapshot.maxPowerWatts(),
                        FLOAT_EPSILON
                ),
                () -> assertEquals(
                        250.0f,
                        snapshot.currentPowerWatts(),
                        FLOAT_EPSILON
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
                () -> createSnapshot(null)
        );

        IllegalArgumentException blankException = assertThrows(
                IllegalArgumentException.class,
                () -> createSnapshot(" ")
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

    private static ServerEnergySnapshot createSnapshot(
            String serverCode
    ) {
        return new ServerEnergySnapshot(
                serverCode,
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                0.50,
                100.0f,
                300.0f,
                200.0f
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

    private static ServerEnergySnapshot createSnapshotWithColumn(
            String column
    ) {
        return new ServerEnergySnapshot(
                "server-01",
                column,
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                0.50,
                100.0f,
                300.0f,
                200.0f
        );
    }

    private static ServerEnergySnapshot createSnapshotWithRackCode(
            RackCode rackCode
    ) {
        return new ServerEnergySnapshot(
                "server-01",
                "C01",
                rackCode,
                "S01",
                HardwareStatus.OK,
                0.50,
                100.0f,
                300.0f,
                200.0f
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
    void shouldRejectInvalidStatus() {
        NullPointerException nullException = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithStatus(null)
        );

        assertEquals(
                "status must not be null",
                nullException.getMessage()
        );
    }

    private static ServerEnergySnapshot createSnapshotWithSlot(
            String slot
    ) {
        return new ServerEnergySnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                slot,
                HardwareStatus.OK,
                0.50,
                100.0f,
                300.0f,
                200.0f
        );
    }

    private static ServerEnergySnapshot createSnapshotWithStatus(
            HardwareStatus status
    ) {
        return new ServerEnergySnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                status,
                0.50,
                100.0f,
                300.0f,
                200.0f
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

    private static ServerEnergySnapshot createSnapshotWithUtilization(
            double utilization
    ) {
        return new ServerEnergySnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                utilization,
                100.0f,
                300.0f,
                200.0f
        );
    }

    @Test
    void shouldRejectInvalidIdlePowerWatts() {
        for (float invalidValue : new float[]{
                -1.0f,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> createSnapshotWithIdlePowerWatts(invalidValue)
            );

            assertEquals(
                    "idlePowerWatts must be finite and >= 0",
                    exception.getMessage()
            );
        }
    }

    private static ServerEnergySnapshot createSnapshotWithIdlePowerWatts(
            float idlePowerWatts
    ) {
        return new ServerEnergySnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                0.50,
                idlePowerWatts,
                300.0f,
                200.0f
        );
    }

    @Test
    void shouldRejectInvalidMaxPowerWatts() {
        for (float invalidValue : new float[]{
                -1.0f,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> createSnapshotWithMaxPowerWatts(invalidValue)
            );

            assertEquals(
                    "maxPowerWatts must be finite and greater than idlePowerWatts",
                    exception.getMessage()
            );
        }
    }

    private static ServerEnergySnapshot createSnapshotWithMaxPowerWatts(
            float maxPowerWatts
    ) {
        return new ServerEnergySnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                0.50,
                100.0f,
                maxPowerWatts,
                200.0f
        );
    }

    @Test
    void shouldRejectInvalidCurrentPowerWatts() {
        for (float invalidValue : new float[]{
                -1.0f,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> createSnapshotWithCurrentPowerWatts(invalidValue)
            );

            assertEquals(
                    "currentPowerWatts must be finite and >= 0",
                    exception.getMessage()
            );
        }
    }

    private static ServerEnergySnapshot createSnapshotWithCurrentPowerWatts(
            float currentPowerWatts
    ) {
        return new ServerEnergySnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                0.50,
                100.0f,
                300.0f,
                currentPowerWatts
        );
    }
}

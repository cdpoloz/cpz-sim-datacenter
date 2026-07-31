package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ServerLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class ServerHealthSnapshotTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void shouldExposeSnapshotDataWithoutAlertsAndLocation() {
        RackCode rackCode = new RackCode("RACK-01");

        ServerHealthSnapshot snapshot =
                new ServerHealthSnapshot(
                        "server-01",
                        "C01",
                        rackCode,
                        "S01",
                        HardwareStatus.OK,
                        Set.of(),
                        0.50,
                        42.5
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
                        Set.of(),
                        snapshot.alertReasons()
                ),
                () -> assertEquals(
                        0.50,
                        snapshot.utilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        42.5,
                        snapshot.temperatureCelsius(),
                        EPSILON
                ),
                () -> assertEquals(
                        new ServerLocation("C01", rackCode, "S01"),
                        snapshot.location()
                ),
                () -> assertFalse(snapshot.hasAlerts())
        );
    }

    @Test
    void shouldExposeActiveAlertReasons() {
        ServerHealthSnapshot snapshot =
                createSnapshotWithAlertReasons(
                        Set.of(
                                ServerAlertReason.HIGH_UTILIZATION,
                                ServerAlertReason.HIGH_TEMPERATURE
                        )
                );

        assertAll(
                () -> assertTrue(snapshot.hasAlerts()),
                () -> assertTrue(
                        snapshot.hasAlertReason(
                                ServerAlertReason.HIGH_UTILIZATION
                        )
                ),
                () -> assertTrue(
                        snapshot.hasAlertReason(
                                ServerAlertReason.HIGH_TEMPERATURE
                        )
                ),
                () -> assertEquals(
                        Set.of(
                                ServerAlertReason.HIGH_UTILIZATION,
                                ServerAlertReason.HIGH_TEMPERATURE
                        ),
                        snapshot.alertReasons()
                )
        );
    }

    @Test
    void shouldReturnFalseForInactiveAlertReason() {
        ServerHealthSnapshot snapshot =
                createSnapshotWithAlertReasons(
                        Set.of(
                                ServerAlertReason.HIGH_UTILIZATION
                        )
                );

        assertFalse(
                snapshot.hasAlertReason(
                        ServerAlertReason.HIGH_TEMPERATURE
                )
        );
    }

    @Test
    void shouldRejectNullServerCode() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithServerCode(null)
        );

        assertEquals(
                "serverCode must not be null.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectBlankServerCode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createSnapshotWithServerCode(" ")
        );

        assertEquals(
                "serverCode must not be blank",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullColumn() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithColumn(null)
        );

        assertEquals(
                "column must not be null.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectBlankColumn() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createSnapshotWithColumn(" ")
        );

        assertEquals(
                "column must not be blank",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullRackCode() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithRackCode(null)
        );

        assertEquals(
                "rackCode must not be null.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullSlot() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithSlot(null)
        );

        assertEquals(
                "slot must not be null.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectBlankSlot() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createSnapshotWithSlot(" ")
        );

        assertEquals(
                "slot must not be blank",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullStatus() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithStatus(null)
        );

        assertEquals(
                "status must not be null.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullAlertReasons() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithAlertReasons(null)
        );

        assertEquals(
                "alertReasons must not be null.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullAlertReasonElement() {
        Set<ServerAlertReason> alertReasons = new HashSet<>();
        alertReasons.add(null);

        assertThrows(
                NullPointerException.class,
                () -> createSnapshotWithAlertReasons(alertReasons)
        );
    }

    @Test
    void shouldMakeDefensiveCopyOfAlertReasons() {
        Set<ServerAlertReason> alertReasons = new HashSet<>();
        alertReasons.add(
                ServerAlertReason.HIGH_UTILIZATION
        );

        ServerHealthSnapshot snapshot =
                createSnapshotWithAlertReasons(alertReasons);

        alertReasons.clear();
        alertReasons.add(
                ServerAlertReason.HIGH_TEMPERATURE
        );

        assertAll(
                () -> assertEquals(
                        Set.of(
                                ServerAlertReason.HIGH_UTILIZATION
                        ),
                        snapshot.alertReasons()
                ),
                () -> assertTrue(
                        snapshot.hasAlertReason(
                                ServerAlertReason.HIGH_UTILIZATION
                        )
                ),
                () -> assertFalse(
                        snapshot.hasAlertReason(
                                ServerAlertReason.HIGH_TEMPERATURE
                        )
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> snapshot.alertReasons().clear()
                )
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
                    () -> createSnapshotWithUtilization(
                            invalidValue
                    )
            );

            assertEquals(
                    "utilization must be finite and between 0 and 1",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldAcceptBoundaryUtilizationValues() {
        assertAll(
                () -> assertEquals(
                        0.0,
                        createSnapshotWithUtilization(0.0).utilization(),
                        EPSILON
                ),
                () -> assertEquals(
                        1.0,
                        createSnapshotWithUtilization(1.0).utilization(),
                        EPSILON
                )
        );
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
                    "temperatureCelsius must be finite.",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldAllowNegativeFiniteTemperatureCelsius() {
        ServerHealthSnapshot snapshot =
                createSnapshotWithTemperatureCelsius(-10.0);

        assertEquals(
                -10.0,
                snapshot.temperatureCelsius(),
                EPSILON
        );
    }

    private static ServerHealthSnapshot createSnapshotWithServerCode(
            String serverCode
    ) {
        return createSnapshot(
                serverCode,
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                Set.of(),
                0.50,
                42.5
        );
    }

    private static ServerHealthSnapshot createSnapshotWithColumn(
            String column
    ) {
        return createSnapshot(
                "server-01",
                column,
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                Set.of(),
                0.50,
                42.5
        );
    }

    private static ServerHealthSnapshot createSnapshotWithRackCode(
            RackCode rackCode
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                rackCode,
                "S01",
                HardwareStatus.OK,
                Set.of(),
                0.50,
                42.5
        );
    }

    private static ServerHealthSnapshot createSnapshotWithSlot(
            String slot
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                slot,
                HardwareStatus.OK,
                Set.of(),
                0.50,
                42.5
        );
    }

    private static ServerHealthSnapshot createSnapshotWithStatus(
            HardwareStatus status
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                status,
                Set.of(),
                0.50,
                42.5
        );
    }

    private static ServerHealthSnapshot
    createSnapshotWithAlertReasons(
            Set<ServerAlertReason> alertReasons
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.ALERT,
                alertReasons,
                0.90,
                82.0
        );
    }

    private static ServerHealthSnapshot
    createSnapshotWithUtilization(
            double utilization
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                Set.of(),
                utilization,
                42.5
        );
    }

    private static ServerHealthSnapshot
    createSnapshotWithTemperatureCelsius(
            double temperatureCelsius
    ) {
        return createSnapshot(
                "server-01",
                "C01",
                new RackCode("RACK-01"),
                "S01",
                HardwareStatus.OK,
                Set.of(),
                0.50,
                temperatureCelsius
        );
    }

    private static ServerHealthSnapshot createSnapshot(
            String serverCode,
            String column,
            RackCode rackCode,
            String slot,
            HardwareStatus status,
            Set<ServerAlertReason> alertReasons,
            double utilization,
            double temperatureCelsius
    ) {
        return new ServerHealthSnapshot(
                serverCode,
                column,
                rackCode,
                slot,
                status,
                alertReasons,
                utilization,
                temperatureCelsius
        );
    }
}

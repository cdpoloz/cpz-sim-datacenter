package com.cpz.sim.datacenter.snapshot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class EnergyConsumptionSnapshotTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void shouldExposeSnapshotDataAndConvertEnergyToKilowattHours() {
        EnergyConsumptionSnapshot snapshot =
                new EnergyConsumptionSnapshot(
                        10L,
                        300.0,
                        1250.0,
                        3750.0,
                        List.of()
                );

        assertAll(
                () -> assertEquals(10L, snapshot.tickIndex()),
                () -> assertEquals(
                        300.0,
                        snapshot.elapsedSeconds(),
                        EPSILON
                ),
                () -> assertEquals(
                        1250.0,
                        snapshot.totalItPowerWatts(),
                        EPSILON
                ),
                () -> assertEquals(
                        3750.0,
                        snapshot.consumedEnergyWh(),
                        EPSILON
                ),
                () -> assertEquals(
                        3.75,
                        snapshot.consumedEnergyKWh(),
                        EPSILON
                ),
                () -> assertEquals(0, snapshot.serverCount()),
                () -> assertEquals(List.of(), snapshot.servers())
        );
    }

    @Test
    void shouldRejectNegativeTickIndex() {
        for (long invalidValue : new long[]{
                -1L,
                Long.MIN_VALUE
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new EnergyConsumptionSnapshot(
                            invalidValue,
                            0.0,
                            0.0,
                            0.0,
                            List.of()
                    )
            );

            assertEquals(
                    "tickIndex must be >= 0",
                    exception.getMessage()
            );
        }
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
                    () -> new EnergyConsumptionSnapshot(
                            1L,
                            invalidValue,
                            0.0,
                            0.0,
                            List.of()
                    )
            );

            assertEquals(
                    "elapsedSeconds must be finite and >= 0",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectInvalidTotalItPowerWatts() {
        for (double invalidValue : new double[]{
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new EnergyConsumptionSnapshot(
                            1L,
                            0.0,
                            invalidValue,
                            0.0,
                            List.of()
                    )
            );

            assertEquals(
                    "totalItPowerWatts must be finite and >= 0",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectInvalidConsumedEnergyWh() {
        for (double invalidValue : new double[]{
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new EnergyConsumptionSnapshot(
                            1L,
                            0.0,
                            0.0,
                            invalidValue,
                            List.of()
                    )
            );

            assertEquals(
                    "consumedEnergyWh must be finite and >= 0",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectNullServerList() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new EnergyConsumptionSnapshot(
                        1L,
                        0.0,
                        0.0,
                        0.0,
                        null
                )
        );

        assertEquals(
                "servers must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullServerElement() {
        List<ServerEnergySnapshot> servers = new ArrayList<>();
        servers.add(null);

        assertThrows(
                NullPointerException.class,
                () -> new EnergyConsumptionSnapshot(
                        1L,
                        0.0,
                        0.0,
                        0.0,
                        servers
                )
        );
    }

    @Test
    void shouldMakeDefensiveCopyOfServers() {
        List<ServerEnergySnapshot> servers = new ArrayList<>();

        EnergyConsumptionSnapshot snapshot =
                new EnergyConsumptionSnapshot(
                        1L,
                        0.0,
                        0.0,
                        0.0,
                        servers
                );

        servers.add(null);

        assertAll(
                () -> assertEquals(0, snapshot.serverCount()),
                () -> assertEquals(List.of(), snapshot.servers()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> snapshot.servers().clear()
                )
        );
    }

}
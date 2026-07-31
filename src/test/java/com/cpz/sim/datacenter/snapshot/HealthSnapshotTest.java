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
class HealthSnapshotTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void shouldExposeEmptySnapshotData() {
        HealthSnapshot snapshot =
                new HealthSnapshot(
                        10L,
                        300.0,
                        List.of()
                );

        assertAll(
                () -> assertEquals(10L, snapshot.tickIndex()),
                () -> assertEquals(
                        300.0,
                        snapshot.elapsedSeconds(),
                        EPSILON
                ),
                () -> assertEquals(0, snapshot.serverCount()),
                () -> assertEquals(0L, snapshot.alertServerCount()),
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
                    () -> new HealthSnapshot(
                            invalidValue,
                            0.0,
                            List.of()
                    )
            );

            assertEquals(
                    "tickIndex must be greater than or equal to zero.",
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
                    () -> new HealthSnapshot(
                            1L,
                            invalidValue,
                            List.of()
                    )
            );

            assertEquals(
                    "elapsedSeconds must be finite "
                            + "and greater than or equal to zero.",
                    exception.getMessage()
            );
        }
    }

    @Test
    void shouldRejectNullServerList() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new HealthSnapshot(
                        1L,
                        0.0,
                        null
                )
        );

        assertEquals(
                "servers must not be null.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullServerElement() {
        List<ServerHealthSnapshot> servers = new ArrayList<>();
        servers.add(null);

        assertThrows(
                NullPointerException.class,
                () -> new HealthSnapshot(
                        1L,
                        0.0,
                        servers
                )
        );
    }

    @Test
    void shouldMakeDefensiveCopyOfServers() {
        List<ServerHealthSnapshot> servers = new ArrayList<>();

        HealthSnapshot snapshot =
                new HealthSnapshot(
                        1L,
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
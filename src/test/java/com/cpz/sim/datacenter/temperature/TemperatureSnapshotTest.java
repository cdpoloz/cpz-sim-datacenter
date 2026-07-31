package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.snapshot.ServerTemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author CPZ
 */
class TemperatureSnapshotTest {

    @Test
    void calculatesServerCountAverageAndMaxTemperature() {
        TemperatureSnapshot snapshot = new TemperatureSnapshot(
                3L,
                1800.0,
                25.0,
                List.of(
                        new ServerTemperatureSnapshot(
                                "server-01",
                                "C01",
                                new RackCode("RACK-01"),
                                "U01",
                                HardwareStatus.OK,
                                0.5f,
                                200.0,
                                30.0
                        ),
                        new ServerTemperatureSnapshot(
                                "server-02",
                                "C01",
                                new RackCode("RACK-01"),
                                "U02",
                                HardwareStatus.OK,
                                0.8f,
                                320.0,
                                36.0
                        )
                )
        );
        assertEquals(2, snapshot.serverCount());
        assertEquals(33.0, snapshot.averageTemperatureCelsius());
        assertEquals(36.0, snapshot.maxTemperatureCelsius());
    }

    @Test
    void emptySnapshotHasZeroServerCountAverageAndMaxTemperature() {
        TemperatureSnapshot snapshot = new TemperatureSnapshot(
                1L,
                0.0,
                25.0,
                List.of()
        );
        assertEquals(0, snapshot.serverCount());
        assertEquals(0.0, snapshot.averageTemperatureCelsius());
        assertEquals(0.0, snapshot.maxTemperatureCelsius());
    }

    @Test
    void rejectsNullServerList() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new TemperatureSnapshot(
                        1L,
                        0.0,
                        25.0,
                        null
                )
        );

        assertEquals(
                "servers must not be null.",
                exception.getMessage()
        );
    }

    @Test
    void rejectsNullServerElement() {
        List<ServerTemperatureSnapshot> servers =
                new ArrayList<>();

        servers.add(null);

        assertThrows(
                NullPointerException.class,
                () -> new TemperatureSnapshot(
                        1L,
                        0.0,
                        25.0,
                        servers
                )
        );
    }

    @Test
    void makesDefensiveCopyOfServers() {
        List<ServerTemperatureSnapshot> servers =
                new ArrayList<>();

        servers.add(
                new ServerTemperatureSnapshot(
                        "server-01",
                        "C01",
                        new RackCode("RACK-01"),
                        "U01",
                        HardwareStatus.OK,
                        0.5f,
                        200.0,
                        30.0
                )
        );

        TemperatureSnapshot snapshot =
                new TemperatureSnapshot(
                        1L,
                        0.0,
                        25.0,
                        servers
                );

        servers.clear();

        assertAll(
                () -> assertEquals(1, snapshot.serverCount()),
                () -> assertEquals(1, snapshot.servers().size()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> snapshot.servers().clear()
                )
        );
    }

    @Test
    void rejectsNegativeTickIndex() {
        for (long invalidValue : new long[]{
                -1L,
                Long.MIN_VALUE
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new TemperatureSnapshot(
                            invalidValue,
                            0.0,
                            25.0,
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
    void rejectsInvalidElapsedSeconds() {
        for (double invalidValue : new double[]{
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new TemperatureSnapshot(
                            1L,
                            invalidValue,
                            25.0,
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
    void rejectsNonFiniteAmbientTemperature() {
        for (double invalidValue : new double[]{
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new TemperatureSnapshot(
                            1L,
                            0.0,
                            invalidValue,
                            List.of()
                    )
            );

            assertEquals(
                    "ambientTemperatureCelsius must be finite.",
                    exception.getMessage()
            );
        }
    }
}

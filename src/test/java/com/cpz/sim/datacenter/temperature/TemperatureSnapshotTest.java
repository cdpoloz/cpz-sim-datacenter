package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.snapshot.ServerTemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;
import org.junit.jupiter.api.Test;

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
}

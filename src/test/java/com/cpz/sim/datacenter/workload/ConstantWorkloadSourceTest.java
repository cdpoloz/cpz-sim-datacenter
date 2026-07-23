package com.cpz.sim.datacenter.workload;

import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author CPZ
 */
class ConstantWorkloadSourceTest {

    private static Server createServer() {
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                300.0f
        );
        return new Server(
                new ServerLocation("A01", new RackCode("RACK-A01-R01"), "U01"),
                config,
                HardwareStatus.OK
        );
    }

    @Test
    void shouldReturnConfiguredUtilization() {
        ConstantWorkloadSource source = new ConstantWorkloadSource(0.65f);
        Server server = createServer();
        SimulationTick tick = new SimulationTick(1, Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertEquals(0.65f, source.getUtilization(server, tick));
        assertEquals(0.65f, source.getUtilization());
    }

    @Test
    void shouldAcceptBoundaryValues() {
        ConstantWorkloadSource zero = new ConstantWorkloadSource(0.0f);
        ConstantWorkloadSource full = new ConstantWorkloadSource(1.0f);
        assertEquals(0.0f, zero.getUtilization());
        assertEquals(1.0f, full.getUtilization());
    }

    @Test
    void shouldRejectInvalidUtilization() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class, () -> new ConstantWorkloadSource(-0.01f)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class, () -> new ConstantWorkloadSource(1.01f)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class, () -> new ConstantWorkloadSource(Float.NaN)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class, () -> new ConstantWorkloadSource(Float.POSITIVE_INFINITY)
                )
        );
    }

}

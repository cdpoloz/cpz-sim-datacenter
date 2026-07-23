package com.cpz.sim.datacenter.workload;

import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
/**
 * @author CPZ
 */
class ScaledWorkloadSourceTest {

    private static Server createServer() {
        ServerLocation location = new ServerLocation("A01", new RackCode("RACK-A01-R01"), "U01");
        ServerConfig config = new ServerConfig(
                "SRV-DEMO-001",
                "CPZ",
                "Demo Server",
                100.0f,
                300.0f
        );
        return new Server(location, config, HardwareStatus.OK);
    }

    private static SimulationTick createTick() {
        return new SimulationTick(1L, Duration.ofMinutes(30), Duration.ofMinutes(30));
    }

    @Test
    void shouldScaleBaseUtilization() {
        Server server = createServer();
        WorkloadSource baseSource = (ignoredServer, ignoredTick) -> 0.5f;
        ServerWorkloadFactorProvider factorProvider = ignoredServer -> 1.5f;
        ScaledWorkloadSource source = new ScaledWorkloadSource(baseSource, factorProvider);
        float utilization = source.getUtilization(server, createTick());
        assertEquals(0.75f, utilization);
    }

    @Test
    void shouldClampScaledUtilizationToOne() {
        Server server = createServer();
        WorkloadSource baseSource = (ignoredServer, ignoredTick) -> 0.8f;
        ServerWorkloadFactorProvider factorProvider = ignoredServer -> 2.0f;
        ScaledWorkloadSource source = new ScaledWorkloadSource(baseSource, factorProvider);
        float utilization = source.getUtilization(server, createTick());
        assertEquals(1.0f, utilization);
    }

    @Test
    void shouldAllowLowerWorkloadFactor() {
        Server server = createServer();
        WorkloadSource baseSource = (ignoredServer, ignoredTick) -> 0.8f;
        ServerWorkloadFactorProvider factorProvider = ignoredServer -> 0.5f;
        ScaledWorkloadSource source = new ScaledWorkloadSource(baseSource, factorProvider);
        float utilization = source.getUtilization(server, createTick());
        assertEquals(0.4f, utilization);
    }

}

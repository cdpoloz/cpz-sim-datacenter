package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class MediumJsonDatacenterIntegrationTest {

    private static Datacenter loadMediumDatacenterFromJson() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterDefinition definition = loader.load(resourcePath("datacenter/medium-datacenter.json"));
        return new DatacenterFactory().create(definition);
    }

    private static Path resourcePath(String resourceName) {
        try {
            return Path.of(
                    Objects.requireNonNull(
                            MediumJsonDatacenterIntegrationTest.class
                                    .getClassLoader()
                                    .getResource(resourceName)
                    ).toURI()
            );
        } catch (URISyntaxException exception) {
            throw new AssertionError("Invalid test resource path: " + resourceName, exception);
        }
    }

    @Test
    void shouldLoadMediumDatacenterFromJson() {
        Datacenter datacenter = loadMediumDatacenterFromJson();
        assertEquals(8, datacenter.getServerCount());
    }

    @Test
    void shouldLoadOfflineServerFromJson() {
        Datacenter datacenter = loadMediumDatacenterFromJson();
        boolean hasOfflineServer = datacenter.getServers().stream()
                .anyMatch(server -> server.getStatus() == HardwareStatus.OFFLINE);
        assertTrue(hasOfflineServer);
    }

    @Test
    void offlineServerShouldNotConsumePower() {
        Datacenter datacenter = loadMediumDatacenterFromJson();
        SimulationClock clock = new SimulationClock(Duration.ofMinutes(30));
        SimulationEngine engine = new SimulationEngine(clock);
        engine.register(new WorkloadSystem(datacenter, new ConstantWorkloadSource(1.0f)));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.step();
        Server offlineServer = datacenter.getServers().stream()
                .filter(server -> server.getStatus() == HardwareStatus.OFFLINE)
                .findFirst()
                .orElseThrow();
        assertEquals(1.0f, offlineServer.getUtilization());
        assertEquals(0.0f, offlineServer.getCurrentPowerWatts());
    }
}

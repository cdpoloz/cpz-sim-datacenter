package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.WorkloadFactorProviderFactory;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.datacenter.workload.ScaledWorkloadSource;
import com.cpz.sim.datacenter.workload.ServerWorkloadFactorProvider;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class MediumJsonDatacenterIntegrationTest {

    private static DatacenterDefinition loadMediumDefinition() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        return loader.load(resourcePath("datacenter/medium-datacenter.json"));
    }

    private static Datacenter loadMediumDatacenterFromJson() {
        return new DatacenterFactory().create(loadMediumDefinition());
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
        assertEquals(5, datacenter.getRackCount());
        assertEquals(8, datacenter.getServerCount());
        assertTrue(datacenter.getServers().stream().anyMatch(server -> server.getRole() == ServerRole.STORAGE));
        assertTrue(datacenter.getServers().stream().anyMatch(
                server -> server.getRole() == ServerRole.GENERAL_PURPOSE
        ));
    }

    @Test
    void shouldKeepEmptyRacksFromJsonLayout() {
        Datacenter datacenter = loadMediumDatacenterFromJson();
        RackCode emptyRackCode = new RackCode("RACK-A03-R01");
        boolean hasEmptyRack = datacenter.getRacks().stream()
                .anyMatch(rack -> rack.getCode().equals(emptyRackCode));
        boolean hasInstalledServer = datacenter.getServers().stream()
                .anyMatch(server -> server.getLocation().rackCode().equals(emptyRackCode));
        assertTrue(hasEmptyRack);
        assertEquals(0.0f, datacenter.getItPowerWattsByRackCode(emptyRackCode));
        assertFalse(hasInstalledServer);
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
        assertEquals(0.0, offlineServer.getUtilization());
        assertEquals(0.0f, offlineServer.getCurrentPowerWatts());
    }

    @Test
    void scaledWorkloadSourceShouldUseFactorsFromJson() {
        DatacenterDefinition definition = loadMediumDefinition();
        Datacenter datacenter = new DatacenterFactory().create(definition);
        ServerWorkloadFactorProvider factorProvider = new WorkloadFactorProviderFactory().create(definition);
        WorkloadSource workloadSource = new ScaledWorkloadSource(new ConstantWorkloadSource(0.5f), factorProvider);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
        engine.register(new WorkloadSystem(datacenter, workloadSource));
        engine.step();
        Server firstServer = datacenter.getServers().stream()
                .filter(server -> server.getCode().equals("A01-RACK-A01-R01-U01"))
                .findFirst()
                .orElseThrow();
        assertEquals(0.75, firstServer.getUtilization());
    }
}

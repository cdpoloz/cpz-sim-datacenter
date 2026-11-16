package com.cpz.sim.datacenter.health;

import com.cpz.sim.datacenter.config.DatacenterConfigException;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.snapshot.HealthSnapshotProvider;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.*;
import com.cpz.sim.foundation.time.SimulationTick;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerAlertPolicyTest {
    @TempDir Path dir;
    private final JsonMapper mapper = JsonMapper.builder().build();

    private ObjectNode base() throws Exception {
        return (ObjectNode)mapper.readTree(getClass().getResourceAsStream("/datacenter/valid-datacenter.json"));
    }
    private Path save(ObjectNode root) throws Exception {
        Path file = dir.resolve("policy.json");
        mapper.writeValue(file.toFile(), root);
        return file;
    }

    @Test void policySelectsReasonsWithoutMutatingDiagnostics() {
        var diagnostic = EnumSet.of(ServerAlertReason.HIGH_UTILIZATION, ServerAlertReason.HIGH_TEMPERATURE);
        var policy = new ServerAlertPolicy(false);
        var visible = policy.visibleReasons(diagnostic);
        assertEquals(Set.of(ServerAlertReason.HIGH_TEMPERATURE), visible);
        assertEquals(2, diagnostic.size());
        assertThrows(UnsupportedOperationException.class, () -> visible.clear());
        assertTrue(policy.visibleReasons(Set.of(ServerAlertReason.HIGH_UTILIZATION)).isEmpty());
        assertEquals(diagnostic, ServerAlertPolicy.defaults().visibleReasons(diagnostic));
    }

    @Test void omittedPolicyKeepsLegacyVisibility() throws Exception {
        var definition = new JsonDatacenterConfigLoader().load(save(base()));
        assertTrue(definition.alertPolicy().highUtilizationEnabled());
        var copy = new com.cpz.sim.datacenter.config.definition.DatacenterDefinition(
                definition.name(), definition.layout(), definition.serverModels(), definition.servers(),
                definition.temperature(), definition.health(), definition.cooling(), definition.dataInputMode(),
                definition.powerInputGranularity(), definition.temperatureInputGranularity(),
                definition.roomAirTemperature(), definition.rackCooling());
        assertTrue(copy.alertPolicy().highUtilizationEnabled());
    }

    @Test void booleansAreLoadedFromJson() throws Exception {
        for (boolean enabled : new boolean[] {false, true}) {
            var root = base();
            root.putObject("alertPolicy").put("highUtilizationEnabled", enabled);
            assertEquals(enabled, new JsonDatacenterConfigLoader().load(save(root)).alertPolicy().highUtilizationEnabled());
        }
    }

    @Test void malformedOrIncompletePolicyIsRejected() throws Exception {
        for (String block : List.of("null", "[]", "{}", "{\"highUtilizationEnabled\":null}",
                "{\"highUtilizationEnabled\":\"false\"}", "{\"highUtilizationEnabled\":0}",
                "{\"highUtilizationEnabled\":false,\"typo\":true}")) {
            var root = base(); root.set("alertPolicy", mapper.readTree(block));
            assertThrows(DatacenterConfigException.class, () -> new JsonDatacenterConfigLoader().load(save(root)), block);
        }
    }

    @Test void disabledOperatorReasonStillExistsInRawHealthAndMetrics() throws Exception {
        var root = base(); root.putObject("alertPolicy").put("highUtilizationEnabled", false);
        var definition = new JsonDatacenterConfigLoader().load(save(root));
        var dc = new DatacenterFactory().create(definition);
        var temperatures = new TemperatureSystem(dc, new TemperatureSystemOptions(24, 24, 5000, 8),
                new SimpleServerTemperatureModel());
        var health = new ServerHealthSystem(dc, temperatures, ServerHealthOptions.defaults());
        var tick = new SimulationTick(1, Duration.ofMinutes(1), Duration.ofMinutes(1));
        dc.getServers().forEach(server -> server.setUtilization(1));
        new PowerConsumptionSystem(dc).update(tick);
        health.update(tick);
        var snapshot = new HealthSnapshotProvider(dc, health, temperatures).snapshot(tick);
        snapshot.servers().forEach(server -> {
            assertEquals(HardwareStatus.ALERT, server.status());
            assertTrue(server.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION));
            assertTrue(definition.alertPolicy().visibleReasons(server.alertReasons()).isEmpty());
            assertEquals(1, server.utilization());
        });
        dc.getServers().forEach(server -> assertEquals(server.getConfig().maxPowerWatts(), server.getCurrentPowerWatts()));
    }
}

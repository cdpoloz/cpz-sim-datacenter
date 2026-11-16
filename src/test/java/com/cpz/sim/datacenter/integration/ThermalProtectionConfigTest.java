package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.config.DatacenterConfigException;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.model.ThermalRestartPolicy;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ThermalProtectionConfigTest {
    @TempDir Path dir;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private ObjectNode root() throws Exception {
        return (ObjectNode)mapper.readTree(Path.of("data/config/thermal-protection-demo.json").toFile());
    }
    private Path save(ObjectNode root) throws Exception {
        Path file = dir.resolve("equipment.json"); mapper.writeValue(file.toFile(), root); return file;
    }

    @Test void allManufacturerPropertiesReachInstalledEquipmentThroughJson() throws Exception {
        var definition = new JsonDatacenterConfigLoader().load(save(root()));
        var p = new DatacenterFactory().create(definition).getServers().iterator().next().getConfig().thermalProtection();
        assertTrue(p.enabled());
        assertEquals(90, p.throttleTemperatureCelsius()); assertEquals(85, p.throttleRecoveryTemperatureCelsius());
        assertEquals(.4, p.throttledMaxUtilization()); assertEquals(105, p.shutdownTemperatureCelsius());
        assertEquals(70, p.restartTemperatureCelsius()); assertEquals(120, p.minimumShutdownSeconds());
        assertEquals(5, p.shutdownPowerWatts()); assertEquals(ThermalRestartPolicy.MANUAL, p.restartPolicy());
    }

    @Test void differentModelsRetainIndependentManufacturerProfiles() throws Exception {
        var root = root();
        var models = (com.fasterxml.jackson.databind.node.ArrayNode)root.path("serverModels");
        var second = ((ObjectNode)models.get(0)).deepCopy();
        second.put("modelCode", "SECOND");
        ((ObjectNode)second.path("thermalProtection")).put("throttleTemperatureCelsius", 95).put("restartPolicy", "AUTOMATIC");
        models.add(second);
        ((ObjectNode)root.path("servers").get(1)).put("modelCode", "SECOND");
        var servers = new DatacenterFactory().create(new JsonDatacenterConfigLoader().load(save(root))).getServers();
        assertEquals(1, servers.stream().filter(s -> s.getConfig().thermalProtection().restartPolicy() == ThermalRestartPolicy.MANUAL).count());
        assertEquals(1, servers.stream().filter(s -> s.getConfig().thermalProtection().throttleTemperatureCelsius() == 95).count());
    }

    @Test void everyProfileFieldIsRequiredAndExplicitNullOrWrongTypesAreRejected() throws Exception {
        for (String field : List.of("enabled", "throttleTemperatureCelsius", "throttleRecoveryTemperatureCelsius",
                "throttledMaxUtilization", "shutdownTemperatureCelsius", "restartTemperatureCelsius",
                "minimumShutdownSeconds", "restartPolicy", "shutdownPowerWatts")) {
            for (String invalid : List.of("missing", "null", "wrongType")) {
                var root = root(); var profile = (ObjectNode)root.path("serverModels").get(0).path("thermalProtection");
                if (invalid.equals("missing")) profile.remove(field);
                else if (invalid.equals("null")) profile.putNull(field);
                else if (field.equals("restartPolicy")) profile.put(field, 1);
                else profile.put(field, "true");
                assertThrows(DatacenterConfigException.class,
                        () -> new JsonDatacenterConfigLoader().load(save(root)), field + ": " + invalid);
            }
        }
    }

    @Test void unknownFieldsInvalidRangesAndUnsafeThresholdOrderingAreRejected() throws Exception {
        for (String change : List.of(
                "{\"throttleTemperatureCelsius\":85}", "{\"shutdownTemperatureCelsius\":89}",
                "{\"restartTemperatureCelsius\":86}", "{\"minimumShutdownSeconds\":-1}",
                "{\"throttledMaxUtilization\":1}", "{\"throttledMaxUtilization\":-0.1}",
                "{\"shutdownPowerWatts\":-1}", "{\"restartPolicy\":\"SOMETIMES\"}", "{\"typo\":1}")) {
            var root = root(); var profile = (ObjectNode)root.path("serverModels").get(0).path("thermalProtection");
            profile.setAll((ObjectNode)mapper.readTree(change));
            assertThrows(DatacenterConfigException.class, () -> new JsonDatacenterConfigLoader().load(save(root)), change);
        }
        var root = root();
        ((ObjectNode)root.path("serverModels").get(0).path("thermalProtection")).put("shutdownPowerWatts", 101);
        var definition = new JsonDatacenterConfigLoader().load(save(root));
        assertThrows(com.cpz.sim.datacenter.config.validation.DatacenterConfigValidationException.class, () -> new DatacenterFactory().create(definition));
    }

    @Test void omittedProfileIsBackwardCompatibleButExplicitNullIsRejected() throws Exception {
        var root = root(); var model = (ObjectNode)root.path("serverModels").get(0);
        model.remove("thermalProtection");
        var definition = new JsonDatacenterConfigLoader().load(save(root));
        assertNull(new DatacenterFactory().create(definition).getServers().iterator().next().getConfig().thermalProtection());
        model.putNull("thermalProtection");
        assertThrows(DatacenterConfigException.class, () -> new JsonDatacenterConfigLoader().load(save(root)));
    }
}

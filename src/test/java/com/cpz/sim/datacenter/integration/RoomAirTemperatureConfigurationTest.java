package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.config.DatacenterConfigException;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.RoomAirTemperatureOptionsDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidationException;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.factory.RoomAirTemperatureOptionsFactory;
import com.cpz.sim.datacenter.temperature.RoomAirThermalState;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RoomAirTemperatureConfigurationTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String VALID_BLOCK = """
            {"initialTemperatureCelsius":24.0,
             "effectiveThermalCapacityJoulesPerCelsius":2000000.0,
             "environmentTemperatureCelsius":20.0,
             "environmentHeatTransferWattsPerCelsius":0.0}
            """;

    @TempDir
    Path directory;

    private DatacenterDefinition load(String block) throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree("""
                {"name":"Room air test",
                 "layout":{"racks":[{"code":"R01","column":"C01","row":"R01","slots":["S01"]}]},
                 "serverModels":[{"modelCode":"M01","manufacturer":"CPZ","model":"Test",
                                  "idlePowerWatts":100.0,"maxPowerWatts":300.0}],
                 "servers":[{"column":"C01","rackCode":"R01","slot":"S01","modelCode":"M01",
                             "status":"OK","workloadFactor":1.0}]}
                """);
        if (block != null) root.set("roomAirTemperature", MAPPER.readTree(block));
        Path path = directory.resolve("datacenter.json");
        Files.writeString(path, MAPPER.writeValueAsString(root));
        return new JsonDatacenterConfigLoader().load(path);
    }

    @Test
    void legacyJsonAndTenArgumentConstructorLeaveRoomAirUnconfigured() throws Exception {
        var definition = load(null);
        var factory = new RoomAirTemperatureOptionsFactory();
        assertNull(definition.roomAirTemperature());
        assertTrue(factory.create(definition).isEmpty());
        var legacy = new DatacenterDefinition(definition.name(), definition.layout(),
                definition.serverModels(), definition.servers(), definition.temperature(), definition.health(),
                definition.cooling(), definition.dataInputMode(), definition.powerInputGranularity(),
                definition.temperatureInputGranularity());
        assertEquals(definition, legacy);
    }

    @Test
    void loadsValidConfigurationAndInitializesIndependentRoomState() throws Exception {
        var definition = load(VALID_BLOCK);
        var options = new RoomAirTemperatureOptionsFactory().create(definition).orElseThrow();
        assertEquals(24.0, options.initialTemperatureCelsius());
        assertEquals(2_000_000.0, options.effectiveThermalCapacityJoulesPerCelsius());
        assertEquals(20.0, options.environmentTemperatureCelsius());
        assertEquals(0.0, options.environmentHeatTransferWattsPerCelsius());
        assertEquals(24.0, new RoomAirThermalState(options).getTemperatureCelsius());
        assertNull(definition.temperature());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "true", "24", "\"room\"", "{}"})
    void rejectsInvalidBlocks(String block) {
        assertThrows(DatacenterConfigException.class, () -> load(block));
    }

    @ParameterizedTest
    @ValueSource(strings = {"initialTemperatureCelsius", "effectiveThermalCapacityJoulesPerCelsius",
            "environmentTemperatureCelsius", "environmentHeatTransferWattsPerCelsius"})
    void rejectsMissingNullAndCoercedFields(String field) throws Exception {
        ObjectNode block = (ObjectNode) MAPPER.readTree(VALID_BLOCK);
        block.remove(field);
        assertThrows(DatacenterConfigException.class, () -> load(block.toString()));
        block.putNull(field);
        assertThrows(DatacenterConfigException.class, () -> load(block.toString()));
        block.put(field, "24");
        assertThrows(DatacenterConfigException.class, () -> load(block.toString()));
        block.put(field, true);
        assertThrows(DatacenterConfigException.class, () -> load(block.toString()));
        block.set(field, MAPPER.readTree("[]"));
        assertThrows(DatacenterConfigException.class, () -> load(block.toString()));
        block.set(field, MAPPER.readTree("1e999"));
        assertThrows(DatacenterConfigException.class, () -> load(block.toString()));
    }

    @Test
    void rejectsUnknownFields() throws Exception {
        ObjectNode block = (ObjectNode) MAPPER.readTree(VALID_BLOCK);
        block.put("initialTemperaturCelsius", 24.0);
        assertThrows(DatacenterConfigException.class, () -> load(block.toString()));
    }

    @Test
    void rejectsNonPhysicalCapacityAndNegativeHeatTransfer() throws Exception {
        var validator = new DatacenterConfigValidator();
        for (String field : new String[]{"effectiveThermalCapacityJoulesPerCelsius",
                "environmentHeatTransferWattsPerCelsius"}) {
            ObjectNode block = (ObjectNode) MAPPER.readTree(VALID_BLOCK);
            block.put(field, -1.0);
            var definition = load(block.toString());
            assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
            assertThrows(DatacenterConfigValidationException.class,
                    () -> new RoomAirTemperatureOptionsFactory().create(definition));
        }
        ObjectNode block = (ObjectNode) MAPPER.readTree(VALID_BLOCK);
        block.put("effectiveThermalCapacityJoulesPerCelsius", 0.0);
        var zeroCapacity = load(block.toString());
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(zeroCapacity));
    }

    @Test
    void validatorRejectsNonFiniteProgrammaticConfiguration() throws Exception {
        var base = load(null);
        var definition = new DatacenterDefinition(base.name(), base.layout(), base.serverModels(),
                base.servers(), base.temperature(), base.health(), base.cooling(), base.dataInputMode(),
                base.powerInputGranularity(), base.temperatureInputGranularity(),
                new RoomAirTemperatureOptionsDefinition(Double.NaN, Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY, Double.NaN));
        var exception = assertThrows(DatacenterConfigValidationException.class,
                () -> new DatacenterConfigValidator().validate(definition));
        for (String field : new String[]{"initialTemperatureCelsius", "effectiveThermalCapacityJoulesPerCelsius",
                "environmentTemperatureCelsius", "environmentHeatTransferWattsPerCelsius"})
            assertTrue(exception.getMessage().contains(field));
    }

    @Test
    void factoryRejectsNullDatacenterAndDoesNotInventDefaults() {
        var factory = new RoomAirTemperatureOptionsFactory();
        assertThrows(NullPointerException.class, () -> factory.create((DatacenterDefinition) null));
        assertTrue(factory.create((RoomAirTemperatureOptionsDefinition) null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                new RoomAirTemperatureOptionsDefinition(24.0, 0.0, 20.0, 0.0)));
    }
}

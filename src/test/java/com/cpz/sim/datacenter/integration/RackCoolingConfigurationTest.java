package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.config.DatacenterConfigException;
import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidationException;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.cooling.HeatRejectionDestination;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.CoupledRoomTemperatureSystemFactory;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import java.util.Optional;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RackCoolingConfigurationTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    @TempDir Path directory;

    private ObjectNode root() throws Exception {
        return (ObjectNode) MAPPER.readTree("""
                {"name":"Rack cooling test",
                 "layout":{"racks":[
                   {"code":"R01","column":"C01","row":"R01","slotCount":2},
                   {"code":"R01","column":"C02","row":"R01","slotCount":2}]},
                 "serverModels":[],"servers":[],
                 "roomAirTemperature":{"initialTemperatureCelsius":24,
                   "effectiveThermalCapacityJoulesPerCelsius":2000000,
                   "environmentTemperatureCelsius":20,"environmentHeatTransferWattsPerCelsius":0},
                 "rackCooling":{"airDensityKilogramsPerCubicMeter":1.204,
                   "airSpecificHeatJoulesPerKilogramKelvin":1005,
                   "units":[{"code":"RC01","column":"C01","rackCode":"R01",
                     "initialAirTemperatureCelsius":24,
                     "effectiveAirThermalCapacityJoulesPerCelsius":10000,
                     "roomHeatTransferWattsPerCelsius":0,
                     "ratedAirflowCubicMetersPerSecond":0.1,
                     "ratedCoolingCapacityWatts":2000,"supplyAirTemperatureCelsius":18,
                     "ratedElectricalPowerWatts":500,
                     "heatRejectionDestination":"ROOM","initiallyEnabled":true}]}}
                """);
    }

    private ObjectNode unit(ObjectNode root) {
        return (ObjectNode) root.path("rackCooling").path("units").get(0);
    }

    private DatacenterDefinition load(ObjectNode root) throws Exception {
        Path path = directory.resolve("datacenter.json");
        Files.writeString(path, root.toString());
        return new JsonDatacenterConfigLoader().load(path);
    }

    private void invalid(ObjectNode root, String message) throws Exception {
        var definition = load(root);
        var error = assertThrows(DatacenterConfigValidationException.class,
                () -> new DatacenterConfigValidator().validate(definition));
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOM", "EXTERNAL"})
    void acceptsBothDestinationsAndEmptyRacksWithoutLegacyCooling(String destination) throws Exception {
        var root = root();
        unit(root).put("heatRejectionDestination", destination);
        var definition = load(root);
        assertDoesNotThrow(() -> new DatacenterConfigValidator().validate(definition));
        assertNull(definition.cooling());
        assertEquals(HeatRejectionDestination.valueOf(destination),
                definition.rackCooling().units().getFirst().heatRejectionDestination());
        assertEquals(0, definition.rackCooling().units().getFirst().roomHeatTransferWattsPerCelsius());
        assertDoesNotThrow(() -> new DatacenterFactory().create(definition));
    }

    @Test
    void directCoupledFactoryInstallsConfiguredRackCooling() throws Exception {
        var configured = load(root());
        var legacyRoot = root();
        legacyRoot.remove("rackCooling");
        var datacenter = new DatacenterFactory().create(load(legacyRoot));
        var temperatures = new TemperatureSystem(datacenter, TemperatureSystemOptions.defaults(),
                new SimpleServerTemperatureModel());
        var coupled = new CoupledRoomTemperatureSystemFactory().create(configured, datacenter,
                temperatures, Optional.empty()).orElseThrow();
        assertTrue(coupled.rackCoolingSystem().isPresent());
        assertEquals(24, coupled.rackAirTemperaturesCelsius().values().iterator().next());
    }

    @Test
    void preservesExistingConstructorAndJsonBehavior() throws Exception {
        var root = root();
        root.remove("rackCooling");
        var definition = load(root);
        assertNull(definition.rackCooling());
        var legacy = new DatacenterDefinition(definition.name(), definition.layout(), definition.serverModels(),
                definition.servers(), definition.temperature(), definition.health(), definition.cooling(),
                definition.dataInputMode(), definition.powerInputGranularity(),
                definition.temperatureInputGranularity(), definition.roomAirTemperature());
        assertEquals(definition, legacy);
        assertDoesNotThrow(() -> new DatacenterFactory().create(definition));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "true", "1", "{}"})
    void rejectsInvalidBlocks(String value) throws Exception {
        var root = root();
        root.set("rackCooling", MAPPER.readTree(value));
        assertThrows(DatacenterConfigException.class, () -> load(root));
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "column", "rackCode", "initialAirTemperatureCelsius",
            "effectiveAirThermalCapacityJoulesPerCelsius", "roomHeatTransferWattsPerCelsius",
            "ratedAirflowCubicMetersPerSecond", "ratedCoolingCapacityWatts",
            "supplyAirTemperatureCelsius", "ratedElectricalPowerWatts",
            "heatRejectionDestination", "initiallyEnabled"})
    void rejectsMissingNullAndWrongTypes(String field) throws Exception {
        var root = root();
        unit(root).remove(field);
        assertThrows(DatacenterConfigException.class, () -> load(root));
        unit(root).putNull(field);
        assertThrows(DatacenterConfigException.class, () -> load(root));
        unit(root).set(field, MAPPER.readTree("[]"));
        assertThrows(DatacenterConfigException.class, () -> load(root));
    }

    @ParameterizedTest
    @ValueSource(strings = {"initialAirTemperatureCelsius", "effectiveAirThermalCapacityJoulesPerCelsius",
            "roomHeatTransferWattsPerCelsius", "ratedAirflowCubicMetersPerSecond",
            "ratedCoolingCapacityWatts", "supplyAirTemperatureCelsius", "ratedElectricalPowerWatts"})
    void rejectsCoercionAndOverflow(String field) throws Exception {
        var root = root();
        unit(root).put(field, "24");
        assertThrows(DatacenterConfigException.class, () -> load(root));
        unit(root).put(field, true);
        assertThrows(DatacenterConfigException.class, () -> load(root));
        unit(root).set(field, MAPPER.readTree("1e999"));
        assertThrows(DatacenterConfigException.class, () -> load(root));
    }

    @Test
    void rejectsMalformedCollectionsUnknownFieldsAndDestination() throws Exception {
        for (String value : List.of("null", "{}", "true", "[null]", "[42]")) {
            var root = root();
            ((ObjectNode) root.get("rackCooling")).set("units", MAPPER.readTree(value));
            assertThrows(DatacenterConfigException.class, () -> load(root));
        }
        var root = root();
        unit(root).put("heatRejectionDestination", "OUTSIDE");
        final var destinationRoot = root;
        assertThrows(DatacenterConfigException.class, () -> load(destinationRoot));
        unit(root).put("heatRejectionDestination", 0);
        assertThrows(DatacenterConfigException.class, () -> load(destinationRoot));
        root = root();
        unit(root).put("initiallyEnabled", "true");
        final var booleanRoot = root;
        assertThrows(DatacenterConfigException.class, () -> load(booleanRoot));
        for (boolean nested : List.of(false, true)) {
            var unknownRoot = root();
            (nested ? unit(unknownRoot) : (ObjectNode) unknownRoot.get("rackCooling")).put("typo", 1);
            assertThrows(DatacenterConfigException.class, () -> load(unknownRoot));
        }
    }

    @Test
    void rejectsMissingNullAndCoercedAirConstants() throws Exception {
        for (String field : List.of("airDensityKilogramsPerCubicMeter", "airSpecificHeatJoulesPerKilogramKelvin")) {
            for (String value : List.of("null", "true", "\"1.2\"", "1e999")) {
                var root = root();
                ((ObjectNode) root.get("rackCooling")).set(field, MAPPER.readTree(value));
                assertThrows(DatacenterConfigException.class, () -> load(root));
            }
            var root = root();
            ((ObjectNode) root.get("rackCooling")).remove(field);
            assertThrows(DatacenterConfigException.class, () -> load(root));
        }
    }

    @Test
    void rejectsInvalidPhysicalMagnitudes() throws Exception {
        for (String field : List.of("effectiveAirThermalCapacityJoulesPerCelsius",
                "ratedAirflowCubicMetersPerSecond", "ratedCoolingCapacityWatts", "ratedElectricalPowerWatts")) {
            for (double value : new double[]{0, -1}) {
                var root = root();
                unit(root).put(field, value);
                invalid(root, field);
            }
        }
        var root = root();
        unit(root).put("roomHeatTransferWattsPerCelsius", -1);
        invalid(root, "roomHeatTransferWattsPerCelsius");
        for (String field : List.of("airDensityKilogramsPerCubicMeter", "airSpecificHeatJoulesPerKilogramKelvin")) {
            root = root();
            ((ObjectNode) root.get("rackCooling")).put(field, 0);
            invalid(root, field);
        }
    }

    @Test
    void validatesExactRackIdentityAndUniqueness() throws Exception {
        var root = root();
        var second = unit(root).deepCopy().put("code", "RC02").put("column", "C02");
        ((ArrayNode) root.path("rackCooling").get("units")).add(second);
        new DatacenterConfigValidator().validate(load(root));
        second.put("column", "C01");
        invalid(root, "Multiple rack cooling units");
        second.put("column", "C02").put("code", "RC01");
        invalid(root, "Duplicated rack cooling unit code");
        root = root();
        unit(root).put("column", "C99");
        invalid(root, "unknown rack location");
        for (String field : List.of("code", "column", "rackCode")) {
            root = root();
            unit(root).put(field, " ");
            invalid(root, field);
        }
    }

    @Test
    void rejectsCrossTypeCodeCollisions() throws Exception {
        for (String type : List.of("supplyUnits", "exhaustUnits")) {
            var root = root();
            ObjectNode cooling = root.putObject("cooling");
            cooling.putArray("zones");
            cooling.putArray("supplyUnits");
            cooling.putArray("exhaustUnits");
            cooling.set("options", MAPPER.readTree("""
                    {"airDensityKilogramsPerCubicMeter":1.204,"airSpecificHeatJoulesPerKilogramKelvin":1005,
                     "initialInletAirTemperatureCelsius":24,"maximumRecirculationFraction":0.95,
                     "residualRecirculationFraction":0.1,"effectiveZoneAirVolumeCubicMeters":1000,
                     "recirculationResponseTimeSeconds":300}
                    """));
            cooling.putArray(type).addObject().put("code", "RC01")
                    .put("ratedAirflowCubicMetersPerSecond", 1).put("ratedElectricalPowerWatts", 1)
                    .put("initiallyEnabled", false).putArray("influences");
            if (type.equals("supplyUnits")) {
                ((ObjectNode) cooling.path(type).get(0)).put("ratedCoolingCapacityWatts", 1)
                        .put("supplyAirTemperatureCelsius", 18);
            }
            // Supply/exhaust records require all fields, but semantic errors
            // elsewhere in the room cooling block must not hide this collision.
            invalid(root, "Duplicated cooling unit code across rack and room");
        }
    }

    @Test
    void requiresCoupledPowerDerivedModeEvenForDisabledUnits() throws Exception {
        var root = root();
        unit(root).put("initiallyEnabled", false);
        root.remove("roomAirTemperature");
        invalid(root, "requires roomAirTemperature");
        root = root();
        root.put("dataInputMode", "TEMPERATURE_DRIVEN");
        invalid(root, "TEMPERATURE_DRIVEN");
        root.put("dataInputMode", "POWER_DRIVEN");
        new DatacenterConfigValidator().validate(load(root));
        ((ArrayNode) root.path("rackCooling").get("units")).removeAll();
        invalid(root, "at least one unit");
    }

    @Test
    void validatesProgrammaticNullsAndNonFiniteNumbers() throws Exception {
        var base = load(root());
        var unit = new RackCoolingUnitConfigDefinition("RC01", "C01", "R01", Double.NaN,
                Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, Double.NaN,
                Double.NEGATIVE_INFINITY, Double.NaN, null, false);
        var rackCooling = new RackCoolingConfigDefinition(Double.NaN, Double.NaN, Arrays.asList(unit, null));
        var definition = new DatacenterDefinition(base.name(), base.layout(), base.serverModels(), base.servers(),
                base.temperature(), base.health(), base.cooling(), base.dataInputMode(),
                base.powerInputGranularity(), base.temperatureInputGranularity(), base.roomAirTemperature(), rackCooling);
        var error = assertThrows(DatacenterConfigValidationException.class,
                () -> new DatacenterConfigValidator().validate(definition));
        for (String field : List.of("airDensity", "airSpecificHeat", "initialAirTemperature",
                "effectiveAirThermalCapacity", "roomHeatTransfer", "ratedAirflow", "ratedCoolingCapacity",
                "supplyAirTemperature", "ratedElectricalPower", "heatRejectionDestination", "units[1]"))
            assertTrue(error.getMessage().contains(field), field);
    }

    @Test
    void existingRepositoryConfigurationsStillValidate() throws Exception {
        try (var paths = Files.list(Path.of("data/config"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var definition = new JsonDatacenterConfigLoader().load(path);
                assertNull(definition.rackCooling(), path.toString());
                assertDoesNotThrow(() -> new DatacenterConfigValidator().validate(definition), path.toString());
            }
        }
    }
}

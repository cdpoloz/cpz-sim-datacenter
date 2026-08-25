package com.cpz.sim.datacenter.config.json;

import com.cpz.sim.datacenter.config.DatacenterConfigException;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidationException;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.model.ServerThermalProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.cpz.sim.datacenter.cooling.CoolingTickInput;
import com.cpz.sim.datacenter.cooling.ServerHeatLoad;
import com.cpz.sim.datacenter.cooling.ServerHeatLoadProvider;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.system.CoolingSystem;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.ExhaustCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
import com.cpz.sim.datacenter.factory.CoolingConfigurationFactory;
import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class JsonDatacenterConfigLoaderTest {

    @TempDir
    Path tempDirectory;

    private static Path resourcePath(String resourceName) {
        try {
            return Path.of(
                    Objects.requireNonNull(
                            JsonDatacenterConfigLoaderTest.class
                                    .getClassLoader()
                                    .getResource(resourceName)
                    ).toURI()
            );
        } catch (URISyntaxException exception) {
            throw new AssertionError("Invalid test resource path: " + resourceName, exception);
        }
    }

    private Path writeSingleServerConfig(String roleProperty) throws IOException {
        return writeSingleServerConfig("", roleProperty);
    }

    private Path writeSingleServerConfig(String thermalProperties, String roleProperty) throws IOException {
        String json = """
                {
                  "name": "Role Test Datacenter",
                  "layout": {
                    "racks": [
                      {
                        "code": "R01",
                        "column": "C01",
                        "row": "R01",
                        "slots": ["S01"]
                      }
                    ]
                  },
                  "serverModels": [
                    {
                      "modelCode": "MODEL-01",
                      "manufacturer": "CPZ",
                      "model": "Role Test Server",
                      "idlePowerWatts": 100.0,
                      "maxPowerWatts": 300.0%s
                    }
                  ],
                  "servers": [
                    {
                      "column": "C01",
                      "rackCode": "R01",
                      "slot": "S01",
                      "modelCode": "MODEL-01",
                      "status": "OK"%s,
                      "workloadFactor": 1.0
                    }
                  ]
                }
                """.formatted(thermalProperties, roleProperty);
        return Files.writeString(tempDirectory.resolve("server-role.json"), json);
    }

    private static String exceptionMessages(Throwable exception) {
        StringBuilder messages = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null) messages.append(current.getMessage()).append(System.lineSeparator());
            current = current.getCause();
        }
        return messages.toString();
    }

    @Test
    void shouldLoadDatacenterDefinitionFromJsonFile() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterDefinition definition = loader.load(resourcePath("datacenter/valid-datacenter.json"));
        assertEquals("Demo Datacenter", definition.name());
        assertEquals(2, definition.layout().racks().size());
        assertEquals("RACK-A01-R01", definition.layout().racks().getFirst().code());
        assertEquals("A01", definition.layout().racks().getFirst().column());
        assertEquals("R01", definition.layout().racks().getFirst().row());
        assertEquals(42, definition.layout().racks().getFirst().slotCount());
        assertEquals(true, definition.layout().racks().getFirst().hasSlotCount());
        assertEquals(false, definition.layout().racks().getFirst().hasSlots());
        assertEquals(1, definition.serverModels().size());
        assertEquals(2, definition.servers().size());
        assertEquals("SRV-DEMO-001", definition.serverModels().getFirst().modelCode());
        assertNull(definition.serverModels().getFirst().thermalCapacityJoulesPerCelsius());
        assertNull(definition.serverModels().getFirst().heatDissipationWattsPerCelsius());
        assertEquals(null, definition.servers().getFirst().column());
        assertEquals("RACK-A01-R01", definition.servers().getFirst().rackCode());
        assertEquals("U01", definition.servers().getFirst().slot());
        assertEquals(1.5f, definition.servers().getFirst().workloadFactor());
        assertNull(definition.servers().getFirst().role());
        assertNull(definition.temperature());
        assertNull(definition.health());
        assertNull(definition.cooling());
    }

    @Test
    void shouldLoadAndBuildModelSpecificThermalProperties() throws IOException {
        DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(
                writeSingleServerConfig(
                        """
                                ,
                                      "thermalCapacityJoulesPerCelsius": 7500.0,
                                      "heatDissipationWattsPerCelsius": 12.5""",
                        ""
                )
        );

        assertEquals(7500.0, definition.serverModels().getFirst().thermalCapacityJoulesPerCelsius());
        assertEquals(12.5, definition.serverModels().getFirst().heatDissipationWattsPerCelsius());
        ServerThermalProperties thermalProperties = new DatacenterFactory()
                .create(definition)
                .getServers()
                .getFirst()
                .getConfig()
                .thermalProperties();
        assertNotNull(thermalProperties);
        assertEquals(7500.0, thermalProperties.thermalCapacityJoulesPerCelsius());
        assertEquals(12.5, thermalProperties.heatDissipationWattsPerCelsius());
    }

    @Test
    void shouldKeepModelSpecificThermalPropertiesAbsentForLegacyJson() throws IOException {
        DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(
                writeSingleServerConfig("", "")
        );

        assertNull(definition.serverModels().getFirst().thermalCapacityJoulesPerCelsius());
        assertNull(definition.serverModels().getFirst().heatDissipationWattsPerCelsius());
        assertNull(
                new DatacenterFactory()
                        .create(definition)
                        .getServers()
                        .getFirst()
                        .getConfig()
                        .thermalProperties()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ",\n      \"thermalCapacityJoulesPerCelsius\": 5000.0",
            ",\n      \"heatDissipationWattsPerCelsius\": 8.0"
    })
    void shouldRejectIncompleteModelSpecificThermalProperties(String thermalProperties) throws IOException {
        DatacenterConfigException exception = assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(
                        writeSingleServerConfig(thermalProperties, "")
                )
        );

        String messages = exceptionMessages(exception);
        assertTrue(messages.contains("must specify both"));
        assertTrue(messages.contains("thermalCapacityJoulesPerCelsius"));
        assertTrue(messages.contains("heatDissipationWattsPerCelsius"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ",\n      \"thermalCapacityJoulesPerCelsius\": null,\n      \"heatDissipationWattsPerCelsius\": 8.0",
            ",\n      \"thermalCapacityJoulesPerCelsius\": 5000.0,\n      \"heatDissipationWattsPerCelsius\": null"
    })
    void shouldRejectExplicitNullModelSpecificThermalProperties(String thermalProperties) throws IOException {
        DatacenterConfigException exception = assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(
                        writeSingleServerConfig(thermalProperties, "")
                )
        );

        assertTrue(exceptionMessages(exception).contains("cannot be null"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ",\n      \"thermalCapacityJoulesPerCelsius\": \"5000.0\",\n      \"heatDissipationWattsPerCelsius\": 8.0",
            ",\n      \"thermalCapacityJoulesPerCelsius\": 5000.0,\n      \"heatDissipationWattsPerCelsius\": false"
    })
    void shouldRejectNonNumericModelSpecificThermalProperties(String thermalProperties) throws IOException {
        DatacenterConfigException exception = assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(
                        writeSingleServerConfig(thermalProperties, "")
                )
        );

        assertTrue(exceptionMessages(exception).contains("must be a number"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ",\n      \"thermalCapacityJoulesPerCelsius\": 0.0,\n      \"heatDissipationWattsPerCelsius\": 8.0",
            ",\n      \"thermalCapacityJoulesPerCelsius\": -1.0,\n      \"heatDissipationWattsPerCelsius\": 8.0",
            ",\n      \"thermalCapacityJoulesPerCelsius\": 1e999,\n      \"heatDissipationWattsPerCelsius\": 8.0",
            ",\n      \"thermalCapacityJoulesPerCelsius\": 5000.0,\n      \"heatDissipationWattsPerCelsius\": 0.0",
            ",\n      \"thermalCapacityJoulesPerCelsius\": 5000.0,\n      \"heatDissipationWattsPerCelsius\": -1.0",
            ",\n      \"thermalCapacityJoulesPerCelsius\": 5000.0,\n      \"heatDissipationWattsPerCelsius\": 1e999"
    })
    void shouldRejectInvalidModelSpecificThermalValues(String thermalProperties) throws IOException {
        DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(
                writeSingleServerConfig(thermalProperties, "")
        );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> new DatacenterFactory().create(definition)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
    void shouldRejectNonStandardNonFiniteThermalJsonNumbers(String value) {
        assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(
                        writeSingleServerConfig(
                                ",\n      \"thermalCapacityJoulesPerCelsius\": " + value
                                        + ",\n      \"heatDissipationWattsPerCelsius\": 8.0",
                                ""
                        )
                )
        );
    }

    @Test
    void shouldRejectUnknownServerModelField() {
        assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(
                        writeSingleServerConfig(",\n      \"unknownThermalField\": 8.0", "")
                )
        );
    }

    @ParameterizedTest
    @EnumSource(ServerRole.class)
    void shouldLoadEveryServerRole(ServerRole role) throws IOException {
        DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(
                writeSingleServerConfig(",\n      \"role\": \"" + role.name() + "\"")
        );

        assertEquals(role, definition.servers().getFirst().role());
    }

    @Test
    void shouldKeepRoleNullWhenJsonFieldIsAbsent() throws IOException {
        DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(writeSingleServerConfig(""));

        assertNull(definition.servers().getFirst().role());
    }

    @Test
    void shouldRejectUnknownServerRole() throws IOException {
        DatacenterConfigException exception = assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(
                        writeSingleServerConfig(",\n      \"role\": \"WEB_SERVER\"")
                )
        );

        String messages = exceptionMessages(exception);
        assertTrue(messages.contains("role"));
        assertTrue(messages.contains("WEB_SERVER"));
        assertTrue(messages.contains("ServerRole"));
    }

    @Test
    void shouldRejectExplicitNullServerRole() throws IOException {
        DatacenterConfigException exception = assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(
                        writeSingleServerConfig(",\n      \"role\": null")
                )
        );

        String messages = exceptionMessages(exception);
        assertTrue(messages.contains("role"));
        assertTrue(messages.contains("cannot be null"));
    }

    @Test
    void shouldRejectNonTextualServerRole() throws IOException {
        DatacenterConfigException exception = assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(
                        writeSingleServerConfig(",\n      \"role\": 1")
                )
        );

        String messages = exceptionMessages(exception);
        assertTrue(messages.contains("role"));
        assertTrue(messages.contains("must be a string"));
    }

    @Test
    void shouldRejectLowercaseServerRole() throws IOException {
        DatacenterConfigException exception = assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(
                        writeSingleServerConfig(",\n      \"role\": \"ai\"")
                )
        );

        String messages = exceptionMessages(exception);
        assertTrue(messages.contains("role"));
        assertTrue(messages.contains("ai"));
        assertTrue(messages.contains("ServerRole"));
    }

    @Test
    void shouldLoadDatacenterDefinitionWithExplicitServerColumn() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterDefinition definition = loader.load(Path.of("data/config/datacenter-repeated-rack-codes.json"));
        assertEquals("Repeated Rack Codes Datacenter", definition.name());
        assertEquals("C01", definition.servers().getFirst().column());
        assertEquals("C02", definition.servers().get(1).column());
        Datacenter datacenter = new DatacenterFactory().create(definition);
        assertEquals(2, datacenter.getRackCount());
        assertEquals(3, datacenter.getServerCount());
        assertEquals("C01-R01-S01", datacenter.getServers().getFirst().getCode());
        assertEquals("C02-R01-S01", datacenter.getServers().get(1).getCode());
        assertEquals(true, datacenter.getServer("C01", "R01", "S02").isEmpty());
    }

    @Test
    void shouldLoadDatacenterDefinitionWithTemperatureBlock() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterDefinition definition = loader.load(resourcePath("datacenter/datacenter-with-temperature.json"));
        assertEquals("Datacenter With Temperature", definition.name());
        assertEquals(24.0, definition.temperature().ambientTemperatureCelsius());
        assertEquals(30.0, definition.temperature().defaultInitialTemperatureCelsius());
        assertEquals(5000.0, definition.temperature().thermalCapacityJoulesPerCelsius());
        assertEquals(8.0, definition.temperature().heatDissipationWattsPerCelsius());
    }

    @Test
    void shouldLoadDatacenterDefinitionWithExplicitSlots() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterDefinition definition = loader.load(resourcePath("datacenter/explicit-slots-datacenter.json"));
        RackDefinition rack = definition.layout().racks().getFirst();
        assertEquals("RACK-A01-R01", rack.code());
        assertEquals(false, rack.hasSlotCount());
        assertEquals(true, rack.hasSlots());
        assertEquals(List.of("S01", "S02", "GPU-A", "NETWORK", "SPARE"), rack.slots());
        Datacenter datacenter = new DatacenterFactory().create(definition);
        assertEquals(List.of("S01", "S02", "GPU-A", "NETWORK", "SPARE"), datacenter.getRacks().getFirst().getSlotCodes());
        assertEquals("GPU-A", datacenter.getServers().getFirst().getLocation().slot());
    }

    @Test
    void shouldBuildDatacenterFromLoadedJsonDefinition() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterDefinition definition = loader.load(resourcePath("datacenter/valid-datacenter.json"));
        DatacenterFactory factory = new DatacenterFactory();
        Datacenter datacenter = factory.create(definition);
        assertEquals(2, datacenter.getRackCount());
        assertEquals(2, datacenter.getServerCount());
        assertEquals(ServerRole.GENERAL_PURPOSE, datacenter.getServers().getFirst().getRole());
    }

    @Test
    void shouldRejectMissingJsonFile() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        assertThrows(DatacenterConfigException.class, () -> loader.load(Path.of("data/config/missing-datacenter.json")));
    }

    @Test
    void shouldRejectPartialTemperatureBlock() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        assertThrows(
                DatacenterConfigException.class,
                () -> loader.load(resourcePath("datacenter/datacenter-with-partial-temperature.json"))
        );
    }

    @Test
    void shouldRejectUnknownServerField() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        assertThrows(
                DatacenterConfigException.class,
                () -> loader.load(resourcePath("datacenter/server-with-unknown-field.json"))
        );
    }

    @Test
    void shouldLoadExistingDataConfigExamples() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterFactory factory = new DatacenterFactory();
        for (String path : List.of(
                "data/config/demo-datacenter.json",
                "data/config/demo-datacenter-medium.json",
                "data/config/datacenter-with-temperature.json",
                "data/config/datacenter-ui-test.json",
                "data/config/datacenter-repeated-rack-codes.json"
        )) {
            DatacenterDefinition definition = loader.load(Path.of(path));
            Datacenter datacenter = factory.create(definition);
            assertEquals(definition.layout().racks().size(), datacenter.getRackCount());
            assertEquals(definition.servers().size(), datacenter.getServerCount());
        }
    }

    private Path writeConfigWithCooling(String coolingValue) throws IOException {
        String originalJson = Files.readString(
                resourcePath("datacenter/valid-datacenter.json")
        );

        int closingBraceIndex = originalJson.lastIndexOf('}');

        if (closingBraceIndex < 0) {
            throw new AssertionError(
                    "Valid datacenter test resource must contain a root object"
            );
        }

        String jsonWithCooling =
                originalJson.substring(0, closingBraceIndex)
                        + ",\n  \"cooling\": "
                        + coolingValue
                        + "\n"
                        + originalJson.substring(closingBraceIndex);

        return Files.writeString(
                tempDirectory.resolve("datacenter-with-cooling.json"),
                jsonWithCooling
        );
    }

    private Path writeValidCoolingConfig() throws IOException {
        return writeConfigWithCooling("""
        {
          "zones": [
            {
              "code": "ZONE-A01-R01",
              "columns": ["A01"],
              "rackCodes": ["RACK-A01-R01"]
            }
          ],
          "supplyUnits": [
            {
              "code": "SUPPLY-01",
              "ratedAirflowCubicMetersPerSecond": 8.0,
              "ratedCoolingCapacityWatts": 100000.0,
              "supplyAirTemperatureCelsius": 18.0,
              "influences": [
                {
                  "zoneCode": "ZONE-A01-R01",
                  "weight": 1.0
                }
              ],
              "initiallyEnabled": true
            }
          ],
          "exhaustUnits": [
            {
              "code": "EXHAUST-01",
              "ratedAirflowCubicMetersPerSecond": 8.0,
              "influences": [
                {
                  "zoneCode": "ZONE-A01-R01",
                  "weight": 1.0
                }
              ],
              "initiallyEnabled": false
            }
          ],
          "options": {
            "airDensityKilogramsPerCubicMeter": 1.204,
            "airSpecificHeatJoulesPerKilogramKelvin": 1005.0,
            "initialInletAirTemperatureCelsius": 24.0,
            "maximumRecirculationFraction": 0.95,
            "residualRecirculationFraction": 0.10,
            "effectiveZoneAirVolumeCubicMeters": 1000.0,
            "recirculationResponseTimeSeconds": 300.0
          }
        }
        """);
    }

    @Test
    void shouldBuildCoolingConfigurationFromLoadedJson()
            throws IOException {
        Path path = writeValidCoolingConfig();

        DatacenterDefinition definition =
                new JsonDatacenterConfigLoader().load(path);

        Datacenter datacenter =
                new DatacenterFactory().create(definition);

        CoolingConfiguration configuration =
                new CoolingConfigurationFactory()
                        .create(definition, datacenter)
                        .orElseThrow();

        assertEquals(1, configuration.zones().size());
        assertEquals(2, configuration.units().size());

        assertEquals(
                "ZONE-A01-R01",
                configuration.zones().getFirst().code()
        );

        assertEquals(
                Set.of(
                        new ServerLocation(
                                "A01",
                                "RACK-A01-R01",
                                "U01"
                        ),
                        new ServerLocation(
                                "A01",
                                "RACK-A01-R01",
                                "U02"
                        )
                ),
                configuration.zones()
                        .getFirst()
                        .serverLocations()
        );

        SupplyCoolingUnitDefinition supply =
                assertInstanceOf(
                        SupplyCoolingUnitDefinition.class,
                        configuration.units().get(0)
                );

        assertEquals("SUPPLY-01", supply.code());
        assertEquals(
                100000.0,
                supply.ratedCoolingCapacityWatts()
        );
        assertTrue(supply.initiallyEnabled());

        ExhaustCoolingUnitDefinition exhaust =
                assertInstanceOf(
                        ExhaustCoolingUnitDefinition.class,
                        configuration.units().get(1)
                );

        assertEquals("EXHAUST-01", exhaust.code());
        assertFalse(exhaust.initiallyEnabled());

        assertEquals(
                24.0,
                configuration.options()
                        .initialInletAirTemperatureCelsius()
        );
        assertEquals(
                0.95,
                configuration.options()
                        .maximumRecirculationFraction()
        );
        assertEquals(
                1_000.0,
                definition.cooling()
                        .options()
                        .effectiveZoneAirVolumeCubicMeters()
        );
    }

    @Test
    void shouldLoadDatacenterDefinitionWithCoolingBlock()
            throws IOException {
        Path path = writeValidCoolingConfig();

        DatacenterDefinition definition =
                new JsonDatacenterConfigLoader().load(path);

        assertNotNull(definition.cooling());

        assertEquals(1, definition.cooling().zones().size());

        assertEquals(
                "ZONE-A01-R01",
                definition.cooling()
                        .zones()
                        .getFirst()
                        .code()
        );

        assertEquals(
                List.of("A01"),
                definition.cooling()
                        .zones()
                        .getFirst()
                        .columns()
        );

        assertEquals(
                List.of("RACK-A01-R01"),
                definition.cooling()
                        .zones()
                        .getFirst()
                        .rackCodes()
        );

        assertEquals(1, definition.cooling().supplyUnits().size());

        assertEquals(
                "SUPPLY-01",
                definition.cooling()
                        .supplyUnits()
                        .getFirst()
                        .code()
        );

        assertEquals(
                8.0,
                definition.cooling()
                        .supplyUnits()
                        .getFirst()
                        .ratedAirflowCubicMetersPerSecond()
        );

        assertEquals(
                100_000.0,
                definition.cooling()
                        .supplyUnits()
                        .getFirst()
                        .ratedCoolingCapacityWatts()
        );

        assertEquals(
                18.0,
                definition.cooling()
                        .supplyUnits()
                        .getFirst()
                        .supplyAirTemperatureCelsius()
        );

        assertTrue(
                definition.cooling()
                        .supplyUnits()
                        .getFirst()
                        .initiallyEnabled()
        );

        assertEquals(
                "ZONE-A01-R01",
                definition.cooling()
                        .supplyUnits()
                        .getFirst()
                        .influences()
                        .getFirst()
                        .zoneCode()
        );

        assertEquals(
                1.0,
                definition.cooling()
                        .supplyUnits()
                        .getFirst()
                        .influences()
                        .getFirst()
                        .weight()
        );

        assertEquals(1, definition.cooling().exhaustUnits().size());

        assertEquals(
                "EXHAUST-01",
                definition.cooling()
                        .exhaustUnits()
                        .getFirst()
                        .code()
        );

        assertEquals(
                8.0,
                definition.cooling()
                        .exhaustUnits()
                        .getFirst()
                        .ratedAirflowCubicMetersPerSecond()
        );

        assertFalse(
                definition.cooling()
                        .exhaustUnits()
                        .getFirst()
                        .initiallyEnabled()
        );

        assertEquals(
                "ZONE-A01-R01",
                definition.cooling()
                        .exhaustUnits()
                        .getFirst()
                        .influences()
                        .getFirst()
                        .zoneCode()
        );

        assertEquals(
                1.0,
                definition.cooling()
                        .exhaustUnits()
                        .getFirst()
                        .influences()
                        .getFirst()
                        .weight()
        );

        assertNotNull(definition.cooling().options());

        assertEquals(
                1.204,
                definition.cooling()
                        .options()
                        .airDensityKilogramsPerCubicMeter()
        );

        assertEquals(
                1_005.0,
                definition.cooling()
                        .options()
                        .airSpecificHeatJoulesPerKilogramKelvin()
        );

        assertEquals(
                24.0,
                definition.cooling()
                        .options()
                        .initialInletAirTemperatureCelsius()
        );

        assertEquals(
                0.95,
                definition.cooling()
                        .options()
                        .maximumRecirculationFraction()
        );
    }

    @Test
    void shouldRejectExplicitNullCoolingBlock() throws IOException {
        Path path = writeConfigWithCooling("null");

        DatacenterConfigException exception = assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(path)
        );

        assertTrue(
                exceptionMessages(exception)
                        .contains("Cooling block cannot be null")
        );
    }

    @Test
    void shouldRejectNonObjectCoolingBlock() throws IOException {
        Path path = writeConfigWithCooling("[]");

        DatacenterConfigException exception = assertThrows(
                DatacenterConfigException.class,
                () -> new JsonDatacenterConfigLoader().load(path)
        );

        assertTrue(exceptionMessages(exception).contains("CoolingConfigDefinition"));
    }

    @Test
    void shouldExecuteCoolingTickFromLoadedJson()
            throws IOException {
        Path path = writeValidCoolingConfig();

        DatacenterDefinition definition =
                new JsonDatacenterConfigLoader().load(path);

        Datacenter datacenter =
                new DatacenterFactory().create(definition);

        CoolingConfiguration configuration =
                new CoolingConfigurationFactory()
                        .create(definition, datacenter)
                        .orElseThrow();

        CoolingSystem coolingSystem =
                new CoolingSystem(configuration);

        List<ServerHeatLoad> heatLoads =
                new ServerHeatLoadProvider(datacenter)
                        .createHeatLoads();

        CoolingSnapshot snapshot =
                coolingSystem.tick(
                        new CoolingTickInput(
                                1L,
                                heatLoads
                        )
                );

        assertEquals(1L, snapshot.tickIndex());
        assertEquals(2, heatLoads.size());
        assertEquals(2, snapshot.units().size());
        assertEquals(1, snapshot.zones().size());

        assertTrue(
                snapshot.units()
                        .get(0)
                        .enabled()
        );

        assertFalse(
                snapshot.units()
                        .get(1)
                        .enabled()
        );

        CoolingZoneSnapshot zone =
                snapshot.zones().getFirst();

        double expectedGeneratedHeatWatts =
                heatLoads.stream()
                        .mapToDouble(
                                ServerHeatLoad::generatedHeatWatts
                        )
                        .sum();

        assertEquals("ZONE-A01-R01", zone.zoneCode());

        assertEquals(
                expectedGeneratedHeatWatts,
                zone.generatedHeatWatts()
        );

        assertEquals(
                100_000.0,
                zone.availableCoolingCapacityWatts()
        );

        assertEquals(
                Math.min(
                        expectedGeneratedHeatWatts,
                        100_000.0
                ),
                zone.usedCoolingCapacityWatts()
        );

        assertEquals(
                Math.max(
                        0.0,
                        expectedGeneratedHeatWatts - 100_000.0
                ),
                zone.coolingDeficitWatts()
        );

        assertEquals(
                8.0,
                zone.supplyAirflowCubicMetersPerSecond()
        );

        assertEquals(
                0.0,
                zone.exhaustAirflowCubicMetersPerSecond()
        );

        assertEquals(
                0.95,
                zone.recirculationFraction(),
                1.0e-9
        );
    }
}

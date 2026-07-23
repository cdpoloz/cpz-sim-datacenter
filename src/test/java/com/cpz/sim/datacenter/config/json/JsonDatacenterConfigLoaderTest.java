package com.cpz.sim.datacenter.config.json;

import com.cpz.sim.datacenter.config.DatacenterConfigException;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.model.Datacenter;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class JsonDatacenterConfigLoaderTest {

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
        assertEquals(null, definition.servers().getFirst().column());
        assertEquals("RACK-A01-R01", definition.servers().getFirst().rackCode());
        assertEquals("U01", definition.servers().getFirst().slot());
        assertEquals(1.5f, definition.servers().getFirst().workloadFactor());
        assertEquals(null, definition.temperature());
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

}

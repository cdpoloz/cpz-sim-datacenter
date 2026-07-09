package com.cpz.sim.datacenter.config.json;

import com.cpz.sim.datacenter.config.DatacenterConfigException;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.model.Datacenter;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
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
        assertEquals(1, definition.serverModels().size());
        assertEquals(2, definition.servers().size());
        assertEquals("SRV-DEMO-001", definition.serverModels().getFirst().modelCode());
        assertEquals("RACK-A01-R01", definition.servers().getFirst().rackCode());
        assertEquals("U01", definition.servers().getFirst().slot());
        assertEquals(1.5f, definition.servers().getFirst().workloadFactor());
        assertEquals(null, definition.temperature());
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

}

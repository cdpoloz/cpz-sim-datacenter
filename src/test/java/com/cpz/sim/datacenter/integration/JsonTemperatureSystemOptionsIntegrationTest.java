package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.TemperatureSystemOptionsFactory;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author CPZ
 */
class JsonTemperatureSystemOptionsIntegrationTest {

    private static Path resourcePath(String resourceName) {
        try {
            return Path.of(
                    Objects.requireNonNull(
                            JsonTemperatureSystemOptionsIntegrationTest.class
                                    .getClassLoader()
                                    .getResource(resourceName)
                    ).toURI()
            );
        } catch (URISyntaxException exception) {
            throw new AssertionError("Invalid test resource path: " + resourceName, exception);
        }
    }

    @Test
    void shouldLoadDatacenterAndTemperatureOptionsFromSameJsonDefinition() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterDefinition definition = loader.load(
                resourcePath("datacenter/datacenter-with-temperature.json")
        );
        Datacenter datacenter = new DatacenterFactory().create(definition);
        TemperatureSystemOptions options = new TemperatureSystemOptionsFactory().create(definition);
        assertEquals(1, datacenter.getRackCount());
        assertEquals(1, datacenter.getServerCount());
        assertEquals(24.0, options.ambientTemperatureCelsius());
        assertEquals(30.0, options.defaultInitialTemperatureCelsius());
        assertEquals(5000.0, options.thermalCapacityJoulesPerCelsius());
        assertEquals(8.0, options.heatDissipationWattsPerCelsius());
    }
}

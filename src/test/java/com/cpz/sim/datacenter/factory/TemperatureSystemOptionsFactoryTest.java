package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.config.definition.TemperatureSystemOptionsDefinition;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class TemperatureSystemOptionsFactoryTest {

    private static DatacenterDefinition validDefinitionWithTemperature(
            TemperatureSystemOptionsDefinition temperature
    ) {
        return new DatacenterDefinition(
                "Demo Datacenter",
                new DatacenterLayoutDefinition(
                        List.of(new RackDefinition("RACK-A01-R01", "A01", "R01", 42))
                ),
                List.of(
                        new ServerModelDefinition(
                                "SRV-DEMO-001",
                                "CPZ",
                                "Demo Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U01",
                                "SRV-DEMO-001",
                                "OK",
                                1.0f
                        )
                ),
                temperature
        );
    }

    @Test
    void rejectsNullDatacenterDefinition() {
        TemperatureSystemOptionsFactory factory = new TemperatureSystemOptionsFactory();
        assertThrows(NullPointerException.class, () -> factory.create((DatacenterDefinition) null));
    }

    @Test
    void nullTemperatureDefinitionReturnsDefaults() {
        TemperatureSystemOptionsFactory factory = new TemperatureSystemOptionsFactory();
        TemperatureSystemOptions options = factory.create((TemperatureSystemOptionsDefinition) null);
        assertEquals(TemperatureSystemOptions.defaults(), options);
    }

    @Test
    void datacenterDefinitionWithoutTemperatureReturnsDefaults() {
        TemperatureSystemOptionsFactory factory = new TemperatureSystemOptionsFactory();
        TemperatureSystemOptions options = factory.create(validDefinitionWithTemperature(null));
        assertEquals(TemperatureSystemOptions.defaults(), options);
    }

    @Test
    void datacenterDefinitionWithTemperatureReturnsConfiguredValues() {
        TemperatureSystemOptionsFactory factory = new TemperatureSystemOptionsFactory();
        TemperatureSystemOptions options = factory.create(
                validDefinitionWithTemperature(
                        new TemperatureSystemOptionsDefinition(24.0, 30.0, 5000.0, 8.0)
                )
        );
        assertEquals(24.0, options.ambientTemperatureCelsius());
        assertEquals(30.0, options.defaultInitialTemperatureCelsius());
        assertEquals(5000.0, options.thermalCapacityJoulesPerCelsius());
        assertEquals(8.0, options.heatDissipationWattsPerCelsius());
    }
}

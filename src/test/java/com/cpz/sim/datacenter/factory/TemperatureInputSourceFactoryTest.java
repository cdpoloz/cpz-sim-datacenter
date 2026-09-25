package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.input.AisleTemperatureToRackTemperatureInputSource;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.input.RackTemperatureInputSource;
import com.cpz.sim.datacenter.input.SimulatedAisleTemperatureInputSource;
import com.cpz.sim.datacenter.input.SimulatedRackTemperatureInputSource;
import com.cpz.sim.datacenter.input.StandardHotAisleCodeResolver;
import com.cpz.sim.datacenter.input.TemperatureInputGranularity;
import com.cpz.sim.datacenter.model.ServerRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TemperatureInputSourceFactoryTest {

    @Test
    void shouldCreateRackLevelSourceByDefault() {
        RackTemperatureInputSource source =
                new TemperatureInputSourceFactory().create(definition(TemperatureInputGranularity.RACK));

        assertInstanceOf(SimulatedRackTemperatureInputSource.class, source);
    }

    @Test
    void shouldCreateAisleAdapterWithStandardHotAisleResolverWhenConfigured() throws Exception {
        RackTemperatureInputSource source =
                new TemperatureInputSourceFactory().create(definition(TemperatureInputGranularity.AISLE));

        AisleTemperatureToRackTemperatureInputSource adapter =
                assertInstanceOf(AisleTemperatureToRackTemperatureInputSource.class, source);
        assertInstanceOf(SimulatedAisleTemperatureInputSource.class, fieldValue(adapter, "aisleTemperatureInputSource"));
        assertInstanceOf(StandardHotAisleCodeResolver.class, fieldValue(adapter, "aisleCodeResolver"));
    }

    private static DatacenterDefinition definition(TemperatureInputGranularity granularity) {
        return new DatacenterDefinition(
                "Temperature Input Source Factory Test",
                new DatacenterLayoutDefinition(
                        List.of(new RackDefinition("RACK-C04-R01", "C04", "R01", List.of("U01")))
                ),
                List.of(new ServerModelDefinition("MODEL-01", "CPZ", "Temperature Test Server", 100.0f, 300.0f)),
                List.of(new ServerDefinition("RACK-C04-R01", "U01", "MODEL-01", "OK", ServerRole.GENERAL_PURPOSE)),
                null,
                null,
                null,
                DatacenterDataInputMode.TEMPERATURE_DRIVEN,
                null,
                granularity
        );
    }

    private static Object fieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}

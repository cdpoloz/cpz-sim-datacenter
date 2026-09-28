package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.HotAisleDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.input.AisleTemperatureToRackTemperatureInputSource;
import com.cpz.sim.datacenter.input.ConfiguredHotAisleCodeResolver;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.input.RackTemperatureInputSource;
import com.cpz.sim.datacenter.input.SimulatedAisleTemperatureInputSource;
import com.cpz.sim.datacenter.input.SimulatedRackTemperatureInputSource;
import com.cpz.sim.datacenter.input.StandardHotAisleCodeResolver;
import com.cpz.sim.datacenter.input.TemperatureInputGranularity;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void shouldCreateAisleAdapterWithConfiguredHotAisleResolverWhenLayoutDeclaresHotAisles() throws Exception {
        RackTemperatureInputSource source =
                new TemperatureInputSourceFactory().create(definition(
                        TemperatureInputGranularity.AISLE,
                        List.of(new HotAisleDefinition("CUSTOM-HA", List.of("C04")))
                ));

        AisleTemperatureToRackTemperatureInputSource adapter =
                assertInstanceOf(AisleTemperatureToRackTemperatureInputSource.class, source);
        assertInstanceOf(SimulatedAisleTemperatureInputSource.class, fieldValue(adapter, "aisleTemperatureInputSource"));
        assertInstanceOf(ConfiguredHotAisleCodeResolver.class, fieldValue(adapter, "aisleCodeResolver"));
    }

    @Test
    void shouldApplySameAisleTemperatureToColumnsSharingConfiguredHotAisle() {
        RackTemperatureInputSource source =
                new TemperatureInputSourceFactory().create(definition(
                        TemperatureInputGranularity.AISLE,
                        List.of(new HotAisleDefinition("HA03", List.of("C04", "C05")))
                ));

        SimulationTick tick = new SimulationTick(1L, Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertEquals(
                source.temperatureCelsius(new RackLocation("C04", "R01"), tick),
                source.temperatureCelsius(new RackLocation("C05", "R01"), tick),
                0.0001
        );
    }

    private static DatacenterDefinition definition(TemperatureInputGranularity granularity) {
        return definition(granularity, null);
    }

    private static DatacenterDefinition definition(
            TemperatureInputGranularity granularity,
            List<HotAisleDefinition> hotAisles
    ) {
        return new DatacenterDefinition(
                "Temperature Input Source Factory Test",
                new DatacenterLayoutDefinition(
                        null,
                        hotAisles,
                        List.of(
                                new RackDefinition("RACK-C04-R01", "C04", "R01", List.of("U01")),
                                new RackDefinition("RACK-C05-R01", "C05", "R01", List.of("U01"))
                        )
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

package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.config.definition.HotAisleDefinition;
import com.cpz.sim.datacenter.model.RackLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredHotAisleCodeResolverTest {

    @Test
    void shouldResolveConfiguredStandardHotAisleColumns() {
        ConfiguredHotAisleCodeResolver resolver = standardResolver();

        assertEquals("HA01", resolver.apply(new RackLocation("C01", "R01")));
        assertEquals("HA02", resolver.apply(new RackLocation("C02", "R01")));
        assertEquals("HA02", resolver.apply(new RackLocation("C03", "R01")));
        assertEquals("HA03", resolver.apply(new RackLocation("C04", "R01")));
        assertEquals("HA03", resolver.apply(new RackLocation("C05", "R01")));
        assertEquals("HA04", resolver.apply(new RackLocation("C06", "R01")));
        assertEquals("HA04", resolver.apply(new RackLocation("C07", "R01")));
        assertEquals("HA05", resolver.apply(new RackLocation("C08", "R01")));
    }

    @Test
    void shouldRejectUnmappedColumnWithClearMessage() {
        ConfiguredHotAisleCodeResolver resolver = standardResolver();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.apply(new RackLocation("C09", "R01"))
        );

        assertTrue(exception.getMessage().contains("No configured hot aisle for rack column: C09"));
    }

    private static ConfiguredHotAisleCodeResolver standardResolver() {
        return new ConfiguredHotAisleCodeResolver(List.of(
                new HotAisleDefinition("HA01", List.of("C01")),
                new HotAisleDefinition("HA02", List.of("C02", "C03")),
                new HotAisleDefinition("HA03", List.of("C04", "C05")),
                new HotAisleDefinition("HA04", List.of("C06", "C07")),
                new HotAisleDefinition("HA05", List.of("C08"))
        ));
    }
}

package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSystemOptions;
import com.cpz.sim.datacenter.cooling.CoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.ExhaustCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class CoolingSystemTest {

    @Test
    void shouldRejectNullConfiguration() {
        assertThrows(NullPointerException.class, () -> new CoolingSystem(null));
    }

    @Test
    void shouldInitializeStatesFromDefinitions() {
        CoolingSystem system = new CoolingSystem(configuration());
        assertTrue(system.isEnabled("SUPPLY-01"));
        assertFalse(system.isEnabled("EXHAUST-01"));
    }

    @Test
    void shouldSetEnabledState() {
        CoolingSystem system = new CoolingSystem(configuration());
        system.setEnabled("EXHAUST-01", true);
        assertTrue(system.isEnabled("EXHAUST-01"));
        system.setEnabled("EXHAUST-01", false);
        assertFalse(system.isEnabled("EXHAUST-01"));
    }

    @Test
    void shouldEnableUnit() {
        CoolingSystem system = new CoolingSystem(configuration());
        system.enable("EXHAUST-01");
        assertTrue(system.isEnabled("EXHAUST-01"));
    }

    @Test
    void shouldDisableUnit() {
        CoolingSystem system = new CoolingSystem(configuration());
        system.disable("SUPPLY-01");
        assertFalse(system.isEnabled("SUPPLY-01"));
    }

    @Test
    void shouldToggleUnitAndReturnNewState() {
        CoolingSystem system = new CoolingSystem(configuration());
        assertFalse(system.toggle("SUPPLY-01"));
        assertFalse(system.isEnabled("SUPPLY-01"));
        assertTrue(system.toggle("SUPPLY-01"));
        assertTrue(system.isEnabled("SUPPLY-01"));
    }

    @Test
    void shouldReturnSameStateWhenRequestedValueIsUnchanged() {
        CoolingSystem system = new CoolingSystem(configuration());
        var originalState = system.stateOf("SUPPLY-01");
        system.setEnabled("SUPPLY-01", true);
        assertSame(originalState, system.stateOf("SUPPLY-01"));
    }

    @Test
    void shouldReturnUnitDefinition() {
        CoolingSystem system = new CoolingSystem(configuration());
        CoolingUnitDefinition definition = system.definitionOf("SUPPLY-01");
        assertSame(system.configuration().units().getFirst(), definition);
    }

    @Test
    void shouldRejectNullUnitCode() {
        CoolingSystem system = new CoolingSystem(configuration());
        assertThrows(NullPointerException.class, () -> system.isEnabled(null));
        assertThrows(NullPointerException.class, () -> system.setEnabled(null, true));
        assertThrows(NullPointerException.class, () -> system.definitionOf(null));
    }

    @Test
    void shouldRejectUnknownUnitCode() {
        CoolingSystem system = new CoolingSystem(configuration());
        assertThrows(IllegalArgumentException.class, () -> system.isEnabled("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> system.enable("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> system.definitionOf("UNKNOWN"));
    }

    private static CoolingConfiguration configuration() {
        CoolingZoneDefinition zone = new CoolingZoneDefinition("ZONE-01", Set.of());
        SupplyCoolingUnitDefinition supply =
                new SupplyCoolingUnitDefinition(
                        "SUPPLY-01",
                        4.0,
                        12_000.0,
                        18.0,
                        List.of(new CoolingZoneInfluence("ZONE-01", 1.0)),
                        true
                );
        ExhaustCoolingUnitDefinition exhaust =
                new ExhaustCoolingUnitDefinition(
                        "EXHAUST-01",
                        4.0,
                        List.of(new CoolingZoneInfluence("ZONE-01",1.0)),
                        false
                );
        return new CoolingConfiguration(List.of(zone), List.of(supply, exhaust), CoolingSystemOptions.defaults());
    }

}
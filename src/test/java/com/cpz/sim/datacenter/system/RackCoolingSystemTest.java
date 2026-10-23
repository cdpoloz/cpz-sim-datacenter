package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.cooling.*;
import com.cpz.sim.datacenter.model.RackLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RackCoolingSystemTest {
    private static final RackLocation RACK_1 = new RackLocation("C01", "R01");
    private static final RackLocation RACK_2 = new RackLocation("C02", "R01");

    private RackCoolingUnitDefinition unit(String code, RackLocation rack, boolean enabled,
                                           double watts, HeatRejectionDestination destination) {
        return new RackCoolingUnitDefinition(code, rack, 24, 10000, 0,
                0.1, 2000, 18, watts, destination, enabled);
    }

    private RackCoolingSystem system() {
        return new RackCoolingSystem(new RackCoolingConfiguration(1.204, 1005, List.of(
                unit("RC01", RACK_1, true, 500, HeatRejectionDestination.ROOM),
                unit("RC02", RACK_2, false, 800, HeatRejectionDestination.EXTERNAL))));
    }

    @Test
    void initializesControlsAndSeparatesElectricalPowerFromRefrigerationCapacity() {
        var system = system();
        assertTrue(system.isEnabled("RC01"));
        assertFalse(system.isEnabled("RC02"));
        assertEquals(500, system.currentElectricalPowerWatts());
        assertEquals(500, system.currentElectricalPowerWatts("RC01"));
        assertEquals(0, system.currentElectricalPowerWatts("RC02"));
        var snapshots = system.currentUnitSnapshots();
        var first = snapshots.getFirst();
        assertEquals(CoolingUnitType.RACK, first.type());
        assertEquals(2000, first.currentCoolingPowerWatts());
        assertEquals(500, first.currentElectricalPowerWatts());
        assertEquals(0.1, first.currentAirflowCubicMetersPerSecond());
        assertTrue(first.isOperating());
        var second = snapshots.get(1);
        assertEquals(0, second.currentCoolingPowerWatts());
        assertEquals(0, second.currentElectricalPowerWatts());
        assertEquals(0, second.currentAirflowCubicMetersPerSecond());
        assertEquals(800, second.ratedElectricalPowerWatts());
        assertFalse(second.isOperating());
    }

    @Test
    void controlsEachUnitIndependentlyAndResetRestoresBothInitialStates() {
        var system = system();
        system.enable("RC02");
        assertEquals(1300, system.currentElectricalPowerWatts());
        system.enable("RC02");
        assertEquals(1300, system.currentElectricalPowerWatts());
        system.disable("RC01");
        assertEquals(800, system.currentElectricalPowerWatts());
        system.setEnabled("RC02", false);
        assertEquals(0, system.currentElectricalPowerWatts());
        assertTrue(system.toggle("RC01"));
        assertFalse(system.toggle("RC01"));
        system.enable("RC02");
        system.reset();
        assertTrue(system.isEnabled("RC01"));
        assertFalse(system.isEnabled("RC02"));
        assertEquals(500, system.currentElectricalPowerWatts());
    }

    @Test
    void usesFullRackIdentityAndKeepsDestinationInImmutableDefinitions() {
        var system = system();
        assertEquals("RC01", system.findUnitForRack(RACK_1).orElseThrow().code());
        assertEquals("RC02", system.findUnitForRack(RACK_2).orElseThrow().code());
        assertTrue(system.findUnitForRack(new RackLocation("C03", "R01")).isEmpty());
        assertEquals(HeatRejectionDestination.EXTERNAL, system.definitionOf("RC02").heatRejectionDestination());
        assertTrue(system.definitionOf("RC01").influences().isEmpty());
        assertEquals(system.configuration().units().getFirst(), system.definitionOf("RC01"));
    }

    @Test
    void stateAndSnapshotsAreStableAfterCommandsAndCapturesDoNotAdvanceAnything() {
        var system = system();
        var state = system.stateOf("RC01");
        var before = system.currentUnitSnapshots();
        assertEquals(before, system.currentUnitSnapshots());
        system.disable("RC01");
        assertTrue(state.enabled());
        assertTrue(before.getFirst().enabled());
        assertEquals(500, before.getFirst().currentElectricalPowerWatts());
        assertFalse(system.currentUnitSnapshots().getFirst().enabled());
        assertThrows(UnsupportedOperationException.class, before::clear);
        system.reset();
        assertEquals(before, system.currentUnitSnapshots());
    }

    @Test
    void rejectsUnknownAndNullControlsWithoutChangingExistingEquipment() {
        var system = system();
        var before = system.currentUnitSnapshots();
        assertThrows(IllegalArgumentException.class, () -> system.definitionOf("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> system.stateOf("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> system.toggle("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> system.setEnabled("UNKNOWN", true));
        assertThrows(IllegalArgumentException.class, () -> system.currentElectricalPowerWatts("UNKNOWN"));
        assertThrows(NullPointerException.class, () -> system.enable(null));
        assertThrows(NullPointerException.class, () -> system.findUnitForRack(null));
        assertEquals(before, system.currentUnitSnapshots());
    }

    @Test
    void separateInstancesDoNotShareOperationalState() {
        var configuration = system().configuration();
        var first = new RackCoolingSystem(configuration);
        var second = new RackCoolingSystem(configuration);
        first.disable("RC01");
        assertTrue(second.isEnabled("RC01"));
        assertTrue(configuration.units().getFirst().initiallyEnabled());
    }

    @Test
    void roomAndRackEquipmentCannotBeRegisteredInTheSameRoomConfiguration() {
        var rack = system().definitionOf("RC01");
        var error = assertThrows(IllegalArgumentException.class, () -> new CoolingConfiguration(
                List.of(new CoolingZoneDefinition("ZONE", Set.of())),
                List.of(rack), CoolingSystemOptions.defaults()));
        assertTrue(error.getMessage().contains("RackCoolingConfiguration"));
    }

    @Test
    void configurationIsDefensiveAndRejectsDuplicateCodesOrRackLocations() {
        var first = unit("RC01", RACK_1, true, 500, HeatRejectionDestination.ROOM);
        var units = new ArrayList<>(List.of(first));
        var config = new RackCoolingConfiguration(1.204, 1005, units);
        units.clear();
        assertEquals(1, config.units().size());
        assertThrows(UnsupportedOperationException.class, () -> config.units().clear());
        assertThrows(IllegalArgumentException.class, () -> new RackCoolingConfiguration(1.204, 1005, List.of()));
        assertThrows(NullPointerException.class, () -> new RackCoolingConfiguration(1.204, 1005, null));
        assertThrows(NullPointerException.class,
                () -> new RackCoolingConfiguration(1.204, 1005, Arrays.asList(first, null)));
        assertThrows(IllegalArgumentException.class, () -> new RackCoolingConfiguration(1.204, 1005,
                List.of(first, unit("RC01", RACK_2, true, 500, HeatRejectionDestination.ROOM))));
        assertThrows(IllegalArgumentException.class, () -> new RackCoolingConfiguration(1.204, 1005,
                List.of(first, unit("RC02", RACK_1, true, 500, HeatRejectionDestination.ROOM))));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void runtimeDefinitionsRejectNonPhysicalMagnitudes(double value) {
        // Each numeric slot is tested separately so one invalid field cannot mask another.
        for (int index : new int[]{1, 3, 4, 6}) {
            double[] values = {24, 10000, 0, 0.1, 2000, 18, 500};
            values[index] = value;
            assertThrows(IllegalArgumentException.class, () -> new RackCoolingUnitDefinition(
                    "RC01", RACK_1, values[0], values[1], values[2], values[3], values[4], values[5],
                    values[6], HeatRejectionDestination.ROOM, true));
        }
        assertThrows(IllegalArgumentException.class, () -> new RackCoolingConfiguration(value, 1005, system().configuration().units()));
        assertThrows(IllegalArgumentException.class, () -> new RackCoolingConfiguration(1.204, value, system().configuration().units()));
    }

    @Test
    void rejectsNullIdentityDestinationAndNonFiniteTemperaturesOrConductance() {
        assertThrows(NullPointerException.class, () -> unit(null, RACK_1, true, 500, HeatRejectionDestination.ROOM));
        assertThrows(IllegalArgumentException.class, () -> unit(" ", RACK_1, true, 500, HeatRejectionDestination.ROOM));
        assertThrows(NullPointerException.class, () -> unit("RC01", null, true, 500, HeatRejectionDestination.ROOM));
        assertThrows(NullPointerException.class, () -> unit("RC01", RACK_1, true, 500, null));
        assertThrows(NullPointerException.class, () -> new RackCoolingSystem(null));
        for (int index : new int[]{0, 2, 5}) {
            double[] values = {24, 10000, 0, 0.1, 2000, 18, 500};
            values[index] = Double.NaN;
            assertThrows(IllegalArgumentException.class, () -> new RackCoolingUnitDefinition(
                    "RC01", RACK_1, values[0], values[1], values[2], values[3], values[4], values[5],
                    values[6], HeatRejectionDestination.ROOM, true));
        }
        assertThrows(IllegalArgumentException.class, () -> new RackCoolingUnitDefinition(
                "RC01", RACK_1, 24, 10000, -1, 0.1, 2000, 18, 500, HeatRejectionDestination.ROOM, true));
    }

    @Test
    void heatDestinationDoesNotChangeEquipmentElectricalPower() {
        var room = new RackCoolingSystem(new RackCoolingConfiguration(1.204, 1005,
                List.of(unit("RC01", RACK_1, true, 500, HeatRejectionDestination.ROOM))));
        var external = new RackCoolingSystem(new RackCoolingConfiguration(1.204, 1005,
                List.of(unit("RC01", RACK_1, true, 500, HeatRejectionDestination.EXTERNAL))));
        assertEquals(room.currentElectricalPowerWatts(), external.currentElectricalPowerWatts());
    }
}

package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.example.RackCoolingComparisonDemo.Scenario;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.model.RackLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RackCoolingComparisonDemoTest {
    private static final RackLocation COOLED = new RackLocation("C01", "R01");
    private static final RackLocation COMPARISON = new RackLocation("C01", "R02");

    private DatacenterDefinition base() {
        return new JsonDatacenterConfigLoader().load(Path.of("data/config/rack-cooling-comparison.json"));
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void checkedInExampleConservesEnergyAndSeparatesThermalAndElectricalBoundaries(Scenario scenario) {
        var base = base();
        var result = RackCoolingComparisonDemo.run(base, scenario, 60);
        assertEquals(60, result.steps().size());
        var step = result.last();
        var op = step.operationalSnapshot();
        var energy = step.facilityEnergySnapshot().orElseThrow();
        boolean installed = scenario != Scenario.NO_UNIT;
        boolean enabled = installed && scenario != Scenario.DISABLED;
        double rackElectrical = enabled ? 250 : 0;
        double roomElectrical = scenario.roomCoolingEnabled() ? 600 : 0;
        assertEquals(800, op.currentItPowerWatts(), 1e-10);
        assertEquals(rackElectrical + roomElectrical, op.coolingPowerWatts(), 1e-10);
        assertEquals(800 + rackElectrical + roomElectrical, energy.consumedFacilityEnergyWh(), 1e-8);
        assertEquals((800 + rackElectrical + roomElectrical) / 800, op.pue(), 1e-10);
        assertEquals(op.pue(), energy.energyPue(), 1e-10);
        assertEquals(installed, step.rackCoolingSnapshot().isPresent());
        double retained = 200000 * (op.roomAirTemperatureCelsius() - 24);
        for (var server : step.temperatureSnapshot().servers()) retained += 5000 * (server.temperatureCelsius() - 30);
        if (installed) retained += 10000 * (step.rackCoolingSnapshot().orElseThrow()
                .findRack(COOLED).orElseThrow().airTemperatureCelsius() - 24);
        double removed = 0, toRoom = 0, external = 0, roomRemoved = 0;
        for (var tick : result.steps()) {
            var thermal = tick.coupledThermalSnapshot().orElseThrow();
            removed += thermal.rackRemovedHeatJoules();
            toRoom += thermal.rackHeatRejectedToRoomJoules();
            external += thermal.rackHeatRejectedExternalJoules();
            roomRemoved += thermal.removedHeatJoules();
            assertEquals(0, thermal.energyBalanceResidualJoules(), 2e-5);
        }
        assertEquals(removed + rackElectrical * 3600, toRoom + external, 1e-6);
        assertEquals(800 * 3600 + rackElectrical * 3600 - roomRemoved - external, retained, 0.001);
        if (enabled) {
            assertTrue(removed > 0);
            // With strong room HVAC, the directly room-cooled rack need not be warmer.
            if (!scenario.roomCoolingEnabled()) assertTrue(op.getRack(COOLED).averageOnlineTemperatureCelsius()
                    < op.getRack(COMPARISON).averageOnlineTemperatureCelsius());
        } else assertEquals(0, removed);
        if (scenario == Scenario.EXTERNAL || scenario == Scenario.EXTERNAL_WITH_HVAC) assertEquals(0, toRoom);
        else assertEquals(0, external);
        if (scenario == Scenario.NO_UNIT) assertEquals(op.getRack(COOLED).averageOnlineTemperatureCelsius(),
                op.getRack(COMPARISON).averageOnlineTemperatureCelsius(), 1e-10);
        // Example transformations never mutate the loaded template.
        assertTrue(base.rackCooling().units().getFirst().initiallyEnabled());
        assertFalse(base.cooling().supplyUnits().getFirst().initiallyEnabled());
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void comparisonSupportsEquivalentUtilizationAndPowerInputs(Scenario scenario) {
        var base = base();
        var power = new DatacenterDefinition(base.name(), base.layout(), base.serverModels(), base.servers(),
                base.temperature(), base.health(), base.cooling(), DatacenterDataInputMode.POWER_DRIVEN,
                base.powerInputGranularity(), base.temperatureInputGranularity(), base.roomAirTemperature(), base.rackCooling());
        var utilizationStep = RackCoolingComparisonDemo.run(base, scenario, 10).last();
        var powerStep = RackCoolingComparisonDemo.run(power, scenario, 10).last();
        assertEquals(utilizationStep.temperatureSnapshot(), powerStep.temperatureSnapshot());
        assertEquals(utilizationStep.coupledThermalSnapshot(), powerStep.coupledThermalSnapshot());
        assertEquals(utilizationStep.facilityEnergySnapshot(), powerStep.facilityEnergySnapshot());
    }

    @Test
    void roomRejectionWarmsCommonRoomMoreThanExternalRejectionWithMatchedLoads() {
        var room = RackCoolingComparisonDemo.run(base(), Scenario.ROOM, 60).last();
        var external = RackCoolingComparisonDemo.run(base(), Scenario.EXTERNAL, 60).last();
        assertTrue(room.operationalSnapshot().roomAirTemperatureCelsius() > external.operationalSnapshot().roomAirTemperatureCelsius());
        assertEquals(room.facilityEnergySnapshot(), external.facilityEnergySnapshot());
        assertThrows(IllegalArgumentException.class, () -> RackCoolingComparisonDemo.run(base(), Scenario.ROOM, 0));
    }
}

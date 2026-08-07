package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSnapshotCoordinator;
import com.cpz.sim.datacenter.cooling.DatacenterCoolingTickInputProvider;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingUnitSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.system.CoolingSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class CoolingSimulationDemoScenarioTest {

    private static final double EPSILON = 0.01;
    private static final double SERVER_UTILIZATION = 0.75;

    @Test
    void shouldProvideColdAirWithoutRecirculationWhenBothUnitsAreEnabled() {
        TestScenario scenario = createScenario();

        CoolingSnapshot snapshot = scenario.executeNextTick();
        CoolingZoneSnapshot zone = getZone(snapshot);

        assertUnitState(
                snapshot,
                CoolingSimulationDemoScenario.SUPPLY_UNIT_CODE,
                true,
                4.0,
                12_000.0
        );

        assertUnitState(
                snapshot,
                CoolingSimulationDemoScenario.EXHAUST_UNIT_CODE,
                true,
                4.0,
                0.0
        );

        assertEquals(800.0, zone.generatedHeatWatts(), EPSILON);
        assertEquals(
                12_000.0,
                zone.availableCoolingCapacityWatts(),
                EPSILON
        );
        assertEquals(0.0, zone.coolingDeficitWatts(), EPSILON);
        assertEquals(
                18.0,
                zone.inletAirTemperatureCelsius(),
                EPSILON
        );
        assertEquals(0.0, zone.recirculationFraction(), EPSILON);
        assertTrue(
                zone.exhaustAirTemperatureCelsius()
                        > zone.inletAirTemperatureCelsius()
        );
    }

    @Test
    void shouldLoseCoolingCapacityWhenOnlySupplyIsDisabled() {
        TestScenario scenario = createScenario();

        scenario.coolingSystem().disable(
                CoolingSimulationDemoScenario.SUPPLY_UNIT_CODE
        );

        CoolingSnapshot snapshot = scenario.executeNextTick();
        CoolingZoneSnapshot zone = getZone(snapshot);

        assertUnitState(
                snapshot,
                CoolingSimulationDemoScenario.SUPPLY_UNIT_CODE,
                false,
                0.0,
                0.0
        );

        assertUnitState(
                snapshot,
                CoolingSimulationDemoScenario.EXHAUST_UNIT_CODE,
                true,
                4.0,
                0.0
        );

        assertEquals(800.0, zone.generatedHeatWatts(), EPSILON);
        assertEquals(
                0.0,
                zone.availableCoolingCapacityWatts(),
                EPSILON
        );
        assertEquals(800.0, zone.coolingDeficitWatts(), EPSILON);
        assertEquals(
                24.0,
                zone.inletAirTemperatureCelsius(),
                EPSILON
        );
        assertEquals(0.95, zone.recirculationFraction(), EPSILON);
    }

    @Test
    void shouldDegradeInletTemperatureWhenOnlyExhaustIsDisabled() {
        TestScenario scenario = createScenario();

        scenario.coolingSystem().disable(
                CoolingSimulationDemoScenario.EXHAUST_UNIT_CODE
        );

        CoolingSnapshot snapshot = scenario.executeNextTick();
        CoolingZoneSnapshot zone = getZone(snapshot);

        assertUnitState(
                snapshot,
                CoolingSimulationDemoScenario.SUPPLY_UNIT_CODE,
                true,
                4.0,
                12_000.0
        );

        assertUnitState(
                snapshot,
                CoolingSimulationDemoScenario.EXHAUST_UNIT_CODE,
                false,
                0.0,
                0.0
        );

        assertEquals(800.0, zone.generatedHeatWatts(), EPSILON);
        assertEquals(
                12_000.0,
                zone.availableCoolingCapacityWatts(),
                EPSILON
        );
        assertEquals(0.0, zone.coolingDeficitWatts(), EPSILON);
        assertEquals(0.95, zone.recirculationFraction(), EPSILON);

        assertTrue(
                zone.inletAirTemperatureCelsius() > 18.0
        );
        assertTrue(
                zone.inletAirTemperatureCelsius() < 24.0
        );
        assertTrue(
                zone.exhaustAirTemperatureCelsius()
                        > zone.inletAirTemperatureCelsius()
        );
    }

    @Test
    void shouldHaveNeitherCoolingNorExtractionWhenBothUnitsAreDisabled() {
        TestScenario scenario = createScenario();

        scenario.coolingSystem().disable(
                CoolingSimulationDemoScenario.SUPPLY_UNIT_CODE
        );

        scenario.coolingSystem().disable(
                CoolingSimulationDemoScenario.EXHAUST_UNIT_CODE
        );

        CoolingSnapshot snapshot = scenario.executeNextTick();
        CoolingZoneSnapshot zone = getZone(snapshot);

        assertUnitState(
                snapshot,
                CoolingSimulationDemoScenario.SUPPLY_UNIT_CODE,
                false,
                0.0,
                0.0
        );

        assertUnitState(
                snapshot,
                CoolingSimulationDemoScenario.EXHAUST_UNIT_CODE,
                false,
                0.0,
                0.0
        );

        assertEquals(800.0, zone.generatedHeatWatts(), EPSILON);
        assertEquals(
                0.0,
                zone.availableCoolingCapacityWatts(),
                EPSILON
        );
        assertEquals(800.0, zone.coolingDeficitWatts(), EPSILON);
        assertEquals(
                24.0,
                zone.inletAirTemperatureCelsius(),
                EPSILON
        );
        assertEquals(0.95, zone.recirculationFraction(), EPSILON);
    }

    private static TestScenario createScenario() {
        Datacenter datacenter =
                CoolingSimulationDemoScenario.createDatacenter();

        CoolingConfiguration coolingConfiguration =
                CoolingSimulationDemoScenario
                        .createCoolingConfiguration(datacenter);

        WorkloadSystem workloadSystem =
                new WorkloadSystem(
                        datacenter,
                        new ConstantWorkloadSource(
                                SERVER_UTILIZATION
                        )
                );

        PowerConsumptionSystem powerSystem =
                new PowerConsumptionSystem(datacenter);

        CoolingSystem coolingSystem =
                new CoolingSystem(coolingConfiguration);

        CoolingSnapshotTemperatureReferenceProvider
                temperatureReferenceProvider =
                new CoolingSnapshotTemperatureReferenceProvider(
                        coolingConfiguration
                );

        CoolingSnapshotCoordinator coordinator =
                new CoolingSnapshotCoordinator(
                        new DatacenterCoolingTickInputProvider(
                                datacenter
                        ),
                        coolingSystem,
                        temperatureReferenceProvider
                );

        SimulationEngine engine =
                new SimulationEngine(
                        new SimulationClock(
                                Duration.ofMinutes(1)
                        )
                );

        engine.register(workloadSystem);
        engine.register(powerSystem);

        return new TestScenario(
                engine,
                coolingSystem,
                coordinator
        );
    }

    private static CoolingZoneSnapshot getZone(
            CoolingSnapshot snapshot
    ) {
        return snapshot.zones()
                .stream()
                .filter(zone -> zone.zoneCode().equals(
                        CoolingSimulationDemoScenario.ZONE_CODE
                ))
                .findFirst()
                .orElseThrow();
    }

    private static CoolingUnitSnapshot getUnit(
            CoolingSnapshot snapshot,
            String unitCode
    ) {
        return snapshot.units()
                .stream()
                .filter(unit -> unit.unitCode().equals(unitCode))
                .findFirst()
                .orElseThrow();
    }

    private static void assertUnitState(
            CoolingSnapshot snapshot,
            String unitCode,
            boolean expectedEnabled,
            double expectedAirflow,
            double expectedCoolingPower
    ) {
        CoolingUnitSnapshot unit =
                getUnit(snapshot, unitCode);

        if (expectedEnabled) {
            assertTrue(unit.enabled());
        } else {
            assertFalse(unit.enabled());
        }

        assertEquals(
                expectedAirflow,
                unit.currentAirflowCubicMetersPerSecond(),
                EPSILON
        );

        assertEquals(
                expectedCoolingPower,
                unit.currentCoolingPowerWatts(),
                EPSILON
        );
    }

    private record TestScenario(
            SimulationEngine engine,
            CoolingSystem coolingSystem,
            CoolingSnapshotCoordinator coordinator
    ) {

        CoolingSnapshot executeNextTick() {
            SimulationTick tick = engine.step();
            return coordinator.update(tick);
        }
    }
}
package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSnapshotCoordinator;
import com.cpz.sim.datacenter.cooling.DatacenterCoolingTickInputProvider;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.system.CoolingSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoolingSimulationDemoTemperatureIntegrationTest {

    private static final double EPSILON = 0.01;
    private static final double SERVER_UTILIZATION = 0.75;

    @Test
    void shouldUseResidualInletReferenceWhenBothUnitsAreEnabled() {
        ScenarioResult result = executeScenario(true, true);

        assertEquals(
                18.6,
                result.inletAirTemperatureCelsius(),
                EPSILON
        );

        /*
         * This is the value observed after one 60-second tick from an initial
         * server temperature of 25 °C, using the residual inlet reference.
         */
        assertEquals(
                29.19,
                result.serverTemperatureCelsius(),
                EPSILON
        );
    }

    @Test
    void shouldHeatServerMoreWhenSupplyIsDisabled() {
        ScenarioResult normalOperation =
                executeScenario(true, true);

        ScenarioResult supplyDisabled =
                executeScenario(false, true);

        assertEquals(
                24.0,
                supplyDisabled.inletAirTemperatureCelsius(),
                EPSILON
        );

        assertTrue(
                supplyDisabled.serverTemperatureCelsius()
                        > normalOperation.serverTemperatureCelsius()
        );
    }

    @Test
    void shouldUseDegradedInletReferenceWhenExhaustIsDisabled() {
        ScenarioResult normalOperation =
                executeScenario(true, true);

        ScenarioResult exhaustDisabled =
                executeScenario(true, false);

        assertTrue(
                exhaustDisabled.inletAirTemperatureCelsius() > 18.0
        );

        assertTrue(
                exhaustDisabled.inletAirTemperatureCelsius() < 24.0
        );

        assertTrue(
                exhaustDisabled.serverTemperatureCelsius()
                        > normalOperation.serverTemperatureCelsius()
        );
    }

    @Test
    void shouldPreserveThermalOrderingAcrossCoolingStates() {
        ScenarioResult normalOperation =
                executeScenario(true, true);

        ScenarioResult exhaustDisabled =
                executeScenario(true, false);

        ScenarioResult supplyDisabled =
                executeScenario(false, true);

        assertTrue(
                normalOperation.inletAirTemperatureCelsius()
                        < exhaustDisabled.inletAirTemperatureCelsius()
        );

        assertTrue(
                exhaustDisabled.inletAirTemperatureCelsius()
                        < supplyDisabled.inletAirTemperatureCelsius()
        );

        assertTrue(
                normalOperation.serverTemperatureCelsius()
                        < exhaustDisabled.serverTemperatureCelsius()
        );

        assertTrue(
                exhaustDisabled.serverTemperatureCelsius()
                        < supplyDisabled.serverTemperatureCelsius()
        );
    }

    private static ScenarioResult executeScenario(
            boolean supplyEnabled,
            boolean exhaustEnabled
    ) {
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

        if (!supplyEnabled) {
            coolingSystem.disable(
                    CoolingSimulationDemoScenario.SUPPLY_UNIT_CODE
            );
        }

        if (!exhaustEnabled) {
            coolingSystem.disable(
                    CoolingSimulationDemoScenario.EXHAUST_UNIT_CODE
            );
        }

        CoolingSnapshotTemperatureReferenceProvider
                temperatureReferenceProvider =
                new CoolingSnapshotTemperatureReferenceProvider(
                        coolingConfiguration
                );

        CoolingSnapshotCoordinator coolingCoordinator =
                new CoolingSnapshotCoordinator(
                        new DatacenterCoolingTickInputProvider(
                                datacenter
                        ),
                        coolingSystem,
                        temperatureReferenceProvider
                );

        TemperatureSystem temperatureSystem =
                new TemperatureSystem(
                        datacenter,
                        new TemperatureSystemOptions(
                                24.0,
                                25.0,
                                5_000.0,
                                8.0
                        ),
                        new SimpleServerTemperatureModel(),
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

        /*
         * Causal order:
         *
         * WorkloadSystem -> PowerConsumptionSystem
         * -> CoolingSnapshotCoordinator -> TemperatureSystem
         */
        SimulationTick tick = engine.step();

        CoolingSnapshot coolingSnapshot =
                coolingCoordinator.update(tick);

        temperatureSystem.update(tick);

        CoolingZoneSnapshot zone =
                coolingSnapshot.zones()
                        .stream()
                        .filter(candidate -> candidate.zoneCode().equals(
                                CoolingSimulationDemoScenario.ZONE_CODE
                        ))
                        .findFirst()
                        .orElseThrow();

        Server server =
                datacenter.getServers()
                        .stream()
                        .findFirst()
                        .orElseThrow();

        double serverTemperature =
                temperatureSystem
                        .getThermalState(server.getCode())
                        .getTemperatureCelsius();

        return new ScenarioResult(
                zone.inletAirTemperatureCelsius(),
                serverTemperature
        );
    }

    private record ScenarioResult(
            double inletAirTemperatureCelsius,
            double serverTemperatureCelsius
    ) {
    }
}

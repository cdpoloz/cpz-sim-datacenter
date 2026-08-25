package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSystemOptions;
import com.cpz.sim.datacenter.cooling.CoolingTickInput;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.ExhaustCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.ServerHeatLoad;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.system.CoolingSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the causal integration between server power, cooling-zone
 * temperatures and server thermal evolution.
 *
 * @author CPZ
 */
class CoolingTemperatureIntegrationTest {

    private static final double EPSILON = 0.000001;

    private static final ServerLocation SERVER_LOCATION =
            new ServerLocation(
                    "A01",
                    new RackCode("RACK-A01-R01"),
                    "U01"
            );

    @Test
    void propagatesCoolingUnitStateChangesToServerTemperature() {
        Datacenter datacenter = createDatacenter();
        Server server = datacenter.getServers().getFirst();

        PowerConsumptionSystem powerSystem =
                new PowerConsumptionSystem(datacenter);

        CoolingConfiguration coolingConfiguration =
                createCoolingConfiguration();

        CoolingSystem coolingSystem =
                new CoolingSystem(coolingConfiguration);

        CoolingSnapshotTemperatureReferenceProvider referenceProvider =
                new CoolingSnapshotTemperatureReferenceProvider(
                        coolingConfiguration
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
                        referenceProvider
                );

        /*
         * First tick:
         *
         * utilization = 0.75
         * power = 100 + 0.75 × (500 - 100) = 400 W
         *
         * SUPPLY and EXHAUST are enabled with equal airflow. The inlet-air
         * temperature uses the configured residual recirculation fraction:
         * 18 × 0.90 + 24 × 0.10 = 18.6 °C.
         */
        SimulationTick firstTick = tickAtSeconds(
                1L,
                60L,
                60L
        );

        powerSystem.update(firstTick);

        assertEquals(
                400.0,
                server.getCurrentPowerWatts(),
                EPSILON
        );

        CoolingSnapshot firstCoolingSnapshot = coolingSystem.tick(
                new CoolingTickInput(
                        firstTick.index(),
                        List.of(
                                new ServerHeatLoad(
                                        server.getLocation(),
                                        server.getCurrentPowerWatts()
                                )
                        )
                )
        );

        CoolingZoneSnapshot firstZoneSnapshot =
                firstCoolingSnapshot.zones().getFirst();

        assertTrue(coolingSystem.isEnabled("SUPPLY-01"));
        assertTrue(coolingSystem.isEnabled("EXHAUST-01"));

        assertEquals(
                400.0,
                firstZoneSnapshot.generatedHeatWatts(),
                EPSILON
        );

        assertEquals(
                4.0,
                firstZoneSnapshot.supplyAirflowCubicMetersPerSecond(),
                EPSILON
        );

        assertEquals(
                4.0,
                firstZoneSnapshot.exhaustAirflowCubicMetersPerSecond(),
                EPSILON
        );

        assertEquals(
                CoolingSystemOptions.DEFAULT_RESIDUAL_RECIRCULATION_FRACTION,
                firstZoneSnapshot.recirculationFraction(),
                EPSILON
        );

        assertEquals(
                18.6,
                firstZoneSnapshot.inletAirTemperatureCelsius(),
                EPSILON
        );

        referenceProvider.updateSnapshot(firstCoolingSnapshot);
        temperatureSystem.update(firstTick);

        /*
         * Initial server temperature = 25 °C
         * Reference temperature = 18.6 °C
         * Heat loss = 8 × (25 - 18.6) = 51.2 W
         * Net thermal power = 400 - 51.2 = 348.8 W
         * Delta = 348.8 / 5,000 × 60 = 4.1856 °C
         * Result = 29.1856 °C
         */
        assertEquals(
                29.1856,
                temperatureSystem
                        .getThermalState(server.getCode())
                        .getTemperatureCelsius(),
                EPSILON
        );

        /*
         * Second tick:
         *
         * SUPPLY is disabled at runtime. The previous zone exhaust temperature
         * becomes the next inlet reference because there is no supply airflow.
         */
        coolingSystem.disable("SUPPLY-01");

        SimulationTick secondTick = tickAtSeconds(
                2L,
                120L,
                60L
        );

        powerSystem.update(secondTick);

        CoolingSnapshot secondCoolingSnapshot = coolingSystem.tick(
                new CoolingTickInput(
                        secondTick.index(),
                        List.of(
                                new ServerHeatLoad(
                                        server.getLocation(),
                                        server.getCurrentPowerWatts()
                                )
                        )
                )
        );

        CoolingZoneSnapshot secondZoneSnapshot =
                secondCoolingSnapshot.zones().getFirst();

        assertFalse(coolingSystem.isEnabled("SUPPLY-01"));
        assertTrue(coolingSystem.isEnabled("EXHAUST-01"));

        assertEquals(
                0.0,
                secondZoneSnapshot.supplyAirflowCubicMetersPerSecond(),
                EPSILON
        );

        assertEquals(
                0.0,
                secondZoneSnapshot.availableCoolingCapacityWatts(),
                EPSILON
        );

        assertEquals(
                400.0,
                secondZoneSnapshot.coolingDeficitWatts(),
                EPSILON
        );

        assertEquals(
                18.6,
                secondZoneSnapshot.inletAirTemperatureCelsius(),
                EPSILON
        );

        referenceProvider.updateSnapshot(secondCoolingSnapshot);
        temperatureSystem.update(secondTick);

        /*
         * Previous server temperature = 29.1856 °C
         * New reference temperature = 18.6 °C
         * Heat loss = 8 × (29.1856 - 18.6) = 84.6848 W
         * Net thermal power = 400 - 84.6848 = 315.3152 W
         * Delta = 315.3152 / 5,000 × 60 = 3.7837824 °C
         * Result = 32.9693824 °C
         */
        assertEquals(
                32.9693824,
                temperatureSystem
                        .getThermalState(server.getCode())
                        .getTemperatureCelsius(),
                EPSILON
        );
    }

    private static Datacenter createDatacenter() {
        RackCode rackCode = new RackCode("RACK-A01-R01");

        Rack rack = new Rack(
                rackCode,
                new RackLocation("A01", "R01"),
                42
        );

        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );

        Server server = new Server(
                SERVER_LOCATION,
                config,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );

        server.setUtilization(0.75);

        return new Datacenter(
                List.of(rack),
                List.of(server)
        );
    }

    private static CoolingConfiguration createCoolingConfiguration() {
        CoolingZoneDefinition zone =
                new CoolingZoneDefinition(
                        "ZONE-01",
                        Set.of(SERVER_LOCATION)
                );

        SupplyCoolingUnitDefinition supply =
                new SupplyCoolingUnitDefinition(
                        "SUPPLY-01",
                        4.0,
                        12_000.0,
                        18.0,
                        List.of(
                                new CoolingZoneInfluence(
                                        zone.code(),
                                        1.0
                                )
                        ),
                        true
                );

        ExhaustCoolingUnitDefinition exhaust =
                new ExhaustCoolingUnitDefinition(
                        "EXHAUST-01",
                        4.0,
                        List.of(
                                new CoolingZoneInfluence(
                                        zone.code(),
                                        1.0
                                )
                        ),
                        true
                );

        return new CoolingConfiguration(
                List.of(zone),
                List.of(supply, exhaust),
                CoolingSystemOptions.defaults()
        );
    }

    private static SimulationTick tickAtSeconds(
            long index,
            long elapsedSeconds,
            long deltaSeconds
    ) {
        return new SimulationTick(
                index,
                Duration.ofSeconds(elapsedSeconds),
                Duration.ofSeconds(deltaSeconds)
        );
    }
}

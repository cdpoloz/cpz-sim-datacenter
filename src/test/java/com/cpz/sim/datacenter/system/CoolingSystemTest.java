package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSystemOptions;
import com.cpz.sim.datacenter.cooling.CoolingTickInput;
import com.cpz.sim.datacenter.cooling.CoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.ExhaustCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.ServerHeatLoad;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingUnitSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class CoolingSystemTest {

    private static final ServerLocation SERVER_LOCATION = new ServerLocation("C01", "R01", "S01");
    private static final ServerLocation FIRST_ZONE_SERVER_LOCATION = new ServerLocation("C01", "R01", "S01");
    private static final ServerLocation SECOND_ZONE_SERVER_LOCATION = new ServerLocation("C02", "R02", "S01");

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

    @Test
    void shouldCalculateTemperaturesWithMaximumRecirculation() {
        CoolingSystem system = new CoolingSystem(configuration());
        CoolingSnapshot snapshot = system.tick(new CoolingTickInput(12L, List.of(new ServerHeatLoad(SERVER_LOCATION, 450.0))));
        CoolingZoneSnapshot zoneSnapshot = snapshot.zones().getFirst();
        double expectedInletTemperature = (18.0 * 0.05) + (24.0 * 0.95);
        assertEquals(0.95, zoneSnapshot.recirculationFraction(), 1.0e-9);
        assertEquals(expectedInletTemperature, zoneSnapshot.inletAirTemperatureCelsius(), 1.0e-9);
        assertEquals(expectedInletTemperature, zoneSnapshot.exhaustAirTemperatureCelsius(), 1.0e-9);
    }

    private static CoolingConfiguration configuration() {
        CoolingZoneDefinition zone = new CoolingZoneDefinition("ZONE-01", Set.of(SERVER_LOCATION));
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
                        List.of(new CoolingZoneInfluence("ZONE-01", 1.0)),
                        false
                );
        return new CoolingConfiguration(List.of(zone), List.of(supply, exhaust), CoolingSystemOptions.defaults());
    }

    @Test
    void shouldRejectNullCoolingTickInput() {
        CoolingSystem system = new CoolingSystem(configuration());
        NullPointerException exception = assertThrows(NullPointerException.class, () -> system.tick(null));
        assertEquals("input must not be null", exception.getMessage());
    }

    @Test
    void shouldCreateSnapshotForCoolingTick() {
        CoolingSystem system = new CoolingSystem(configuration());
        CoolingSnapshot snapshot = system.tick(new CoolingTickInput(12L, List.of()));
        assertEquals(12L, snapshot.tickIndex());
        assertEquals(2, snapshot.units().size());
        assertEquals("SUPPLY-01", snapshot.units().get(0).unitCode());
        assertEquals("EXHAUST-01", snapshot.units().get(1).unitCode());
        assertTrue(snapshot.units().get(0).enabled());
        assertFalse(snapshot.units().get(1).enabled());
        assertEquals(1, snapshot.zones().size());
        assertEquals("ZONE-01", snapshot.zones().getFirst().zoneCode());
    }

    @Test
    void shouldCalculateCoolingResourcesAndHeatForZone() {
        CoolingSystem system = new CoolingSystem(configuration());
        CoolingSnapshot snapshot = system.tick(new CoolingTickInput(12L, List.of(new ServerHeatLoad(SERVER_LOCATION, 450.0))));
        CoolingZoneSnapshot zoneSnapshot = snapshot.zones().getFirst();
        assertEquals(450.0, zoneSnapshot.generatedHeatWatts());
        assertEquals(12_000.0, zoneSnapshot.availableCoolingCapacityWatts());
        assertEquals(450.0, zoneSnapshot.usedCoolingCapacityWatts());
        assertEquals(0.0, zoneSnapshot.coolingDeficitWatts());
        assertEquals(4.0, zoneSnapshot.supplyAirflowCubicMetersPerSecond());
        assertEquals(0.0, zoneSnapshot.exhaustAirflowCubicMetersPerSecond());
    }

    @Test
    void shouldCreateOperationalSnapshotsForEnabledAndDisabledUnits() {
        CoolingSystem system = new CoolingSystem(configuration());
        CoolingSnapshot snapshot = system.tick(new CoolingTickInput(12L, List.of()));
        CoolingUnitSnapshot supplySnapshot = snapshot.units().getFirst();
        assertEquals("SUPPLY-01", supplySnapshot.unitCode());
        assertTrue(supplySnapshot.enabled());
        assertEquals(4.0, supplySnapshot.currentAirflowCubicMetersPerSecond());
        assertEquals(12_000.0, supplySnapshot.currentCoolingPowerWatts());
        CoolingUnitSnapshot exhaustSnapshot = snapshot.units().get(1);
        assertEquals("EXHAUST-01", exhaustSnapshot.unitCode());
        assertFalse(exhaustSnapshot.enabled());
        assertEquals(0.0, exhaustSnapshot.currentAirflowCubicMetersPerSecond());
        assertEquals(0.0, exhaustSnapshot.currentCoolingPowerWatts());
    }

    @Test
    void shouldCalculateCoolingDeficitWhenHeatExceedsCapacity() {
        CoolingSystem system = new CoolingSystem(configuration());
        CoolingSnapshot snapshot = system.tick(new CoolingTickInput(12L, List.of(new ServerHeatLoad(SERVER_LOCATION, 15_000.0))));
        CoolingZoneSnapshot zoneSnapshot = snapshot.zones().getFirst();
        assertEquals(15_000.0, zoneSnapshot.generatedHeatWatts());
        assertEquals(12_000.0, zoneSnapshot.availableCoolingCapacityWatts());
        assertEquals(12_000.0, zoneSnapshot.usedCoolingCapacityWatts());
        assertEquals(3_000.0, zoneSnapshot.coolingDeficitWatts());
    }

    @Test
    void shouldSmoothTowardResidualRecirculationWhenExhaustMatchesSupplyAirflow() {
        CoolingSystem system = new CoolingSystem(configuration());
        system.enable("EXHAUST-01");

        CoolingSnapshot snapshot =
                system.tick(
                        new CoolingTickInput(
                                12L,
                                List.of(new ServerHeatLoad(SERVER_LOCATION, 450.0))
                        )
                );

        CoolingZoneSnapshot zoneSnapshot = snapshot.zones().getFirst();

        double expectedRecirculationFraction =
                expectedRecirculationAfterOneMinute(
                        CoolingSystemOptions.DEFAULT_MAXIMUM_RECIRCULATION_FRACTION,
                        CoolingSystemOptions.DEFAULT_RESIDUAL_RECIRCULATION_FRACTION
                );

        double expectedInletTemperature =
                (18.0 * (1.0 - expectedRecirculationFraction))
                        + (24.0 * expectedRecirculationFraction);

        assertEquals(4.0, zoneSnapshot.supplyAirflowCubicMetersPerSecond());
        assertEquals(4.0, zoneSnapshot.exhaustAirflowCubicMetersPerSecond());
        assertEquals(expectedRecirculationFraction, zoneSnapshot.recirculationFraction(), 1.0e-9);
        assertEquals(expectedInletTemperature, zoneSnapshot.inletAirTemperatureCelsius(), 1.0e-9);
        assertEquals(expectedInletTemperature, zoneSnapshot.exhaustAirTemperatureCelsius(), 1.0e-9);
    }

    @Test
    void shouldCalculateIntermediateRecirculationWhenAirflowIsPartiallyUnbalanced() {
        CoolingZoneDefinition zone = new CoolingZoneDefinition("ZONE-01", Set.of(SERVER_LOCATION));
        SupplyCoolingUnitDefinition supply =
                new SupplyCoolingUnitDefinition(
                        "SUPPLY-01",
                        10.0,
                        12_000.0,
                        18.0,
                        List.of(new CoolingZoneInfluence("ZONE-01", 1.0)),
                        true
                );
        ExhaustCoolingUnitDefinition exhaust =
                new ExhaustCoolingUnitDefinition(
                        "EXHAUST-01",
                        5.0,
                        List.of(new CoolingZoneInfluence("ZONE-01", 1.0)),
                        true
                );
        CoolingConfiguration configuration =
                new CoolingConfiguration(
                        List.of(zone),
                        List.of(supply, exhaust),
                        CoolingSystemOptions.defaults()
                );
        CoolingSystem system = new CoolingSystem(configuration);

        CoolingSnapshot snapshot = system.tick(new CoolingTickInput(12L, List.of(new ServerHeatLoad(SERVER_LOCATION, 450.0))));
        CoolingZoneSnapshot zoneSnapshot = snapshot.zones().getFirst();

        double expected =
                CoolingSystemOptions.DEFAULT_RESIDUAL_RECIRCULATION_FRACTION
                        + (5.0 / 15.0)
                        * (CoolingSystemOptions.DEFAULT_MAXIMUM_RECIRCULATION_FRACTION
                        - CoolingSystemOptions.DEFAULT_RESIDUAL_RECIRCULATION_FRACTION);

        assertEquals(10.0, zoneSnapshot.supplyAirflowCubicMetersPerSecond(), 1.0e-9);
        assertEquals(5.0, zoneSnapshot.exhaustAirflowCubicMetersPerSecond(), 1.0e-9);
        assertEquals(expected, zoneSnapshot.recirculationFraction(), 1.0e-9);
    }

    @Test
    void shouldAccumulateZoneAirTemperatureWhenZoneHasNoAirflow() {
        CoolingSystem system = new CoolingSystem(configuration());
        system.disable("SUPPLY-01");

        CoolingSnapshot firstSnapshot =
                system.tick(
                        new CoolingTickInput(
                                12L,
                                List.of(new ServerHeatLoad(SERVER_LOCATION, 450.0))
                        )
                );

        CoolingZoneSnapshot zoneSnapshot = firstSnapshot.zones().getFirst();

        double expectedRise =
                (450.0 * 60.0)
                        / (1_210.02 * 1_000.0);

        assertEquals(0.0, zoneSnapshot.availableCoolingCapacityWatts());
        assertEquals(0.0, zoneSnapshot.usedCoolingCapacityWatts());
        assertEquals(450.0, zoneSnapshot.coolingDeficitWatts());
        assertEquals(0.0, zoneSnapshot.supplyAirflowCubicMetersPerSecond());
        assertEquals(0.95, zoneSnapshot.recirculationFraction(), 1.0e-9);
        assertEquals(24.0, zoneSnapshot.inletAirTemperatureCelsius(), 1.0e-9);
        assertEquals(24.0 + expectedRise, zoneSnapshot.exhaustAirTemperatureCelsius(), 1.0e-9);

        CoolingSnapshot secondSnapshot =
                system.tick(
                        new CoolingTickInput(
                                13L,
                                List.of(new ServerHeatLoad(SERVER_LOCATION, 450.0))
                        )
                );

        CoolingZoneSnapshot secondZoneSnapshot = secondSnapshot.zones().getFirst();

        assertEquals(24.0 + expectedRise, secondZoneSnapshot.inletAirTemperatureCelsius(), 1.0e-9);
        assertEquals(24.0 + (expectedRise * 2.0), secondZoneSnapshot.exhaustAirTemperatureCelsius(), 1.0e-9);
    }

    @Test
    void shouldRejectServerLocationNotAssignedToCoolingZone() {
        CoolingSystem system = new CoolingSystem(configuration());
        ServerLocation unknownLocation = new ServerLocation("C99", "R99", "S99");
        CoolingTickInput input = new CoolingTickInput(12L, List.of(new ServerHeatLoad(unknownLocation, 450.0)));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> system.tick(input));
        assertEquals("server location is not assigned to a cooling zone: " + unknownLocation, exception.getMessage());
    }

    @Test
    void shouldRejectDuplicateServerHeatLoads() {
        CoolingSystem system = new CoolingSystem(configuration());
        CoolingTickInput input = new CoolingTickInput(
                12L,
                List.of(new ServerHeatLoad(SERVER_LOCATION, 300.0),
                        new ServerHeatLoad(SERVER_LOCATION, 150.0)
                )
        );
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> system.tick(input));
        assertEquals("cooling input must not contain duplicate server locations: " + SERVER_LOCATION, exception.getMessage());
    }

    @Test
    void shouldDistributeCoolingResourcesAcrossMultipleZones() {
        CoolingSystem system = new CoolingSystem(multiZoneConfiguration());
        CoolingSnapshot snapshot = system.tick(new CoolingTickInput(20L, List.of()));
        assertEquals(2, snapshot.zones().size());
        CoolingZoneSnapshot firstZone = snapshot.zones().getFirst();
        assertEquals("ZONE-01", firstZone.zoneCode());
        /*
         * SUPPLY-01:
         * airflow = 4.0 × 0.75 = 3.0 m³/s
         * capacity = 12,000 × 0.75 = 9,000 W
         *
         * SUPPLY-02:
         * airflow = 2.0 × 0.50 = 1.0 m³/s
         * capacity = 6,000 × 0.50 = 3,000 W
         */
        assertEquals((19.0 * 0.05) + (24.0 * 0.95), firstZone.inletAirTemperatureCelsius(), 1.0e-9);
        assertEquals((19.0 * 0.05) + (24.0 * 0.95), firstZone.exhaustAirTemperatureCelsius(), 1.0e-9);
        /*
         * Temperatura ponderada:
         * (18 °C × 3 m³/s + 22 °C × 1 m³/s) / 4 m³/s
         * = 19 °C
         */
        CoolingZoneSnapshot secondZone = snapshot.zones().get(1);
        assertEquals("ZONE-02", secondZone.zoneCode());
        /*
         * SUPPLY-01:
         * airflow = 4.0 × 0.25 = 1.0 m³/s
         * capacity = 12,000 × 0.25 = 3,000 W
         *
         * SUPPLY-02:
         * airflow = 2.0 × 0.50 = 1.0 m³/s
         * capacity = 6,000 × 0.50 = 3,000 W
         */
        assertEquals((20.0 * 0.05) + (24.0 * 0.95), secondZone.inletAirTemperatureCelsius(), 1.0e-9);
        assertEquals((20.0 * 0.05) + (24.0 * 0.95), secondZone.exhaustAirTemperatureCelsius(), 1.0e-9);
        /*
         * Temperatura ponderada:
         * (18 °C × 1 m³/s + 22 °C × 1 m³/s) / 2 m³/s
         * = 20 °C
         */
    }

    private static CoolingConfiguration multiZoneConfiguration() {
        CoolingZoneDefinition firstZone = new CoolingZoneDefinition("ZONE-01", Set.of(FIRST_ZONE_SERVER_LOCATION));
        CoolingZoneDefinition secondZone = new CoolingZoneDefinition("ZONE-02", Set.of(SECOND_ZONE_SERVER_LOCATION));
        SupplyCoolingUnitDefinition firstSupply =
                new SupplyCoolingUnitDefinition(
                        "SUPPLY-01",
                        4.0,
                        12_000.0,
                        18.0,
                        List.of(
                                new CoolingZoneInfluence("ZONE-01", 0.75),
                                new CoolingZoneInfluence("ZONE-02", 0.25)
                        ),
                        true
                );
        SupplyCoolingUnitDefinition secondSupply =
                new SupplyCoolingUnitDefinition(
                        "SUPPLY-02",
                        2.0,
                        6_000.0,
                        22.0,
                        List.of(
                                new CoolingZoneInfluence("ZONE-01", 0.50),
                                new CoolingZoneInfluence("ZONE-02", 0.50)
                        ),
                        true
                );
        return new CoolingConfiguration(List.of(firstZone, secondZone), List.of(firstSupply, secondSupply), CoolingSystemOptions.defaults());
    }

    @Test
    void shouldAggregateHeatIndependentlyForMultipleZones() {
        CoolingSystem system = new CoolingSystem(multiZoneConfiguration());
        CoolingSnapshot snapshot = system.tick(
                new CoolingTickInput(
                        21L,
                        List.of(new ServerHeatLoad(FIRST_ZONE_SERVER_LOCATION, 2_000.0),
                                new ServerHeatLoad(SECOND_ZONE_SERVER_LOCATION, 8_000.0)
                        )
                )
        );
        CoolingZoneSnapshot firstZone = snapshot.zones().getFirst();
        assertEquals("ZONE-01", firstZone.zoneCode());
        assertEquals(2_000.0, firstZone.generatedHeatWatts());
        assertEquals(2_000.0, firstZone.usedCoolingCapacityWatts());
        assertEquals(0.0, firstZone.coolingDeficitWatts());
        CoolingZoneSnapshot secondZone = snapshot.zones().get(1);
        assertEquals("ZONE-02", secondZone.zoneCode());
        assertEquals(8_000.0, secondZone.generatedHeatWatts());
        assertEquals(6_000.0, secondZone.usedCoolingCapacityWatts());
        assertEquals(2_000.0, secondZone.coolingDeficitWatts());
    }

    @Test
    void shouldResetUnitsToTheirInitiallyEnabledStates() {
        CoolingSystem system = new CoolingSystem(configuration());
        system.disable("SUPPLY-01");
        system.enable("EXHAUST-01");
        assertFalse(system.isEnabled("SUPPLY-01"));
        assertTrue(system.isEnabled("EXHAUST-01"));
        system.reset();
        assertTrue(system.isEnabled("SUPPLY-01"));
        assertFalse(system.isEnabled("EXHAUST-01"));
    }

    private static double expectedRecirculationAfterOneMinute(
            double previousRecirculation,
            double targetRecirculation
    ) {
        double smoothingFactor =
                1.0 - Math.exp(
                        -60.0
                                / CoolingSystemOptions.DEFAULT_RECIRCULATION_RESPONSE_TIME_SECONDS
                );

        return previousRecirculation
                + (targetRecirculation - previousRecirculation)
                * smoothingFactor;
    }
}

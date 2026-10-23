package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.cooling.*;
import com.cpz.sim.datacenter.factory.*;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.datacenter.history.*;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.*;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RackCoolingHistoryIntegrationTest {
    private record Fixture(Datacenter dc, CoupledRoomTemperatureSystem coupled,
                           EnergyConsumptionSystem energy, ServerHealthSystem health,
                           DatacenterOperationalSnapshotProvider operations,
                           EnergyConsumptionSnapshotProvider energyProvider, TemperatureSnapshotProvider temperatures,
                           HealthSnapshotProvider healthProvider, DatacenterSimulationHistoryRecorder recorder) {
        SimulationTick advance(long index) {
            var tick = new SimulationTick(index, Duration.ofSeconds(index * 60), Duration.ofSeconds(60));
            coupled.update(tick); health.update(tick); energy.update(tick);
            return tick;
        }
        RackCoolingSystem controls() { return coupled.rackCoolingSystem().orElseThrow(); }
    }

    private static Fixture fixture(boolean roomCooling, boolean secondRack, HeatRejectionDestination destination) {
        var influences = List.of(new CoolingZoneInfluenceConfigDefinition("Z", 1));
        var units = new java.util.ArrayList<RackCoolingUnitConfigDefinition>();
        units.add(new RackCoolingUnitConfigDefinition("RC1", "C01", "R01", 30, 10000, 40,
                0.1, 2000, 18, 500, destination, true));
        if (secondRack) units.add(new RackCoolingUnitConfigDefinition("RC2", "C02", "R01", 26, 15000, 30,
                0.1, 2000, 18, 800, HeatRejectionDestination.EXTERNAL, true));
        var definition = new DatacenterDefinition("History integration", new DatacenterLayoutDefinition(List.of(
                new RackDefinition("R01", "C01", "R01", List.of("S01")),
                new RackDefinition("R01", "C02", "R01", List.of("S01")))),
                List.of(new ServerModelDefinition("M", "CPZ", "Test", 100, 300)),
                List.of(new ServerDefinition("C01", "R01", "S01", "M", "OK", ServerRole.GENERAL_PURPOSE),
                        new ServerDefinition("C02", "R01", "S01", "M", "OK", ServerRole.GENERAL_PURPOSE)),
                new TemperatureSystemOptionsDefinition(24, 24, 5000, 8), null,
                roomCooling ? new CoolingConfigDefinition(
                        List.of(new CoolingZoneConfigDefinition("Z", List.of("C01", "C02"), List.of("R01"))),
                        List.of(new SupplyCoolingUnitConfigDefinition("SUP", 1, 400, 1000, 18, influences, true)),
                        List.of(new ExhaustCoolingUnitConfigDefinition("EXH", 1, 200, influences, true)),
                        new CoolingSystemOptionsDefinition(1.204, 1005, 24, 0.95, 0.1, 1000, 300)) : null,
                null, null, null, new RoomAirTemperatureOptionsDefinition(24, 2000000, 24, 0),
                new RackCoolingConfigDefinition(1.204, 1005, units));
        var dc = new DatacenterFactory().create(definition);
        dc.getServers().forEach(server -> server.setCurrentPowerWatts(200));
        var options = new TemperatureSystemOptionsFactory().create(definition);
        var temps = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel());
        var room = new CoolingConfigurationFactory().create(definition, dc).map(CoolingSystem::new);
        var coupled = new CoupledRoomTemperatureSystemFactory().create(definition, dc, temps, room).orElseThrow();
        var energy = new EnergyConsumptionSystem(dc);
        var health = new ServerHealthSystem(dc, temps, ServerHealthOptions.defaults());
        var energyProvider = new EnergyConsumptionSnapshotProvider(dc, energy);
        var temperatureProvider = new TemperatureSnapshotProvider(dc, temps, options);
        var healthProvider = new HealthSnapshotProvider(dc, health, temps);
        var operations = new DatacenterOperationalSnapshotProvider(dc);
        var recorder = new DatacenterSimulationHistoryRecorder(energyProvider, temperatureProvider, healthProvider,
                operations, coupled::lastCoolingSnapshot, new DatacenterSimulationHistory(), Optional.empty(), coupled::lastSnapshot);
        return new Fixture(dc, coupled, energy, health, operations, energyProvider, temperatureProvider, healthProvider, recorder);
    }

    @ParameterizedTest
    @EnumSource(HeatRejectionDestination.class)
    void includesRoomAndRackElectricityOnceRegardlessOfHeatDestination(HeatRejectionDestination destination) {
        var f = fixture(true, false, destination);
        var step = f.recorder.record(f.advance(1));
        var op = step.operationalSnapshot();
        assertEquals(400, op.currentItPowerWatts());
        assertEquals(1100, op.coolingPowerWatts());
        assertEquals(1500, op.totalFacilityPowerWatts());
        assertEquals(3.75, op.pue());
        assertEquals(600, step.coolingSnapshot().orElseThrow().totalElectricalPowerWatts());
        assertEquals(500, step.rackCoolingSnapshot().orElseThrow().totalElectricalPowerWatts());
        var electricity = step.facilityEnergySnapshot().orElseThrow();
        assertEquals(400.0 / 60, electricity.consumedItEnergyWh(), 1e-10);
        assertEquals(10, electricity.consumedRoomCoolingEnergyWh(), 1e-10);
        assertEquals(500.0 / 60, electricity.consumedRackCoolingEnergyWh(), 1e-10);
        assertEquals(25, op.consumedFacilityEnergyWh(), 1e-10);
        assertEquals(1100.0 / 60, op.consumedCoolingEnergyWh(), 1e-10);
        assertEquals(3.75, electricity.energyPue(), 1e-10);
        // The legacy energy snapshot retains IT-only semantics.
        assertEquals(400.0 / 60, step.energySnapshot().consumedEnergyWh(), 1e-10);
        assertSame(step.rackCoolingSnapshot().orElseThrow(), f.coupled.lastRackCoolingSnapshot().orElseThrow());
        assertEquals(0, step.coupledThermalSnapshot().orElseThrow().energyBalanceResidualJoules(), 2e-5);
    }

    @Test
    void capturesLocalRackAirActualHeatAndPerUnitIntegratedTransfers() {
        var f = fixture(false, true, HeatRejectionDestination.ROOM);
        var step = f.recorder.record(f.advance(1));
        assertTrue(step.coolingSnapshot().isEmpty());
        assertFalse(step.hasCoolingSnapshot());
        assertTrue(step.hasRackCoolingSnapshot());
        assertTrue(step.hasAnyCoolingSnapshot());
        assertEquals(1300, step.operationalSnapshot().coolingPowerWatts());
        assertEquals(1700, step.operationalSnapshot().totalFacilityPowerWatts());
        var racks = step.rackCoolingSnapshot().orElseThrow();
        var first = racks.findRack(new RackLocation("C01", "R01")).orElseThrow();
        var second = racks.findRack(new RackLocation("C02", "R01")).orElseThrow();
        assertNotEquals(first.unitCode(), second.unitCode());
        assertEquals(first, racks.findUnit("RC1").orElseThrow());
        assertEquals(f.coupled.rackAirTemperatureCelsius(first.rackLocation()).orElseThrow(), first.airTemperatureCelsius());
        assertNotEquals(step.operationalSnapshot().averageOnlineServerTemperatureCelsius(), first.airTemperatureCelsius());
        assertTrue(first.actualCoolingPowerWatts() <= first.equipment().currentCoolingPowerWatts());
        assertEquals(first.actualCoolingPowerWatts() + 500, first.heatRejectionPowerWatts());
        var ledger = step.coupledThermalSnapshot().orElseThrow();
        assertEquals(first.electricalEnergyJoules() + second.electricalEnergyJoules(), ledger.rackCoolingElectricalEnergyJoules(), 1e-8);
        assertEquals(first.removedHeatJoules() + second.removedHeatJoules(), ledger.rackRemovedHeatJoules(), 1e-8);
        assertEquals(first.rejectedHeatJoules(), ledger.rackHeatRejectedToRoomJoules(), 1e-8);
        assertEquals(second.rejectedHeatJoules(), ledger.rackHeatRejectedExternalJoules(), 1e-8);
        for (var rack : racks.units()) assertEquals(0, rack.localEnergyBalanceResidualJoules(), 2e-5);
        assertThrows(UnsupportedOperationException.class, () -> racks.units().clear());
    }

    @Test
    void commandsAfterCompletedTickDoNotRewriteCaptureAndDisabledUnitsStillExchangePassiveHeat() {
        var f = fixture(false, false, HeatRejectionDestination.ROOM);
        var tick = f.advance(1);
        var capture = f.coupled.lastRackCoolingSnapshot().orElseThrow();
        f.controls().disable("RC1");
        var first = f.recorder.record(tick);
        assertEquals(capture, first.rackCoolingSnapshot().orElseThrow());
        assertEquals(500, first.operationalSnapshot().coolingPowerWatts());
        var second = f.recorder.record(f.advance(2));
        var rack = second.rackCoolingSnapshot().orElseThrow().units().getFirst();
        assertEquals(0, rack.currentElectricalPowerWatts());
        assertEquals(0, rack.actualCoolingPowerWatts());
        assertEquals(0, rack.electricalEnergyJoules());
        assertEquals(0, rack.rejectedHeatJoules());
        assertNotEquals(0, rack.heatTransferredToRoomJoules());
        assertEquals(0, second.operationalSnapshot().coolingPowerWatts());
        assertEquals(1, second.operationalSnapshot().pue());
        assertEquals(500.0 / 60, second.facilityEnergySnapshot().orElseThrow().consumedRackCoolingEnergyWh(), 1e-10);
        assertEquals(500, first.rackCoolingSnapshot().orElseThrow().totalElectricalPowerWatts());
    }

    @Test
    void allOfflineKeepsCoolingConsumptionButInstantaneousPueIsUnavailable() {
        var f = fixture(false, false, HeatRejectionDestination.EXTERNAL);
        f.dc.getServers().forEach(server -> { server.setStatus(HardwareStatus.OFFLINE); server.updatePowerConsumption(); });
        var step = f.recorder.record(f.advance(1));
        assertEquals(0, step.operationalSnapshot().currentItPowerWatts());
        assertEquals(500, step.operationalSnapshot().totalFacilityPowerWatts());
        assertTrue(Double.isNaN(step.operationalSnapshot().pue()));
        var electricity = step.facilityEnergySnapshot().orElseThrow();
        assertTrue(Double.isNaN(electricity.energyPue()));
        assertEquals(500.0 / 60, electricity.consumedFacilityEnergyWh(), 1e-10);
    }

    @Test
    void energyAccumulatesWithoutRecordingAndReadsOrRejectedRecordsDoNotAddEnergy() {
        var f = fixture(false, false, HeatRejectionDestination.ROOM);
        f.advance(1); f.advance(2);
        var tick = f.advance(3);
        var energy = f.coupled.lastFacilityEnergySnapshot().orElseThrow();
        assertEquals(45, energy.consumedFacilityEnergyWh(), 1e-10);
        var step = f.recorder.record(tick);
        assertEquals(45, step.operationalSnapshot().consumedFacilityEnergyWh(), 1e-10);
        assertThrows(IllegalArgumentException.class, () -> f.recorder.record(tick));
        assertEquals(1, f.recorder.history().size());
        assertSame(energy, f.coupled.lastFacilityEnergySnapshot().orElseThrow());
        f.recorder.clear();
        assertSame(energy, f.coupled.lastFacilityEnergySnapshot().orElseThrow());
        f.recorder.record(tick);
        assertEquals(45, f.recorder.history().latest().orElseThrow().operationalSnapshot().consumedFacilityEnergyWh(), 1e-10);
    }

    @Test
    void thermalAndItEnergyResetIntervalsMustMatchAndResetReplayIsStable() {
        var f = fixture(false, false, HeatRejectionDestination.ROOM);
        var first = f.recorder.record(f.advance(1));
        f.coupled.reset(); f.recorder.clear();
        assertTrue(f.coupled.lastFacilityEnergySnapshot().isEmpty());
        assertTrue(f.coupled.lastRackCoolingSnapshot().isEmpty());
        var tick = f.advance(1); // IT energy was deliberately not reset.
        assertThrows(IllegalArgumentException.class, () -> f.recorder.record(tick));
        assertTrue(f.recorder.history().isEmpty());
        f.coupled.reset(); f.energy.reset(); f.recorder.clear();
        var replay = f.recorder.record(f.advance(1));
        assertEquals(first, replay);
    }

    @Test
    void failedThermalStepDoesNotAccumulateElectricalEnergy() {
        var f = fixture(false, false, HeatRejectionDestination.ROOM);
        f.advance(1);
        var before = f.coupled.lastFacilityEnergySnapshot().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> f.coupled.update(new SimulationTick(2,
                Duration.ofSeconds(120), Duration.ofSeconds(86401))));
        assertSame(before, f.coupled.lastFacilityEnergySnapshot().orElseThrow());
        f.advance(2);
        assertEquals(30, f.coupled.lastFacilityEnergySnapshot().orElseThrow().consumedFacilityEnergyWh(), 1e-10);
    }

    @Test
    void rejectsStaleRackAndDuplicateRoomRackEquipmentBeforeRecording() {
        var f = fixture(true, false, HeatRejectionDestination.ROOM);
        var tick = f.advance(1);
        var stale = f.coupled.lastRackCoolingSnapshot().orElseThrow();
        var next = f.advance(2);
        assertThrows(IllegalArgumentException.class, () -> f.operations.snapshot(f.energyProvider.snapshot(next),
                f.temperatures.snapshot(next), f.healthProvider.snapshot(next), f.coupled.lastCoolingSnapshot(),
                f.coupled.lastSnapshot().map(CoupledRoomThermalSnapshot::roomAir), Optional.of(stale), Optional.empty()));
        var rack = f.coupled.lastRackCoolingSnapshot().orElseThrow();
        var room = f.coupled.lastCoolingSnapshot().orElseThrow();
        var duplicated = new CoolingSnapshot(next.index(), List.of(rack.units().getFirst().equipment()), room.zones());
        assertThrows(IllegalArgumentException.class, () -> f.operations.snapshot(f.energyProvider.snapshot(next),
                f.temperatures.snapshot(next), f.healthProvider.snapshot(next), Optional.of(duplicated),
                f.coupled.lastSnapshot().map(CoupledRoomThermalSnapshot::roomAir), Optional.of(rack), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> f.recorder.record(tick));
        assertTrue(f.recorder.history().isEmpty());
    }

    @Test
    void fractionalServerPowersKeepItMeterAndFacilityLedgerCoherent() {
        var f = fixture(true, false, HeatRejectionDestination.ROOM);
        var servers = List.copyOf(f.dc.getServers());
        servers.get(0).setCurrentPowerWatts(200.01f);
        servers.get(1).setCurrentPowerWatts(201.02f);
        double expectedPower = (double) 200.01f + (double) 201.02f;
        var step = f.recorder.record(f.advance(1));
        assertEquals(expectedPower, step.energySnapshot().totalItPowerWatts(), 1e-10);
        assertEquals(expectedPower, step.operationalSnapshot().currentItPowerWatts(), 1e-10);
        assertEquals(expectedPower / 60, step.energySnapshot().consumedEnergyWh(), 1e-10);
        assertEquals(step.energySnapshot().consumedEnergyWh(),
                step.facilityEnergySnapshot().orElseThrow().consumedItEnergyWh(), 1e-10);
    }
}

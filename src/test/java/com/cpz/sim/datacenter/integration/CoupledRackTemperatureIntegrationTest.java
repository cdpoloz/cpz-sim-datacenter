package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.cooling.*;
import com.cpz.sim.datacenter.factory.*;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.CoupledRoomThermalSnapshot;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.*;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoupledRackTemperatureIntegrationTest {
    private static final RackLocation R1 = new RackLocation("C01", "R01");
    private static final RackLocation R2 = new RackLocation("C02", "R01");
    private static final double EPS = 2e-5;

    private record Fixture(DatacenterDefinition definition, Datacenter datacenter,
                           TemperatureSystem temperatures, TemperatureSystemOptions options,
                           CoupledRoomTemperatureSystem coupled) {
        double local(RackLocation rack) { return coupled.rackAirTemperatureCelsius(rack).orElseThrow(); }
        void power(double value) { datacenter.getServers().forEach(s -> s.setCurrentPowerWatts(value)); }
        double server(int index) { return temperatures.getThermalState(datacenter.getServers().get(index).getCode()).getTemperatureCelsius(); }
    }

    private static RackCoolingUnitConfigDefinition unit(String code, String column,
            HeatRejectionDestination destination, boolean enabled, double initial, double g, double capacity) {
        return new RackCoolingUnitConfigDefinition(code, column, "R01", initial, 10000, g,
                0.1, capacity, 18, 500, destination, enabled);
    }

    private static Fixture fixture(List<RackCoolingUnitConfigDefinition> units, int servers) {
        var allServers = List.of(
                new ServerDefinition("C01", "R01", "S01", "M", "OK", ServerRole.GENERAL_PURPOSE),
                new ServerDefinition("C02", "R01", "S01", "M", "OK", ServerRole.GENERAL_PURPOSE),
                new ServerDefinition("C03", "R01", "S01", "M", "OK", ServerRole.GENERAL_PURPOSE));
        var def = new DatacenterDefinition("Local rack test", new DatacenterLayoutDefinition(List.of(
                new RackDefinition("R01", "C01", "R01", List.of("S01")),
                new RackDefinition("R01", "C02", "R01", List.of("S01")),
                new RackDefinition("R01", "C03", "R01", List.of("S01")))),
                List.of(new ServerModelDefinition("M", "CPZ", "Test", 100, 300)),
                allServers.subList(0, servers), new TemperatureSystemOptionsDefinition(24, 24, 5000, 8),
                null, null, DatacenterDataInputMode.POWER_DRIVEN, null, null,
                new RoomAirTemperatureOptionsDefinition(24, 2000000, 24, 0),
                new RackCoolingConfigDefinition(1.204, 1005, units));
        var datacenter = new DatacenterFactory().create(def);
        var options = new TemperatureSystemOptionsFactory().create(def);
        var temperatures = new TemperatureSystem(datacenter, options, new SimpleServerTemperatureModel());
        var coupled = new CoupledRoomTemperatureSystemFactory().create(def, datacenter, temperatures, Optional.empty()).orElseThrow();
        return new Fixture(def, datacenter, temperatures, options, coupled);
    }

    private static SimulationTick tick(long index, double seconds) {
        long nanos = Math.round(seconds * 1e9);
        return new SimulationTick(index, Duration.ofNanos(index * nanos), Duration.ofNanos(nanos));
    }

    private static CoupledRoomThermalSnapshot advance(Fixture f, long index, double seconds) {
        f.coupled.update(tick(index, seconds));
        var ledger = f.coupled.lastSnapshot().orElseThrow();
        assertEquals(0, ledger.energyBalanceResidualJoules(), EPS);
        assertEquals(ledger.inputElectricalEnergyJoules() - ledger.serverEnergyChangeJoules(),
                ledger.heatTransferredToAirJoules(), EPS);
        assertEquals(ledger.rackRemovedHeatJoules() + ledger.rackCoolingElectricalEnergyJoules(),
                ledger.rackHeatRejectedToRoomJoules() + ledger.rackHeatRejectedExternalJoules(), EPS);
        return ledger;
    }

    @Test
    void roomAndExternalRejectDifferentHeatWithoutChangingAnIsolatedRack() {
        var room = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.ROOM, true, 30, 0, 2000)), 1);
        var external = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.EXTERNAL, true, 30, 0, 2000)), 1);
        room.power(300); external.power(300);
        var a = advance(room, 1, 60);
        var b = advance(external, 1, 60);
        assertEquals(room.local(R1), external.local(R1), 1e-10);
        assertEquals(room.server(0), external.server(0), 1e-10);
        assertEquals(24, external.coupled.roomAirTemperatureCelsius(), 1e-10);
        assertTrue(room.coupled.roomAirTemperatureCelsius() > 24);
        assertEquals(a.rackHeatRejectedToRoomJoules(), a.roomAirEnergyChangeJoules(), EPS);
        assertEquals(a.rackRemovedHeatJoules() + 30000, b.rackHeatRejectedExternalJoules(), EPS);
        assertEquals(0, a.rackHeatRejectedExternalJoules());
        assertEquals(0, b.rackHeatRejectedToRoomJoules());
    }

    @Test
    void coolsOnlyItsOwnAirNodeAndRetainsDirectRoomServers() {
        var f = fixture(List.of(
                unit("RC1", "C01", HeatRejectionDestination.EXTERNAL, true, 24, 50, 2000),
                unit("RC2", "C02", HeatRejectionDestination.EXTERNAL, false, 24, 50, 2000)), 3);
        f.power(300);
        for (int i = 1; i <= 10; i++) advance(f, i, 60);
        assertTrue(f.local(R1) < f.local(R2));
        assertTrue(f.server(0) < f.server(1));
        assertTrue(f.server(0) < f.server(2));
        assertTrue(f.coupled.rackAirTemperatureCelsius(new RackLocation("C03", "R01")).isEmpty());
        assertEquals(2, f.coupled.rackAirTemperaturesCelsius().size());
        assertEquals(30000, f.coupled.lastSnapshot().orElseThrow().rackCoolingElectricalEnergyJoules());
    }

    @ParameterizedTest
    @ValueSource(doubles = {10, 2000})
    void emptyIsolatedRackMatchesIndependentBackwardEulerSolution(double capacity) {
        var f = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.EXTERNAL, true, 30, 0, capacity)), 0);
        var ledger = advance(f, 1, 1);
        double k = 1.204 * 1005 * 0.1;
        double expected = capacity == 10 ? 30 - 10.0 / 10000 : (10000 * 30 + k * 18) / (10000 + k);
        assertEquals(expected, f.local(R1), 1e-12);
        assertEquals(Math.min(capacity, k * (expected - 18)), ledger.rackRemovedHeatJoules(), 1e-8);
        assertEquals(0, ledger.inputElectricalEnergyJoules());
        assertEquals(24, f.coupled.roomAirTemperatureCelsius());
    }

    @Test
    void enabledUnitBelowSupplyDoesNotHeatRackButStillConsumesElectricity() {
        var f = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.ROOM, true, 10, 0, 2000)), 0);
        var ledger = advance(f, 1, 60);
        assertEquals(10, f.local(R1));
        assertEquals(0, ledger.rackRemovedHeatJoules());
        assertEquals(30000, ledger.rackHeatRejectedToRoomJoules());
        assertEquals(24.015, f.coupled.roomAirTemperatureCelsius(), 1e-10);
    }

    @ParameterizedTest
    @ValueSource(doubles = {10, 40})
    void disabledUnitPreservesSignedPassiveExchange(double localInitial) {
        var f = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.ROOM, false, localInitial, 50, 2000)), 0);
        var ledger = advance(f, 1, 60);
        assertEquals(0, ledger.rackRemovedHeatJoules());
        assertEquals(0, ledger.rackCoolingElectricalEnergyJoules());
        assertEquals(0, ledger.rackHeatRejectedToRoomJoules());
        assertEquals(Math.signum(localInitial - 24), Math.signum(ledger.rackHeatTransferredToRoomJoules()));
        assertEquals(ledger.rackHeatTransferredToRoomJoules(), ledger.roomAirEnergyChangeJoules(), EPS);
    }

    @Test
    void offlineServerReleasesStoredHeatOnlyIntoItsRackAir() {
        var f = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.EXTERNAL, false, 24, 0, 2000)), 1);
        var server = f.datacenter.getServers().getFirst();
        server.setStatus(HardwareStatus.OFFLINE);
        server.updatePowerConsumption();
        f.temperatures.setTemperatureCelsius(server.getCode(), 50);
        var ledger = advance(f, 1, 60);
        assertEquals(0, ledger.inputElectricalEnergyJoules());
        assertTrue(f.local(R1) > 24);
        assertTrue(f.server(0) < 50);
        assertEquals(24, f.coupled.roomAirTemperatureCelsius());
        assertEquals(-ledger.serverEnergyChangeJoules(), ledger.rackAirEnergyChangeJoules(), EPS);
    }

    @Test
    void tickSubdivisionAndResetReplayAreDeterministic() {
        var units = List.of(unit("RC1", "C01", HeatRejectionDestination.ROOM, true, 30, 50, 2000));
        var large = fixture(units, 3);
        var small = fixture(units, 3);
        large.power(300); small.power(300);
        advance(large, 1, 60);
        for (int i = 1; i <= 60; i++) advance(small, i, 1);
        assertEquals(large.local(R1), small.local(R1), 1e-10);
        assertEquals(large.coupled.roomAirTemperatureCelsius(), small.coupled.roomAirTemperatureCelsius(), 1e-10);
        for (int i = 0; i < 3; i++) assertEquals(large.server(i), small.server(i), 1e-10);
        var first = large.coupled.lastSnapshot().orElseThrow();
        large.coupled.rackCoolingSystem().orElseThrow().disable("RC1");
        large.coupled.reset();
        assertEquals(30, large.local(R1));
        assertEquals(24, large.server(0));
        assertTrue(large.coupled.rackCoolingSystem().orElseThrow().isEnabled("RC1"));
        assertTrue(large.coupled.lastSnapshot().isEmpty());
        advance(large, 1, 60);
        assertEquals(first, large.coupled.lastSnapshot().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 1, 60, 600})
    void conservesEnergyForMixedDestinationsAndTickSizes(double seconds) {
        var f = fixture(List.of(
                unit("RC1", "C01", HeatRejectionDestination.ROOM, true, 30, 50, 2000),
                unit("RC2", "C02", HeatRejectionDestination.EXTERNAL, true, 20, 30, 100)), 3);
        f.power(300);
        for (int i = 1; i <= 4; i++) advance(f, i, seconds);
        assertTrue(f.coupled.lastSnapshot().orElseThrow().rackHeatRejectedToRoomJoules() > 0);
        assertTrue(f.coupled.lastSnapshot().orElseThrow().rackHeatRejectedExternalJoules() > 0);
    }

    @Test
    void rejectsRepeatedTicksWithoutChangingPublishedStatesOrCaptures() {
        var f = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.ROOM, true, 30, 50, 2000)), 1);
        advance(f, 1, 60);
        var before = f.coupled.lastSnapshot().orElseThrow();
        var air = f.coupled.rackAirTemperaturesCelsius();
        double server = f.server(0);
        assertThrows(IllegalArgumentException.class, () -> f.coupled.update(tick(1, 60)));
        assertEquals(before, f.coupled.lastSnapshot().orElseThrow());
        assertSame(air, f.coupled.rackAirTemperaturesCelsius());
        assertEquals(server, f.server(0));
        assertThrows(UnsupportedOperationException.class, () -> air.clear());
    }

    @Test
    void lateRoomProjectionFailurePublishesNoServerRackOrRoomUpdate() {
        var f = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.ROOM, true, 30, 50, 2000)), 3);
        f.power(300);
        var roomCooling = new CoolingSystem(new CoolingConfiguration(
                List.of(new CoolingZoneDefinition("Z", Set.of())),
                List.of(new SupplyCoolingUnitDefinition("SUP", 1, 100, 1000, 18,
                        List.of(new CoolingZoneInfluence("Z", 1)), true)), CoolingSystemOptions.defaults()));
        var coupled = new CoupledRoomTemperatureSystem(f.datacenter, f.temperatures, f.options,
                new RoomAirTemperatureOptions(24, 2000000, 24, 0), Optional.of(roomCooling), f.coupled.rackCoolingSystem());
        var air = coupled.rackAirTemperaturesCelsius();
        assertThrows(IllegalArgumentException.class, () -> coupled.update(tick(1, 60)));
        assertEquals(24, coupled.roomAirTemperatureCelsius());
        assertSame(air, coupled.rackAirTemperaturesCelsius());
        for (int i = 0; i < 3; i++) assertEquals(24, f.server(i));
        assertTrue(coupled.lastSnapshot().isEmpty());
        assertTrue(coupled.lastCoolingSnapshot().isEmpty());
    }

    @Test
    void combinesRackCoolingWithRoomSupplyExhaustAndEnvironmentLoss() {
        var base = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.ROOM, true, 30, 50, 2000)), 3);
        var def = base.definition;
        var influence = List.of(new CoolingZoneInfluenceConfigDefinition("Z", 1));
        var roomCooling = new CoolingConfigDefinition(
                List.of(new CoolingZoneConfigDefinition("Z", List.of("C01", "C02", "C03"), List.of("R01"))),
                List.of(new SupplyCoolingUnitConfigDefinition("SUP", 1, 400, 1000, 18, influence, true)),
                List.of(new ExhaustCoolingUnitConfigDefinition("EXH", 1, 200, influence, true)),
                new CoolingSystemOptionsDefinition(1.204, 1005, 24, 0.95, 0.1, 1000, 300));
        var configured = new DatacenterDefinition(def.name(), def.layout(), def.serverModels(), def.servers(),
                def.temperature(), def.health(), roomCooling, def.dataInputMode(), def.powerInputGranularity(),
                def.temperatureInputGranularity(), new RoomAirTemperatureOptionsDefinition(24, 2000000, 20, 40), def.rackCooling());
        var roomSystem = new CoolingConfigurationFactory().create(configured, base.datacenter).map(CoolingSystem::new);
        var coupled = new CoupledRoomTemperatureSystemFactory().create(configured, base.datacenter,
                base.temperatures, roomSystem, base.coupled.rackCoolingSystem()).orElseThrow();
        var f = new Fixture(configured, base.datacenter, base.temperatures, base.options, coupled);
        f.power(300);
        var ledger = advance(f, 1, 60);
        assertTrue(ledger.removedHeatJoules() > 0);
        assertTrue(ledger.environmentHeatLossJoules() > 0);
        assertTrue(ledger.rackHeatRejectedToRoomJoules() > 0);
        assertEquals(2, coupled.lastCoolingSnapshot().orElseThrow().units().size());
        assertEquals(600, coupled.lastCoolingSnapshot().orElseThrow().totalElectricalPowerWatts());
        // Room equipment electricity remains outside the thermal ledger;
        // rack electricity is explicit. Facility aggregate integration is stage 6.
        assertEquals(30000, ledger.rackCoolingElectricalEnergyJoules());
    }

    @Test
    void powerDrivenPipelineActuallyRunsLocalRackThermalSystem() {
        var f = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.EXTERNAL, true, 30, 50, 2000)), 1);
        var systems = new DatacenterInputPipelineFactory().createInputSystems(f.definition, f.datacenter,
                f.temperatures, f.options, null, (server, tick) -> 250, f.coupled);
        var tick = tick(1, 60);
        systems.forEach(system -> system.update(tick));
        assertEquals(15000, f.coupled.lastSnapshot().orElseThrow().inputElectricalEnergyJoules());
        assertEquals(0, f.coupled.lastSnapshot().orElseThrow().energyBalanceResidualJoules(), EPS);
        assertTrue(f.local(R1) < 30);
    }

    @Test
    void numericallyUnresolvableRackConductanceFailsWithoutPublishingPartialState() {
        var definition = new RackCoolingUnitConfigDefinition("RC1", "C01", "R01", 30, 10000, 0,
                1e296, 1e296, 18, 500, HeatRejectionDestination.EXTERNAL, true);
        var f = fixture(List.of(definition), 0);
        var before = f.coupled.rackAirTemperaturesCelsius();
        assertThrows(IllegalArgumentException.class, () -> f.coupled.update(tick(1, 1)));
        assertSame(before, f.coupled.rackAirTemperaturesCelsius());
        assertEquals(24, f.coupled.roomAirTemperatureCelsius());
        assertTrue(f.coupled.lastSnapshot().isEmpty());
    }

    @Test
    void pipelineAndFactoriesRejectMissingOrMismatchedRackControls() {
        var f = fixture(List.of(unit("RC1", "C01", HeatRejectionDestination.ROOM, true, 30, 50, 2000)), 1);
        assertThrows(IllegalArgumentException.class, () -> new CoupledRoomTemperatureSystemFactory().create(
                f.definition, f.datacenter, f.temperatures, Optional.empty(), Optional.empty()));
        var withoutRack = new CoupledRoomTemperatureSystem(f.datacenter, f.temperatures, f.options,
                new RoomAirTemperatureOptions(24, 2000000, 24, 0), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new DatacenterInputPipelineFactory().createInputSystems(
                f.definition, f.datacenter, f.temperatures, f.options,
                new ConstantWorkloadSource(0.5), null, withoutRack));
        var explicit = new CoupledRoomTemperatureSystemFactory().create(f.definition, f.datacenter,
                f.temperatures, Optional.empty(), f.coupled.rackCoolingSystem()).orElseThrow();
        assertSame(f.coupled.rackCoolingSystem().orElseThrow(), explicit.rackCoolingSystem().orElseThrow());
    }
}

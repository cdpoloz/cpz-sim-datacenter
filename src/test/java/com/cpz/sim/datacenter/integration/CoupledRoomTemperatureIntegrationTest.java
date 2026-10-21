package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.factory.*;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.input.PowerInputGranularity;
import com.cpz.sim.datacenter.input.TemperatureInputGranularity;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.snapshot.CoupledRoomThermalSnapshot;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CoupledRoomTemperatureIntegrationTest {

    private record Fixture(DatacenterDefinition definition, Datacenter datacenter,
                           TemperatureSystem temperatures, TemperatureSystemOptions options,
                           Optional<CoolingSystem> cooling, CoupledRoomTemperatureSystem coupled) {
        void offlineAt(double temperature) {
            datacenter.getServers().forEach(server -> {
                server.setStatus(HardwareStatus.OFFLINE);
                server.updatePowerConsumption();
                temperatures.setTemperatureCelsius(server.getCode(), temperature);
            });
        }
    }

    private static DatacenterDefinition definition(DatacenterDataInputMode mode, double initialAir,
                                                   double environment, double ua, boolean cooling,
                                                   double coolingCapacity, boolean roomConfigured) {
        var influences = List.of(new CoolingZoneInfluenceConfigDefinition("Z01", 1.0));
        return new DatacenterDefinition("Coupled test",
                new DatacenterLayoutDefinition(List.of(new RackDefinition("R01", "C01", "R01", List.of("S01", "S02")))),
                List.of(new ServerModelDefinition("M01", "CPZ", "Default", 100.0f, 300.0f),
                        new ServerModelDefinition("M02", "CPZ", "Larger inertia", 100.0f, 300.0f, 20000.0, 16.0)),
                List.of(new ServerDefinition("R01", "S01", "M01", "OK", ServerRole.GENERAL_PURPOSE, 1.0f),
                        new ServerDefinition("R01", "S02", "M02", "OK", ServerRole.AI, 1.0f)),
                new TemperatureSystemOptionsDefinition(24.0, 24.0, 5000.0, 8.0), null,
                cooling ? new CoolingConfigDefinition(
                        List.of(new CoolingZoneConfigDefinition("Z01", List.of("C01"), List.of("R01"))),
                        List.of(new SupplyCoolingUnitConfigDefinition("SUP", 1.0, 400.0, coolingCapacity, 18.0, influences, false)),
                        List.of(new ExhaustCoolingUnitConfigDefinition("EXH", 1.0, 200.0, influences, false)),
                        new CoolingSystemOptionsDefinition(1.204, 1005.0, 24.0, 0.95, 0.1, 1000.0, 300.0)) : null,
                mode, PowerInputGranularity.SERVER, TemperatureInputGranularity.RACK,
                roomConfigured ? new RoomAirTemperatureOptionsDefinition(initialAir, 2_000_000.0, environment, ua) : null);
    }

    private static Fixture fixture(DatacenterDefinition definition) {
        var datacenter = new DatacenterFactory().create(definition);
        var options = new TemperatureSystemOptionsFactory().create(definition);
        var temperatures = new TemperatureSystem(datacenter, options, new SimpleServerTemperatureModel());
        var cooling = new CoolingConfigurationFactory().create(definition, datacenter).map(CoolingSystem::new);
        var coupled = new CoupledRoomTemperatureSystemFactory().create(definition, datacenter, temperatures, cooling).orElse(null);
        return new Fixture(definition, datacenter, temperatures, options, cooling, coupled);
    }

    private static Fixture fixture(double initialAir, double environment, double ua, boolean cooling, double capacity) {
        return fixture(definition(DatacenterDataInputMode.UTILIZATION_DRIVEN, initialAir, environment, ua, cooling, capacity, true));
    }

    private static SimulationTick tick(long index, long seconds) {
        return new SimulationTick(index, Duration.ofSeconds(index * seconds), Duration.ofSeconds(seconds));
    }

    private static CoupledRoomThermalSnapshot advance(Fixture f, long index, long seconds) {
        f.coupled.update(tick(index, seconds));
        var snapshot = f.coupled.lastSnapshot().orElseThrow();
        assertEquals(0.0, snapshot.energyBalanceResidualJoules(), 2e-6);
        assertEquals(snapshot.inputElectricalEnergyJoules() - snapshot.serverEnergyChangeJoules(),
                snapshot.heatTransferredToAirJoules(), 2e-6);
        assertEquals(snapshot.heatTransferredToAirJoules() - snapshot.removedHeatJoules()
                        - snapshot.environmentHeatLossJoules(), snapshot.roomAirEnergyChangeJoules(), 2e-6);
        return snapshot;
    }

    @Test
    void storesPartOfElectricalEnergyInServersAndTransfersRemainderToAirOnce() {
        var f = fixture(24.0, 24.0, 0.0, false, 1000.0);
        f.datacenter.getServers().forEach(s -> s.setCurrentPowerWatts(300.0));
        for (int i = 1; i <= 21; i++) {
            var snapshot = advance(f, i, 60);
            assertEquals(36_000.0, snapshot.inputElectricalEnergyJoules());
            assertTrue(snapshot.serverEnergyChangeJoules() > 0.0);
            assertTrue(snapshot.roomAirEnergyChangeJoules() > 0.0);
            assertTrue(snapshot.roomAirEnergyChangeJoules() < snapshot.inputElectricalEnergyJoules());
            assertEquals(0.0, snapshot.removedHeatJoules());
        }
        assertTrue(f.coupled.roomAirTemperatureCelsius() < 24.0 + 600.0 * 1260.0 / 2_000_000.0);
    }

    @Test
    void offlineHotServersReleaseStoredHeatWithModelSpecificProperties() {
        var f = fixture(24.0, 24.0, 0.0, false, 1000.0);
        f.offlineAt(60.0);
        var snapshot = advance(f, 1, 60);
        assertEquals(0.0, snapshot.inputElectricalEnergyJoules());
        assertTrue(snapshot.serverEnergyChangeJoules() < 0.0);
        assertTrue(snapshot.heatTransferredToAirJoules() > 0.0);
        assertTrue(f.coupled.roomAirTemperatureCelsius() > 24.0);
        var servers = f.datacenter.getServers();
        double first = f.temperatures.getThermalState(servers.get(0).getCode()).getTemperatureCelsius();
        double second = f.temperatures.getThermalState(servers.get(1).getCode()).getTemperatureCelsius();
        assertTrue(first < second);
        assertTrue(first > f.coupled.roomAirTemperatureCelsius());
    }

    @Test
    void colderOfflineServersAbsorbHeatFromAirWithoutClampingTransfer() {
        var f = fixture(40.0, 40.0, 0.0, false, 1000.0);
        f.offlineAt(24.0);
        var snapshot = advance(f, 1, 60);
        assertTrue(snapshot.serverEnergyChangeJoules() > 0.0);
        assertTrue(snapshot.heatTransferredToAirJoules() < 0.0);
        assertTrue(snapshot.roomAirEnergyChangeJoules() < 0.0);
    }

    @Test
    void tracksSignedEnvironmentEnergy() {
        for (double environment : new double[]{10.0, 40.0}) {
            var f = fixture(24.0, environment, 500.0, false, 1000.0);
            f.offlineAt(24.0);
            var snapshot = advance(f, 1, 60);
            assertEquals(Math.signum(24.0 - environment), Math.signum(snapshot.environmentHeatLossJoules()));
        }
    }

    @Test
    void coolingRemovesStoredHeatWithNoItLoadAndRespectsRatedCapacity() {
        var f = fixture(40.0, 40.0, 0.0, true, 100.0);
        f.offlineAt(40.0);
        f.cooling.orElseThrow().enable("SUP");
        f.cooling.orElseThrow().enable("EXH");
        var snapshot = advance(f, 1, 60);
        assertEquals(0.0, snapshot.inputElectricalEnergyJoules());
        assertEquals(6000.0, snapshot.removedHeatJoules(), 1e-6);
        assertTrue(f.coupled.roomAirTemperatureCelsius() < 40.0);
        assertEquals(600.0, f.cooling.orElseThrow().currentUnitSnapshots().stream()
                .mapToDouble(s -> s.currentElectricalPowerWatts()).sum());
    }

    @Test
    void exhaustAssistsSupplyWithoutCreatingAnotherHeatSink() {
        var exhaustOnly = fixture(40.0, 40.0, 0.0, true, 1_000_000.0);
        exhaustOnly.offlineAt(40.0);
        exhaustOnly.cooling.orElseThrow().enable("EXH");
        assertEquals(0.0, advance(exhaustOnly, 1, 60).removedHeatJoules());
        assertEquals(40.0, exhaustOnly.coupled.roomAirTemperatureCelsius(), 1e-10);

        var supplyOnly = fixture(40.0, 40.0, 0.0, true, 1_000_000.0);
        var balanced = fixture(40.0, 40.0, 0.0, true, 1_000_000.0);
        for (Fixture f : List.of(supplyOnly, balanced)) {
            f.offlineAt(40.0);
            f.cooling.orElseThrow().enable("SUP");
        }
        balanced.cooling.orElseThrow().enable("EXH");
        assertTrue(advance(balanced, 1, 60).removedHeatJoules() > advance(supplyOnly, 1, 60).removedHeatJoules());
    }

    @Test
    void coolingDoesNotCrossSupplyTemperatureAndShutsOffBelowIt() {
        var f = fixture(17.0, 17.0, 0.0, true, 1_000_000.0);
        f.offlineAt(17.0);
        f.cooling.orElseThrow().enable("SUP");
        f.cooling.orElseThrow().enable("EXH");
        assertEquals(0.0, advance(f, 1, 60).removedHeatJoules());
        assertEquals(17.0, f.coupled.roomAirTemperatureCelsius(), 1e-10);
        var warm = fixture(18.1, 18.1, 0.0, true, 1_000_000.0);
        warm.offlineAt(18.1);
        warm.cooling.orElseThrow().enable("SUP");
        warm.cooling.orElseThrow().enable("EXH");
        advance(warm, 1, 3600);
        assertTrue(warm.coupled.roomAirTemperatureCelsius() >= 18.0);
    }

    @Test
    void disablingUnitsTakesEffectOnNextTickAndResetRestoresAllThermalState() {
        var f = fixture(40.0, 40.0, 0.0, true, 100.0);
        f.offlineAt(40.0);
        var cooling = f.cooling.orElseThrow();
        cooling.enable("SUP");
        assertTrue(advance(f, 1, 60).removedHeatJoules() > 0.0);
        cooling.disable("SUP");
        assertEquals(0.0, advance(f, 2, 60).removedHeatJoules());
        f.coupled.reset();
        assertEquals(40.0, f.coupled.roomAirTemperatureCelsius());
        assertTrue(f.coupled.lastSnapshot().isEmpty());
        f.datacenter.getServers().forEach(s -> assertEquals(24.0,
                f.temperatures.getThermalState(s.getCode()).getTemperatureCelsius()));
        assertFalse(cooling.isEnabled("SUP"));
        advance(f, 1, 60);
    }

    @Test
    void oneMinuteAndSixtyOneSecondTicksUseTheSameInternalSteps() {
        var minute = fixture(30.0, 20.0, 100.0, true, 1000.0);
        var seconds = fixture(30.0, 20.0, 100.0, true, 1000.0);
        for (Fixture f : List.of(minute, seconds)) f.cooling.orElseThrow().enable("SUP");
        advance(minute, 1, 60);
        for (int i = 1; i <= 60; i++) advance(seconds, i, 1);
        assertEquals(minute.coupled.roomAirTemperatureCelsius(), seconds.coupled.roomAirTemperatureCelsius(), 1e-10);
        for (var server : minute.datacenter.getServers())
            assertEquals(minute.temperatures.getThermalState(server.getCode()).getTemperatureCelsius(),
                    seconds.temperatures.getThermalState(server.getCode()).getTemperatureCelsius(), 1e-10);
    }

    @Test
    void replacesLegacyThermalUpdateInBothPowerDerivedPipelines() {
        for (var mode : new DatacenterDataInputMode[]{DatacenterDataInputMode.UTILIZATION_DRIVEN, DatacenterDataInputMode.POWER_DRIVEN}) {
            var f = fixture(definition(mode, 24.0, 24.0, 0.0, false, 1000.0, true));
            var engine = new SimulationEngine(new SimulationClock(Duration.ofSeconds(60)));
            var factory = new DatacenterInputPipelineFactory();
            var systems = factory.createInputSystems(f.definition, f.datacenter, f.temperatures, f.options,
                    new ConstantWorkloadSource(0.5), (server, tick) -> 200.0, f.coupled);
            assertSame(f.coupled, systems.getLast());
            assertFalse(systems.contains(f.temperatures));
            factory.registerInputSystems(engine, f.definition, f.datacenter, f.temperatures, f.options,
                    new ConstantWorkloadSource(0.5), (server, tick) -> 200.0, f.coupled);
            engine.step();
            var snapshot = f.coupled.lastSnapshot().orElseThrow();
            assertEquals(24_000.0, snapshot.inputElectricalEnergyJoules(), 1e-6);
            assertEquals(0.0, snapshot.energyBalanceResidualJoules(), 2e-6);
            assertThrows(IllegalArgumentException.class, () -> factory.createInputSystems(f.definition,
                    f.datacenter, f.temperatures, f.options, new ConstantWorkloadSource(0.5), null));
        }
    }

    @Test
    void legacyPipelineRemainsUncoupledWithoutRoomConfiguration() {
        var f = fixture(definition(DatacenterDataInputMode.UTILIZATION_DRIVEN, 24.0, 24.0, 0.0, false, 1000.0, false));
        assertNull(f.coupled);
        var systems = new DatacenterInputPipelineFactory().createInputSystems(f.definition, f.datacenter,
                f.temperatures, f.options, new ConstantWorkloadSource(0.5), null);
        assertSame(f.temperatures, systems.getLast());
    }

    @Test
    void rejectsTemperatureDrivenCouplingWithoutOverwritingObservedState() {
        var def = definition(DatacenterDataInputMode.TEMPERATURE_DRIVEN, 24.0, 24.0, 0.0, false, 1000.0, true);
        var dc = new DatacenterFactory().create(def);
        var options = new TemperatureSystemOptionsFactory().create(def);
        var temperatures = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel());
        var code = dc.getServers().getFirst().getCode();
        temperatures.setTemperatureCelsius(code, 50.0);
        assertThrows(IllegalArgumentException.class, () -> new CoupledRoomTemperatureSystemFactory()
                .create(def, dc, temperatures, Optional.empty()));
        assertEquals(50.0, temperatures.getThermalState(code).getTemperatureCelsius());
    }

    @Test
    void rejectsMissingConfiguredCoolingAndMismatchedStateStores() {
        var f = fixture(24.0, 24.0, 0.0, true, 1000.0);
        assertThrows(IllegalArgumentException.class, () -> new CoupledRoomTemperatureSystemFactory()
                .create(f.definition, f.datacenter, f.temperatures, Optional.empty()));
        var other = fixture(24.0, 24.0, 0.0, false, 1000.0);
        assertThrows(IllegalArgumentException.class, () -> new CoupledRoomTemperatureSystemFactory()
                .create(other.definition, other.datacenter, f.temperatures, Optional.empty()));
    }

    @Test
    void repeatedTicksAreRejectedWithoutChangingPublishedState() {
        var f = fixture(24.0, 24.0, 0.0, false, 1000.0);
        var first = advance(f, 1, 60);
        assertThrows(IllegalArgumentException.class, () -> f.coupled.update(tick(1, 60)));
        assertSame(first, f.coupled.lastSnapshot().orElseThrow());
        assertEquals(first.roomAir().temperatureCelsius(), f.coupled.roomAirTemperatureCelsius());
    }

    @Test
    void failedCalculationsDoNotPartiallyWriteServerOrRoomTemperatures() {
        var f = fixture(24.0, 24.0, 0.0, false, 1000.0);
        var previous = advance(f, 1, 60);
        var servers = f.datacenter.getServers();
        var firstCode = servers.get(0).getCode();
        var secondCode = servers.get(1).getCode();
        f.temperatures.setTemperatureCelsius(firstCode, Double.MAX_VALUE);
        f.temperatures.setTemperatureCelsius(secondCode, 31.0);
        assertThrows(IllegalArgumentException.class, () -> f.coupled.update(tick(2, 60)));
        assertEquals(Double.MAX_VALUE, f.temperatures.getThermalState(firstCode).getTemperatureCelsius());
        assertEquals(31.0, f.temperatures.getThermalState(secondCode).getTemperatureCelsius());
        assertEquals(previous.roomAir().temperatureCelsius(), f.coupled.roomAirTemperatureCelsius());
        assertSame(previous, f.coupled.lastSnapshot().orElseThrow());
    }
}

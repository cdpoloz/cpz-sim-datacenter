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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Independent full-matrix and analytical verification, without production solver helpers. */
class RackThermalBalanceValidationTest {
    private static final RackLocation[] RACKS = {new RackLocation("C01", "R01"), new RackLocation("C02", "R01")};
    private static final double[] CAPACITY = {5000, 12000, 5000, 10000, 15000, 200000};
    private static final double[] H = {8, 16, 8};
    private static final double[] POWER = {100, 250, 75};
    private static final double[] G = {40, 70};
    private static final double UA = 25;
    private static final double ENVIRONMENT = 22;
    private static final double TOLERANCE_J = 2e-5;

    private record Fixture(DatacenterDefinition definition, Datacenter datacenter,
                           TemperatureSystem temperatures, CoupledRoomTemperatureSystem coupled,
                           List<RackCoolingUnitConfigDefinition> units) {
        double[] state() {
            double[] result = new double[6];
            for (int i = 0; i < 3; i++) result[i] = temperatures.getThermalState(
                    datacenter.getServers().get(i).getCode()).getTemperatureCelsius();
            for (int j = 0; j < 2; j++) result[3 + j] = coupled.rackAirTemperatureCelsius(RACKS[j]).orElseThrow();
            result[5] = coupled.roomAirTemperatureCelsius();
            return result;
        }
    }

    private static Fixture fixture(int mask, int profile, boolean reverseUnits, DatacenterDataInputMode mode) {
        double localInitial = profile == 0 ? 10 : profile == 1 ? 40 : 24;
        var units = new ArrayList<>(List.of(
                new RackCoolingUnitConfigDefinition("RC1", "C01", "R01", localInitial, 10000, G[0], 0.1,
                        profile == 1 ? 100 : 2000, 18, 500,
                        (mask & 4) == 0 ? HeatRejectionDestination.ROOM : HeatRejectionDestination.EXTERNAL, (mask & 1) != 0),
                new RackCoolingUnitConfigDefinition("RC2", "C02", "R01", 22, 15000, G[1], 0.03,
                        1000, 18, 800,
                        (mask & 8) == 0 ? HeatRejectionDestination.ROOM : HeatRejectionDestination.EXTERNAL, (mask & 2) != 0)));
        var canonicalUnits = List.copyOf(units);
        if (reverseUnits) Collections.reverse(units);
        var racks = new ArrayList<RackDefinition>();
        var servers = new ArrayList<ServerDefinition>();
        for (int i = 1; i <= 3; i++) {
            String column = "C0" + i;
            racks.add(new RackDefinition("R01", column, "R01", List.of("S01")));
            servers.add(new ServerDefinition(column, "R01", "S01", i == 2 ? "M2" : "M1", "OK", ServerRole.GENERAL_PURPOSE));
        }
        var definition = new DatacenterDefinition("Balance verification", new DatacenterLayoutDefinition(racks),
                List.of(new ServerModelDefinition("M1", "CPZ", "Default", 100, 300),
                        new ServerModelDefinition("M2", "CPZ", "Thermal variant", 100, 300, 12000.0, 16.0)),
                servers, new TemperatureSystemOptionsDefinition(24, 24, 5000, 8), null, null, mode, null, null,
                new RoomAirTemperatureOptionsDefinition(26, CAPACITY[5], ENVIRONMENT, UA),
                new RackCoolingConfigDefinition(1.204, 1005, units));
        var datacenter = new DatacenterFactory().create(definition);
        var options = new TemperatureSystemOptionsFactory().create(definition);
        var temperatures = new TemperatureSystem(datacenter, options, new SimpleServerTemperatureModel());
        var coupled = new CoupledRoomTemperatureSystemFactory().create(definition, datacenter, temperatures, Optional.empty()).orElseThrow();
        double[] initial = {50, 10, 35};
        for (int i = 0; i < 3; i++) {
            var server = datacenter.getServers().get(i);
            temperatures.setTemperatureCelsius(server.getCode(), initial[i]);
            server.setCurrentPowerWatts(POWER[i]);
        }
        return new Fixture(definition, datacenter, temperatures, coupled, canonicalUnits);
    }

    private static Fixture fixture(int mask, int profile) {
        return fixture(mask, profile, false, DatacenterDataInputMode.POWER_DRIVEN);
    }

    private static SimulationTick tick(long index, double seconds) {
        long nanos = Math.round(seconds * 1e9);
        return new SimulationTick(index, Duration.ofNanos(index * nanos), Duration.ofNanos(nanos));
    }

    private static double storage(double[] before, double[] after, int first, int end) {
        double result = 0;
        for (int i = first; i < end; i++) result += CAPACITY[i] * (after[i] - before[i]);
        return result;
    }

    private static void assertStorage(Fixture fixture, double[] before, CoupledRoomThermalSnapshot ledger) {
        double[] after = fixture.state();
        assertEquals(storage(before, after, 0, 3), ledger.serverEnergyChangeJoules(), TOLERANCE_J);
        assertEquals(storage(before, after, 3, 5), ledger.rackAirEnergyChangeJoules(), TOLERANCE_J);
        assertEquals(storage(before, after, 5, 6), ledger.roomAirEnergyChangeJoules(), TOLERANCE_J);
        assertEquals(0, ledger.energyBalanceResidualJoules(), TOLERANCE_J);
        assertEquals(storage(before, after, 0, 6), ledger.inputElectricalEnergyJoules()
                + ledger.rackCoolingElectricalEnergyJoules() - ledger.rackHeatRejectedExternalJoules()
                - ledger.removedHeatJoules() - ledger.environmentHeatLossJoules(), TOLERANCE_J);
    }

    static Stream<Arguments> operatingCases() {
        return java.util.stream.IntStream.range(0, 16).boxed().flatMap(mask ->
                Stream.of(0.25, 2.4).map(seconds -> Arguments.of(mask, seconds)));
    }

    @ParameterizedTest(name = "full-matrix reference: control/destination mask={0}, tick={1}s")
    @MethodSource("operatingCases")
    void matchesIndependentFullNodeMatrixAndIntegratedTransfers(int mask, double seconds) {
        var f = fixture(mask, mask % 3);
        double[] reference = f.state();
        for (int tickIndex = 1; tickIndex <= 3; tickIndex++) {
            double[] before = f.state();
            double removed = 0, toRoom = 0, external = 0, exchange = 0, serverToAir = 0, environment = 0;
            int steps = (int) Math.ceil(seconds);
            double dt = seconds / steps;
            for (int step = 0; step < steps; step++) {
                reference = denseStep(reference, f.units, dt);
                for (int i = 0; i < 3; i++) {
                    int air = i < 2 ? 3 + i : 5;
                    serverToAir += H[i] * (reference[i] - reference[air]) * dt;
                }
                for (int j = 0; j < 2; j++) {
                    var unit = f.units.get(j);
                    double q = removal(unit, reference[3 + j]);
                    double p = unit.initiallyEnabled() ? unit.ratedElectricalPowerWatts() : 0;
                    removed += q * dt;
                    if (unit.heatRejectionDestination() == HeatRejectionDestination.ROOM) toRoom += (q + p) * dt;
                    else external += (q + p) * dt;
                    exchange += G[j] * (reference[3 + j] - reference[5]) * dt;
                }
                environment += UA * (reference[5] - ENVIRONMENT) * dt;
            }
            f.coupled.update(tick(tickIndex, seconds));
            assertArrayEquals(reference, f.state(), 2e-10);
            var ledger = f.coupled.lastSnapshot().orElseThrow();
            assertStorage(f, before, ledger);
            assertEquals(425 * seconds, ledger.inputElectricalEnergyJoules(), TOLERANCE_J);
            assertEquals(removed, ledger.rackRemovedHeatJoules(), TOLERANCE_J);
            assertEquals(toRoom, ledger.rackHeatRejectedToRoomJoules(), TOLERANCE_J);
            assertEquals(external, ledger.rackHeatRejectedExternalJoules(), TOLERANCE_J);
            assertEquals(exchange, ledger.rackHeatTransferredToRoomJoules(), TOLERANCE_J);
            assertEquals(serverToAir, ledger.heatTransferredToAirJoules(), TOLERANCE_J);
            assertEquals(environment, ledger.environmentHeatLossJoules(), TOLERANCE_J);
        }
    }

    /** Enumerate inactive/linear/capacity cooling branches, then solve ALL nodes
     * by pivoted Gaussian elimination. No production air solver or server
     * elimination coefficient is reused. */
    private static double[] denseStep(double[] before, List<RackCoolingUnitConfigDefinition> units, double dt) {
        for (int first = 0; first < 3; first++) for (int second = 0; second < 3; second++) {
            int[] branches = {first, second};
            double[][] matrix = new double[6][6];
            double[] rhs = new double[6];
            for (int i = 0; i < 6; i++) {
                matrix[i][i] = CAPACITY[i] / dt;
                rhs[i] = CAPACITY[i] / dt * before[i];
            }
            for (int i = 0; i < 3; i++) {
                connect(matrix, i, i < 2 ? 3 + i : 5, H[i]);
                rhs[i] += POWER[i];
            }
            matrix[5][5] += UA;
            rhs[5] += UA * ENVIRONMENT;
            boolean candidate = true;
            for (int j = 0; j < 2; j++) {
                connect(matrix, 3 + j, 5, G[j]);
                var unit = units.get(j);
                if (!unit.initiallyEnabled()) {
                    if (branches[j] != 0) candidate = false;
                    continue;
                }
                boolean room = unit.heatRejectionDestination() == HeatRejectionDestination.ROOM;
                if (room) rhs[5] += unit.ratedElectricalPowerWatts();
                if (branches[j] == 1) {
                    double k = 1.204 * 1005 * unit.ratedAirflowCubicMetersPerSecond();
                    matrix[3 + j][3 + j] += k;
                    rhs[3 + j] += k * unit.supplyAirTemperatureCelsius();
                    if (room) {
                        matrix[5][3 + j] -= k;
                        rhs[5] -= k * unit.supplyAirTemperatureCelsius();
                    }
                } else if (branches[j] == 2) {
                    rhs[3 + j] -= unit.ratedCoolingCapacityWatts();
                    if (room) rhs[5] += unit.ratedCoolingCapacityWatts();
                }
            }
            if (!candidate) continue;
            double[] state = gaussian(matrix, rhs);
            for (int j = 0; j < 2; j++) {
                var unit = units.get(j);
                if (!unit.initiallyEnabled()) continue;
                double unconstrained = 1.204 * 1005 * unit.ratedAirflowCubicMetersPerSecond()
                        * (state[3 + j] - unit.supplyAirTemperatureCelsius());
                if (branches[j] == 0 && unconstrained > 1e-7) candidate = false;
                if (branches[j] == 1 && (unconstrained < -1e-7 || unconstrained > unit.ratedCoolingCapacityWatts() + 1e-7)) candidate = false;
                if (branches[j] == 2 && unconstrained < unit.ratedCoolingCapacityWatts() - 1e-7) candidate = false;
            }
            if (candidate) return state;
        }
        throw new AssertionError("reference cooling active set not found");
    }

    private static void connect(double[][] matrix, int first, int second, double conductance) {
        matrix[first][first] += conductance;
        matrix[second][second] += conductance;
        matrix[first][second] -= conductance;
        matrix[second][first] -= conductance;
    }

    private static double[] gaussian(double[][] matrix, double[] rhs) {
        for (int column = 0; column < rhs.length; column++) {
            int pivot = column;
            for (int row = column + 1; row < rhs.length; row++)
                if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) pivot = row;
            double[] row = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = row;
            double value = rhs[column]; rhs[column] = rhs[pivot]; rhs[pivot] = value;
            assertTrue(Math.abs(matrix[column][column]) > 1e-12);
            for (int i = column + 1; i < rhs.length; i++) {
                double factor = matrix[i][column] / matrix[column][column];
                for (int j = column; j < rhs.length; j++) matrix[i][j] -= factor * matrix[column][j];
                rhs[i] -= factor * rhs[column];
            }
        }
        double[] result = new double[rhs.length];
        for (int i = rhs.length - 1; i >= 0; i--) {
            double value = rhs[i];
            for (int j = i + 1; j < rhs.length; j++) value -= matrix[i][j] * result[j];
            result[i] = value / matrix[i][i];
        }
        return result;
    }

    private static double removal(RackCoolingUnitConfigDefinition unit, double temperature) {
        if (!unit.initiallyEnabled()) return 0;
        return Math.max(0, Math.min(unit.ratedCoolingCapacityWatts(),
                1.204 * 1005 * unit.ratedAirflowCubicMetersPerSecond() * (temperature - unit.supplyAirTemperatureCelsius())));
    }

    @Test
    void controlsAndLoadChangesConserveEnergyAcrossTwoSimulatedHours() {
        var f = fixture(7, 2);
        double[] initial = f.state();
        double[] totals = new double[5];
        var controls = f.coupled.rackCoolingSystem().orElseThrow();
        var firstCapture = f.coupled.rackAirTemperaturesCelsius();
        for (int minute = 1; minute <= 120; minute++) {
            boolean firstEnabled = minute >= 11 && minute <= 40 || minute >= 81;
            boolean secondEnabled = minute >= 31 && minute <= 100;
            controls.setEnabled("RC1", firstEnabled);
            controls.setEnabled("RC2", secondEnabled);
            double itPower = 0;
            for (int i = 0; i < 3; i++) {
                var server = f.datacenter.getServers().get(i);
                if (minute >= 51 && minute <= 70) {
                    server.setStatus(HardwareStatus.OFFLINE);
                    server.updatePowerConsumption();
                } else {
                    server.setStatus(HardwareStatus.OK);
                    server.setCurrentPowerWatts(minute < 51 ? POWER[i] : POWER[i] * 0.5);
                }
                itPower += server.getCurrentPowerWatts();
            }
            double[] before = f.state();
            f.coupled.update(tick(minute, 60));
            var ledger = f.coupled.lastSnapshot().orElseThrow();
            assertStorage(f, before, ledger);
            double expectedElectricity = ((firstEnabled ? 500 : 0) + (secondEnabled ? 800 : 0)) * 60;
            assertEquals(expectedElectricity, ledger.rackCoolingElectricalEnergyJoules(), TOLERANCE_J);
            assertEquals(itPower * 60, ledger.inputElectricalEnergyJoules(), TOLERANCE_J);
            if (!firstEnabled && !secondEnabled) assertEquals(0, ledger.rackRemovedHeatJoules());
            totals[0] += ledger.inputElectricalEnergyJoules();
            totals[1] += ledger.rackCoolingElectricalEnergyJoules();
            totals[2] += ledger.rackHeatRejectedExternalJoules();
            totals[3] += ledger.removedHeatJoules();
            totals[4] += ledger.environmentHeatLossJoules();
        }
        assertEquals(totals[0] + totals[1] - totals[2] - totals[3] - totals[4],
                storage(initial, f.state(), 0, 6), 1e-3);
        assertEquals(24, firstCapture.get(RACKS[0]));
    }

    @Test
    void rejectedStepPreservesSuccessfulCaptureAndAllowsAValidRetry() {
        var actual = fixture(7, 2);
        var reference = fixture(7, 2);
        actual.coupled.update(tick(1, 60));
        reference.coupled.update(tick(1, 60));
        var committed = actual.coupled.lastSnapshot().orElseThrow();
        var air = actual.coupled.rackAirTemperaturesCelsius();
        double[] state = actual.state();
        assertThrows(IllegalArgumentException.class, () -> actual.coupled.update(tick(2, 86401)));
        assertSame(committed, actual.coupled.lastSnapshot().orElseThrow());
        assertSame(air, actual.coupled.rackAirTemperaturesCelsius());
        assertArrayEquals(state, actual.state(), 0);
        actual.coupled.update(tick(2, 60));
        reference.coupled.update(tick(2, 60));
        assertArrayEquals(reference.state(), actual.state(), 0);
        assertEquals(reference.coupled.lastSnapshot(), actual.coupled.lastSnapshot());
    }

    @Test
    void reversingUnitConfigurationOrderDoesNotChangeRackPhysics() {
        var first = fixture(7, 1, false, DatacenterDataInputMode.POWER_DRIVEN);
        var reversed = fixture(7, 1, true, DatacenterDataInputMode.POWER_DRIVEN);
        for (int i = 1; i <= 20; i++) {
            first.coupled.update(tick(i, 60));
            reversed.coupled.update(tick(i, 60));
        }
        assertArrayEquals(first.state(), reversed.state(), 1e-9);
        assertEquals(first.coupled.lastSnapshot().orElseThrow().rackRemovedHeatJoules(),
                reversed.coupled.lastSnapshot().orElseThrow().rackRemovedHeatJoules(), TOLERANCE_J);
    }

    @Test
    void utilizationAndPowerDrivenModesAgreeForTheSameElectricalLoad() {
        var utilization = fixture(3, 2, false, DatacenterDataInputMode.UTILIZATION_DRIVEN);
        var power = fixture(3, 2, false, DatacenterDataInputMode.POWER_DRIVEN);
        var options = new TemperatureSystemOptionsFactory().create(utilization.definition);
        var utilSystems = new DatacenterInputPipelineFactory().createInputSystems(utilization.definition,
                utilization.datacenter, utilization.temperatures, options, new ConstantWorkloadSource(0.5), null, utilization.coupled);
        var powerSystems = new DatacenterInputPipelineFactory().createInputSystems(power.definition,
                power.datacenter, power.temperatures, options, null, (server, tick) -> 200, power.coupled);
        for (int i = 1; i <= 10; i++) {
            var tick = tick(i, 60);
            utilSystems.forEach(system -> system.update(tick));
            powerSystems.forEach(system -> system.update(tick));
            assertArrayEquals(utilization.state(), power.state(), 1e-10);
        }
    }

    @Test
    void subsecondStepsConvergeTowardAnIndependentContinuousTwoAirNodeSolution() {
        double[] errors = new double[3];
        for (int refinement = 0; refinement < 3; refinement++) {
            double dt = 1.0 / (1 << refinement);
            double cRack = 10000, cRoom = 200000, conductance = 50;
            var def = new DatacenterDefinition("Passive analytical test",
                    new DatacenterLayoutDefinition(List.of(new RackDefinition("R01", "C01", "R01", 1))),
                    List.of(), List.of(), null, null, null, null, null, null,
                    new RoomAirTemperatureOptionsDefinition(24, cRoom, 24, 0),
                    new RackCoolingConfigDefinition(1.204, 1005, List.of(new RackCoolingUnitConfigDefinition(
                            "RC1", "C01", "R01", 40, cRack, conductance, 0.1, 2000, 18, 500, HeatRejectionDestination.ROOM, false))));
            var dc = new DatacenterFactory().create(def);
            var opts = new TemperatureSystemOptionsFactory().create(def);
            var temps = new TemperatureSystem(dc, opts, new SimpleServerTemperatureModel());
            var coupled = new CoupledRoomTemperatureSystemFactory().create(def, dc, temps, Optional.empty()).orElseThrow();
            for (int i = 1; i <= (int) (60 / dt); i++) coupled.update(tick(i, dt));
            double mean = (cRack * 40 + cRoom * 24) / (cRack + cRoom);
            double difference = 16 * Math.exp(-conductance * (1 / cRack + 1 / cRoom) * 60);
            double exactRack = mean + cRoom / (cRack + cRoom) * difference;
            errors[refinement] = Math.abs(coupled.rackAirTemperatureCelsius(RACKS[0]).orElseThrow() - exactRack);
            assertEquals(cRack * 40 + cRoom * 24, cRack * coupled.rackAirTemperatureCelsius(RACKS[0]).orElseThrow()
                    + cRoom * coupled.roomAirTemperatureCelsius(), 1e-5);
        }
        assertTrue(errors[1] < errors[0] * 0.6);
        assertTrue(errors[2] < errors[1] * 0.6);
    }
}

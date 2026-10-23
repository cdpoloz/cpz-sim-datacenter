package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerThermalProperties;
import com.cpz.sim.datacenter.snapshot.CoupledRoomThermalSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.cooling.CoolingTickInput;
import com.cpz.sim.datacenter.cooling.ServerHeatLoad;
import com.cpz.sim.datacenter.snapshot.RoomAirTemperatureSnapshot;
import com.cpz.sim.datacenter.temperature.RoomAirCoolingPath;
import com.cpz.sim.datacenter.temperature.RoomAirTemperatureOptions;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

import com.cpz.sim.datacenter.cooling.RackCoolingConfiguration;
import com.cpz.sim.datacenter.cooling.RackCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.HeatRejectionDestination;
import com.cpz.sim.datacenter.model.RackLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Single owner of server/room thermal evolution in power-derived modes.
 * Replaces TemperatureSystem.update and all legacy cooling thermal tick adapters.
 * The existing TemperatureSystem remains the per-server state store consumed by
 * snapshots and health. CoolingSystem supplies controls/resources only.
 *
 * <p>Conservative backward Euler steps of at most one second solve all servers
 * and the room simultaneously. A capped, non-negative cold-air sink prevents
 * cooling below supply temperature in an otherwise unforced room. This is a
 * mixed-air approximation: optional local rack nodes exchange heat with the
 * common room node; it is not a spatial inlet/hot-aisle model.</p>
 */
public final class CoupledRoomTemperatureSystem implements Simulatable {

    private final Datacenter datacenter;
    private final TemperatureSystem serverTemperatures;
    private final TemperatureSystemOptions serverOptions;
    private final RoomAirTemperatureOptions roomOptions;
    private final Optional<CoolingSystem> coolingSystem;
    private final Optional<RackCoolingSystem> rackCoolingSystem;
    private Map<RackLocation, Double> rackAirTemperatures = Map.of();
    private double roomTemperatureCelsius;
    private CoupledRoomThermalSnapshot lastSnapshot;
    private CoolingSnapshot lastCoolingSnapshot;

    public CoupledRoomTemperatureSystem(
            Datacenter datacenter,
            TemperatureSystem serverTemperatures,
            TemperatureSystemOptions serverOptions,
            RoomAirTemperatureOptions roomOptions,
            Optional<CoolingSystem> coolingSystem
    ) {
        this(datacenter, serverTemperatures, serverOptions, roomOptions, coolingSystem, Optional.empty());
    }

    public CoupledRoomTemperatureSystem(
            Datacenter datacenter, TemperatureSystem serverTemperatures,
            TemperatureSystemOptions serverOptions, RoomAirTemperatureOptions roomOptions,
            Optional<CoolingSystem> coolingSystem, Optional<RackCoolingSystem> rackCoolingSystem
    ) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
        this.serverTemperatures = Objects.requireNonNull(serverTemperatures, "serverTemperatures must not be null");
        this.serverOptions = Objects.requireNonNull(serverOptions, "serverOptions must not be null");
        this.roomOptions = Objects.requireNonNull(roomOptions, "roomOptions must not be null");
        this.coolingSystem = Objects.requireNonNull(coolingSystem, "coolingSystem must not be null");
        if (!serverTemperatures.uses(datacenter, serverOptions))
            throw new IllegalArgumentException("temperature state store must use the same datacenter and server options");
        this.rackCoolingSystem = Objects.requireNonNull(rackCoolingSystem, "rackCoolingSystem must not be null");
        if (rackCoolingSystem.isPresent()) {
            var roomCodes = coolingSystem.map(system -> system.configuration().units().stream()
                    .map(unit -> unit.code()).toList()).orElse(List.of());
            for (var unit : rackCoolingSystem.orElseThrow().configuration().units()) {
                if (datacenter.getRacks().stream().noneMatch(rack -> rack.getLocation().equals(unit.rackLocation())))
                    throw new IllegalArgumentException("rack cooling references unknown runtime rack: " + unit.rackLocation());
                if (roomCodes.contains(unit.code()))
                    throw new IllegalArgumentException("duplicate cooling unit code across rack and room: " + unit.code());
            }
        }
        roomTemperatureCelsius = roomOptions.initialTemperatureCelsius();
        rackAirTemperatures = initialRackAirTemperatures();
    }

    public boolean uses(Datacenter datacenter, TemperatureSystem temperatures, TemperatureSystemOptions options,
                        RoomAirTemperatureOptions roomOptions) {
        return this.datacenter == datacenter && serverTemperatures == temperatures
                && serverOptions.equals(options) && this.roomOptions.equals(roomOptions);
    }

    public boolean usesRackCooling(Optional<RackCoolingConfiguration> configuration) {
        return rackCoolingSystem.map(RackCoolingSystem::configuration).equals(configuration);
    }

    private Map<RackLocation, Double> initialRackAirTemperatures() {
        Map<RackLocation, Double> temperatures = new LinkedHashMap<>();
        rackCoolingSystem.ifPresent(system -> system.configuration().units().forEach(unit ->
                temperatures.put(unit.rackLocation(), unit.initialAirTemperatureCelsius())));
        return Map.copyOf(temperatures);
    }

    @Override
    public void update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null");
        if (lastSnapshot != null && (tick.index() <= lastSnapshot.roomAir().tickIndex()
                || tick.elapsedSeconds() <= lastSnapshot.roomAir().elapsedSeconds()))
            throw new IllegalArgumentException("coupled ticks must advance chronologically; reset before replay");
        double duration = tick.deltaSeconds();
        // A bounded tick avoids silently spending billions of iterations on malformed input.
        if (!Double.isFinite(duration) || duration <= 0.0 || duration > 86_400.0)
            throw new IllegalArgumentException("coupled tick duration must be in (0, 86400] seconds");
        int steps = (int) Math.ceil(duration);
        double dt = duration / steps;
        List<Server> servers = List.copyOf(datacenter.getServers());
        int count = servers.size();
        double[] temperatures = new double[count];
        double[] initial = new double[count];
        double[] capacity = new double[count];
        double[] conductance = new double[count];
        double[] power = new double[count];
        double totalPower = 0.0;
        for (int i = 0; i < count; i++) {
            Server server = servers.get(i);
            var state = serverTemperatures.getThermalState(server.getCode());
            if (state == null) throw new IllegalStateException("missing thermal state for " + server.getCode());
            temperatures[i] = initial[i] = state.getTemperatureCelsius();
            ServerThermalProperties properties = server.getConfig().thermalProperties();
            capacity[i] = properties == null ? serverOptions.thermalCapacityJoulesPerCelsius() : properties.thermalCapacityJoulesPerCelsius();
            conductance[i] = properties == null ? serverOptions.heatDissipationWattsPerCelsius() : properties.heatDissipationWattsPerCelsius();
            power[i] = server.getCurrentPowerWatts();
            if (!Double.isFinite(power[i]) || power[i] < 0.0)
                throw new IllegalArgumentException("server power must be finite and >= 0");
            totalPower += power[i];
        }
        List<RoomAirCoolingPath> paths = coolingSystem.map(CoolingSystem::roomAirCoolingPaths).orElse(List.of());
        List<RackCoolingUnitDefinition> rackUnits = rackCoolingSystem.map(system -> system.configuration().units()).orElse(List.of());
        int rackCount = rackUnits.size();
        double[] rackAir = new double[rackCount];
        double[] rackInitial = new double[rackCount];
        double[] rackPower = new double[rackCount];
        List<RoomAirCoolingPath> rackPaths = new ArrayList<>();
        Map<RackLocation, Integer> rackIndex = new LinkedHashMap<>();
        for (int j = 0; j < rackCount; j++) {
            var unit = rackUnits.get(j);
            rackIndex.put(unit.rackLocation(), j);
            rackAir[j] = rackInitial[j] = rackAirTemperatures.get(unit.rackLocation());
            boolean enabled = rackCoolingSystem.orElseThrow().isEnabled(unit.code());
            rackPower[j] = enabled ? unit.ratedElectricalPowerWatts() : 0.0;
            var config = rackCoolingSystem.orElseThrow().configuration();
            rackPaths.add(new RoomAirCoolingPath(enabled ? config.airDensityKilogramsPerCubicMeter()
                    * config.airSpecificHeatJoulesPerKilogramKelvin() * unit.ratedAirflowCubicMetersPerSecond() : 0.0,
                    unit.supplyAirTemperatureCelsius(), enabled ? unit.ratedCoolingCapacityWatts() : 0.0));
        }
        int[] serverRack = new int[count];
        for (int i = 0; i < count; i++) serverRack[i] = rackIndex.getOrDefault(servers.get(i).getLocation().rackLocation(), -1);
        double rackElectricalEnergy = Arrays.stream(rackPower).sum() * duration;
        double rackRemovedEnergy = 0.0;
        double rackRejectedToRoom = 0.0;
        double rackRejectedExternal = 0.0;
        double rackTransferredToRoom = 0.0;
        double room = roomTemperatureCelsius;
        double transferredEnergy = 0.0;
        double removedEnergy = 0.0;
        double environmentEnergy = 0.0;
        double airCapacity = roomOptions.effectiveThermalCapacityJoulesPerCelsius();
        double environmentConductance = roomOptions.environmentHeatTransferWattsPerCelsius();
        double[] gamma = new double[count];
        double[] base = new double[count];
        for (int step = 0; step < steps; step++) {
            double coefficient = airCapacity / dt + environmentConductance;
            double rhs = airCapacity / dt * room
                    + environmentConductance * roomOptions.environmentTemperatureCelsius();
            double[] rackCoefficients = new double[rackCount];
            double[] rackRhs = new double[rackCount];
            for (int j = 0; j < rackCount; j++) {
                var unit = rackUnits.get(j);
                rackCoefficients[j] = unit.effectiveAirThermalCapacityJoulesPerCelsius() / dt + unit.roomHeatTransferWattsPerCelsius();
                rackRhs[j] = unit.effectiveAirThermalCapacityJoulesPerCelsius() / dt * rackAir[j];
            }
            for (int i = 0; i < count; i++) {
                double ratio = dt * conductance[i] / capacity[i];
                gamma[i] = ratio <= 1.0 ? conductance[i] / (1.0 + ratio)
                        : (capacity[i] / dt) / (1.0 + 1.0 / ratio);
                base[i] = temperatures[i] + dt * power[i] / capacity[i];
                if (serverRack[i] < 0) {
                    coefficient += gamma[i];
                    rhs += gamma[i] * base[i];
                } else {
                    rackCoefficients[serverRack[i]] += gamma[i];
                    rackRhs[serverRack[i]] += gamma[i] * base[i];
                }
            }
            requireFinite(coefficient);
            requireFinite(rhs);
            List<CoupledRackAirSolver.RackEquation> rackEquations = new ArrayList<>();
            for (int j = 0; j < rackCount; j++) {
                requireFinite(rackCoefficients[j]);
                requireFinite(rackRhs[j]);
                rackEquations.add(new CoupledRackAirSolver.RackEquation(rackCoefficients[j], rackRhs[j],
                        rackUnits.get(j).roomHeatTransferWattsPerCelsius(), rackPaths.get(j), rackPower[j],
                        rackUnits.get(j).heatRejectionDestination() == HeatRejectionDestination.ROOM));
            }
            double nextRoom = rackCount == 0 ? solveRoom(coefficient, rhs / coefficient, paths)
                    : CoupledRackAirSolver.solve(coefficient, rhs / coefficient, paths, rackEquations);
            for (int j = 0; j < rackCount; j++) {
                var equation = rackEquations.get(j);
                rackAir[j] = equation.temperatureAt(nextRoom);
                double removed = rackPaths.get(j).removedHeatWatts(rackAir[j]);
                rackRemovedEnergy += removed * dt;
                double rejected = (removed + rackPower[j]) * dt;
                if (equation.rejectsToRoom()) rackRejectedToRoom += rejected;
                else rackRejectedExternal += rejected;
                rackTransferredToRoom += equation.roomConductance() * (rackAir[j] - nextRoom) * dt;
            }
            for (int i = 0; i < count; i++) {
                double reference = serverRack[i] < 0 ? nextRoom : rackAir[serverRack[i]];
                double transfer = gamma[i] * (base[i] - reference);
                temperatures[i] += dt * (power[i] - transfer) / capacity[i];
                requireFinite(temperatures[i]);
                transferredEnergy += transfer * dt;
            }
            removedEnergy += paths.stream().mapToDouble(path -> path.removedHeatWatts(nextRoom)).sum() * dt;
            environmentEnergy += environmentConductance
                    * (nextRoom - roomOptions.environmentTemperatureCelsius()) * dt;
            room = nextRoom;
        }
        double serverEnergyChange = 0.0;
        for (int i = 0; i < count; i++) serverEnergyChange += capacity[i] * (temperatures[i] - initial[i]);
        double rackAirEnergyChange = 0.0;
        Map<RackLocation, Double> nextRackAir = new LinkedHashMap<>();
        for (int j = 0; j < rackCount; j++) {
            var unit = rackUnits.get(j);
            rackAirEnergyChange += unit.effectiveAirThermalCapacityJoulesPerCelsius() * (rackAir[j] - rackInitial[j]);
            nextRackAir.put(unit.rackLocation(), rackAir[j]);
        }
        Map<RackLocation, Double> nextRackAirSnapshot = Map.copyOf(nextRackAir);
        CoupledRoomThermalSnapshot snapshot = new CoupledRoomThermalSnapshot(
                new RoomAirTemperatureSnapshot(tick.index(), tick.elapsedSeconds(), room),
                totalPower * duration, serverEnergyChange, airCapacity * (room - roomTemperatureCelsius),
                transferredEnergy, removedEnergy, environmentEnergy, rackAirEnergyChange,
                rackElectricalEnergy, rackRemovedEnergy, rackRejectedToRoom, rackRejectedExternal, rackTransferredToRoom);
        if (rackCount > 0) {
            double energyScale = Math.abs(snapshot.inputElectricalEnergyJoules())
                    + Math.abs(snapshot.rackCoolingElectricalEnergyJoules())
                    + Math.abs(snapshot.serverEnergyChangeJoules()) + Math.abs(snapshot.roomAirEnergyChangeJoules())
                    + Math.abs(snapshot.rackAirEnergyChangeJoules()) + Math.abs(snapshot.removedHeatJoules())
                    + Math.abs(snapshot.rackHeatRejectedExternalJoules()) + Math.abs(snapshot.environmentHeatLossJoules());
            requireFinite(energyScale);
            if (Math.abs(snapshot.energyBalanceResidualJoules()) > 1e-6 + 1e-9 * energyScale)
                throw new IllegalArgumentException("coupled rack energy balance exceeds numerical tolerance");
        }
        final double finalRoom = room;
        CoolingSnapshot coolingSnapshot = coolingSystem.map(system -> system.snapshotForMixedRoom(
                new CoolingTickInput(tick.index(), duration, servers.stream()
                        .map(server -> new ServerHeatLoad(server.getLocation(), server.getCurrentPowerWatts())).toList()),
                finalRoom)).orElse(null);
        // Publish only after every calculation and snapshot validation succeeds.
        for (int i = 0; i < count; i++) serverTemperatures.setTemperatureCelsius(servers.get(i).getCode(), temperatures[i]);
        roomTemperatureCelsius = room;
        rackAirTemperatures = nextRackAirSnapshot;
        lastSnapshot = snapshot;
        lastCoolingSnapshot = coolingSnapshot;
    }

    private static double solveRoom(double coefficient, double uncooledTemperature, List<RoomAirCoolingPath> paths) {
        requireFinite(uncooledTemperature);
        if (paths.isEmpty() || paths.stream().mapToDouble(p -> p.removedHeatWatts(uncooledTemperature)).sum() == 0.0)
            return uncooledTemperature;
        double totalCapacity = paths.stream().mapToDouble(RoomAirCoolingPath::capacityWatts).sum();
        requireFinite(totalCapacity);
        double lower = uncooledTemperature - totalCapacity / coefficient;
        double upper = uncooledTemperature;
        requireFinite(lower);
        for (int iteration = 0; iteration < 100; iteration++) {
            double midpoint = lower + (upper - lower) * 0.5;
            if (midpoint == lower || midpoint == upper) break;
            double removal = paths.stream().mapToDouble(p -> p.removedHeatWatts(midpoint)).sum();
            double residual = coefficient * (midpoint - uncooledTemperature) + removal;
            if (residual > 0.0) upper = midpoint;
            else lower = midpoint;
        }
        return lower + (upper - lower) * 0.5;
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("coupled thermal calculation must remain finite");
    }

    /** Read-only local air temperatures; no fallback to server or room averages. */
    public Map<RackLocation, Double> rackAirTemperaturesCelsius() {
        return rackAirTemperatures;
    }

    public OptionalDouble rackAirTemperatureCelsius(RackLocation rack) {
        Objects.requireNonNull(rack, "rack must not be null");
        Double temperature = rackAirTemperatures.get(rack);
        return temperature == null ? OptionalDouble.empty() : OptionalDouble.of(temperature);
    }

    /** The same authoritative controls consumed by the thermal update. */
    public Optional<RackCoolingSystem> rackCoolingSystem() {
        return rackCoolingSystem;
    }

    public double roomAirTemperatureCelsius() {
        return roomTemperatureCelsius;
    }

    public Optional<CoupledRoomThermalSnapshot> lastSnapshot() {
        return Optional.ofNullable(lastSnapshot);
    }

    public Optional<CoolingSnapshot> lastCoolingSnapshot() {
        return Optional.ofNullable(lastCoolingSnapshot);
    }

    @Override
    public void reset() {
        for (Server server : datacenter.getServers())
            serverTemperatures.setTemperatureCelsius(server.getCode(), serverOptions.defaultInitialTemperatureCelsius());
        roomTemperatureCelsius = roomOptions.initialTemperatureCelsius();
        lastSnapshot = null;
        lastCoolingSnapshot = null;
        coolingSystem.ifPresent(CoolingSystem::reset);
        rackCoolingSystem.ifPresent(RackCoolingSystem::reset);
        rackAirTemperatures = initialRackAirTemperatures();
    }
}

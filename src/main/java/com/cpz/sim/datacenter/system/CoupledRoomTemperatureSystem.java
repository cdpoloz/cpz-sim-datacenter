package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerThermalProperties;
import com.cpz.sim.datacenter.snapshot.CoupledRoomThermalSnapshot;
import com.cpz.sim.datacenter.snapshot.RoomAirTemperatureSnapshot;
import com.cpz.sim.datacenter.temperature.RoomAirCoolingPath;
import com.cpz.sim.datacenter.temperature.RoomAirTemperatureOptions;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

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
 * mixed-room approximation, not a spatial inlet/hot-aisle model.</p>
 */
public final class CoupledRoomTemperatureSystem implements Simulatable {

    private final Datacenter datacenter;
    private final TemperatureSystem serverTemperatures;
    private final TemperatureSystemOptions serverOptions;
    private final RoomAirTemperatureOptions roomOptions;
    private final Optional<CoolingSystem> coolingSystem;
    private double roomTemperatureCelsius;
    private CoupledRoomThermalSnapshot lastSnapshot;

    public CoupledRoomTemperatureSystem(
            Datacenter datacenter,
            TemperatureSystem serverTemperatures,
            TemperatureSystemOptions serverOptions,
            RoomAirTemperatureOptions roomOptions,
            Optional<CoolingSystem> coolingSystem
    ) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
        this.serverTemperatures = Objects.requireNonNull(serverTemperatures, "serverTemperatures must not be null");
        this.serverOptions = Objects.requireNonNull(serverOptions, "serverOptions must not be null");
        this.roomOptions = Objects.requireNonNull(roomOptions, "roomOptions must not be null");
        this.coolingSystem = Objects.requireNonNull(coolingSystem, "coolingSystem must not be null");
        if (!serverTemperatures.uses(datacenter, serverOptions))
            throw new IllegalArgumentException("temperature state store must use the same datacenter and server options");
        roomTemperatureCelsius = roomOptions.initialTemperatureCelsius();
    }

    public boolean uses(Datacenter datacenter, TemperatureSystem temperatures, TemperatureSystemOptions options,
                        RoomAirTemperatureOptions roomOptions) {
        return this.datacenter == datacenter && serverTemperatures == temperatures
                && serverOptions.equals(options) && this.roomOptions.equals(roomOptions);
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
            for (int i = 0; i < count; i++) {
                double ratio = dt * conductance[i] / capacity[i];
                gamma[i] = ratio <= 1.0 ? conductance[i] / (1.0 + ratio)
                        : (capacity[i] / dt) / (1.0 + 1.0 / ratio);
                base[i] = temperatures[i] + dt * power[i] / capacity[i];
                coefficient += gamma[i];
                rhs += gamma[i] * base[i];
            }
            requireFinite(coefficient);
            requireFinite(rhs);
            double nextRoom = solveRoom(coefficient, rhs / coefficient, paths);
            for (int i = 0; i < count; i++) {
                double transfer = gamma[i] * (base[i] - nextRoom);
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
        CoupledRoomThermalSnapshot snapshot = new CoupledRoomThermalSnapshot(
                new RoomAirTemperatureSnapshot(tick.index(), tick.elapsedSeconds(), room),
                totalPower * duration, serverEnergyChange, airCapacity * (room - roomTemperatureCelsius),
                transferredEnergy, removedEnergy, environmentEnergy);
        // Publish only after every calculation and snapshot validation succeeds.
        for (int i = 0; i < count; i++) serverTemperatures.setTemperatureCelsius(servers.get(i).getCode(), temperatures[i]);
        roomTemperatureCelsius = room;
        lastSnapshot = snapshot;
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

    public double roomAirTemperatureCelsius() {
        return roomTemperatureCelsius;
    }

    public Optional<CoupledRoomThermalSnapshot> lastSnapshot() {
        return Optional.ofNullable(lastSnapshot);
    }

    @Override
    public void reset() {
        for (Server server : datacenter.getServers())
            serverTemperatures.setTemperatureCelsius(server.getCode(), serverOptions.defaultInitialTemperatureCelsius());
        roomTemperatureCelsius = roomOptions.initialTemperatureCelsius();
        lastSnapshot = null;
        coolingSystem.ifPresent(CoolingSystem::reset);
    }
}

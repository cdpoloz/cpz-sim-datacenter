package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.snapshot.RoomAirTemperatureSnapshot;
import com.cpz.sim.datacenter.temperature.RoomAirTemperatureOptions;
import com.cpz.sim.datacenter.temperature.RoomAirThermalInput;
import com.cpz.sim.datacenter.temperature.RoomAirThermalState;
import com.cpz.sim.datacenter.temperature.SimpleRoomAirTemperatureModel;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;
import java.util.Optional;

/**
 * Advances a room-air state from explicit thermal transfers for each tick.
 * Like CoolingSystem, this is invoked explicitly with its input; it is not
 * automatically registered in the authoritative-input pipeline.
 * It does not read IT electrical power or existing cooling-zone snapshots.
 */
public final class RoomAirTemperatureSystem {

    private final RoomAirTemperatureOptions options;
    private final RoomAirThermalState state;
    private final SimpleRoomAirTemperatureModel model = new SimpleRoomAirTemperatureModel();
    private RoomAirTemperatureSnapshot lastSnapshot;

    public RoomAirTemperatureSystem(RoomAirTemperatureOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        state = new RoomAirThermalState(options);
    }

    /**
     * Advances once and captures the resulting state. Callers supply heat
     * transfers averaged over this tick, and must invoke this method once per
     * tick in chronological order. Failed calculations leave state unchanged.
     */
    public RoomAirTemperatureSnapshot tick(SimulationTick tick, RoomAirThermalInput input) {
        Objects.requireNonNull(tick, "tick must not be null");
        double next = model.nextTemperatureCelsius(
                state.getTemperatureCelsius(), tick.deltaSeconds(), input, options);
        RoomAirTemperatureSnapshot snapshot =
                new RoomAirTemperatureSnapshot(tick.index(), tick.elapsedSeconds(), next);
        state.setTemperatureCelsius(next);
        lastSnapshot = snapshot;
        return snapshot;
    }

    public double temperatureCelsius() {
        return state.getTemperatureCelsius();
    }

    public Optional<RoomAirTemperatureSnapshot> lastSnapshot() {
        return Optional.ofNullable(lastSnapshot);
    }

    public void reset() {
        state.setTemperatureCelsius(options.initialTemperatureCelsius());
        lastSnapshot = null;
    }
}

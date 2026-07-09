package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.temperature.ServerThermalState;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.List;
import java.util.Objects;

/**
 * Builds immutable temperature snapshots from the current simulation state.
 */
public final class TemperatureSnapshotProvider {

    private final Datacenter datacenter;
    private final TemperatureSystem temperatureSystem;
    private final TemperatureSystemOptions options;

    public TemperatureSnapshotProvider(
            Datacenter datacenter,
            TemperatureSystem temperatureSystem,
            TemperatureSystemOptions options
    ) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null.");
        this.temperatureSystem = Objects.requireNonNull(temperatureSystem, "temperatureSystem must not be null.");
        this.options = Objects.requireNonNull(options, "options must not be null.");
    }

    /**
     * Builds an immutable temperature snapshot for the given tick.
     *
     * <p>This provider reads current state after the simulation systems have
     * updated. It does not advance the simulation.
     */
    public TemperatureSnapshot snapshot(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null.");
        List<ServerTemperatureSnapshot> serverSnapshots = datacenter.getServers()
                .stream()
                .map(this::captureServer)
                .toList();
        return new TemperatureSnapshot(
                tick.index(),
                tick.elapsedSeconds(),
                options.ambientTemperatureCelsius(),
                serverSnapshots
        );
    }

    private ServerTemperatureSnapshot captureServer(Server server) {
        ServerThermalState state = temperatureSystem.getThermalState(server.getCode());
        double temperatureCelsius = state != null
                ? state.getTemperatureCelsius()
                : options.defaultInitialTemperatureCelsius();
        return new ServerTemperatureSnapshot(
                server.getCode(),
                server.getLocation().rackCode(),
                server.getLocation().slot(),
                server.getStatus(),
                server.getUtilization(),
                server.getCurrentPowerWatts(),
                temperatureCelsius
        );
    }
}

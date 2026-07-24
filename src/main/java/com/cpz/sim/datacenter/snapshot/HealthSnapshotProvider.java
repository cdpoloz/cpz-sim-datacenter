package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.health.ServerHealthState;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.system.ServerHealthSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.temperature.ServerThermalState;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds immutable health snapshots from the current simulation state.
 *
 * <p>For a coherent view, invoke this provider after the server health system
 * has evaluated the current tick.
 *
 * @author CPZ
 */
public class HealthSnapshotProvider {

    private final Datacenter datacenter;
    private final ServerHealthSystem healthSystem;
    private final TemperatureSystem temperatureSystem;

    public HealthSnapshotProvider(Datacenter datacenter, ServerHealthSystem healthSystem, TemperatureSystem temperatureSystem) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null.");
        this.healthSystem = Objects.requireNonNull(healthSystem, "healthSystem must not be null.");
        this.temperatureSystem = Objects.requireNonNull(temperatureSystem, "temperatureSystem must not be null.");
    }

    /**
     * Builds an immutable health snapshot for the given tick.
     *
     * <p>This provider reads current state after the simulation systems have
     * updated. It does not advance the simulation. Each status is the value
     * calculated by the health system, or the preserved {@code OFFLINE} value.
     */
    public HealthSnapshot snapshot(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null.");
        List<ServerHealthSnapshot> serverSnapshots =
                datacenter.getServers()
                        .stream()
                        .map(this::captureServer)
                        .toList();
        return new HealthSnapshot(tick.index(), tick.elapsedSeconds(), serverSnapshots);
    }

    private ServerHealthSnapshot captureServer(Server server) {
        ServerHealthState healthState = healthSystem.getHealthState(server.getCode());
        ServerThermalState thermalState = temperatureSystem.getThermalState(server.getCode());
        if (thermalState == null) throw new IllegalStateException("Missing thermal state for server: " + server.getCode());
        return new ServerHealthSnapshot(
                server.getCode(),
                server.getLocation().column(),
                server.getLocation().rackCode(),
                server.getLocation().slot(),
                server.getStatus(),
                healthState != null ? healthState.getAlertReasons() : Set.of(),
                server.getUtilization(),
                thermalState.getTemperatureCelsius()
        );
    }
}

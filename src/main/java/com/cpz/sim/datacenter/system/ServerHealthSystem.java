package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.datacenter.health.ServerHealthState;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.temperature.ServerThermalState;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Derives the health status of every installed server from its current
 * utilization and representative internal temperature.
 *
 * <p>This system must run after {@link WorkloadSystem},
 * {@link PowerConsumptionSystem}, and {@link TemperatureSystem} so it evaluates
 * values calculated for the current simulation tick, and before
 * {@link EnergyConsumptionSystem} in the causal pipeline.
 *
 * <p>Status priority is:
 *
 * <pre>
 * OFFLINE &gt; ALERT &gt; OK
 * </pre>
 *
 * <p>An offline server is never changed automatically to {@code ALERT} or
 * {@code OK}.
 *
 * @author CPZ
 */
public class ServerHealthSystem implements Simulatable {

    private final Datacenter datacenter;
    private final TemperatureSystem temperatureSystem;
    private final ServerHealthOptions options;
    private final Map<String, ServerHealthState> healthStates = new HashMap<>();

    public ServerHealthSystem(Datacenter datacenter, TemperatureSystem temperatureSystem, ServerHealthOptions options) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null.");
        this.temperatureSystem = Objects.requireNonNull(temperatureSystem, "temperatureSystem must not be null.");
        this.options = Objects.requireNonNull(options, "options must not be null.");
        initializeHealthStates();
    }

    private void initializeHealthStates() {
        for (Server server : datacenter.getServers()) {
            String serverCode = requireNonBlank(server.getCode(), "server.code");
            ServerHealthState previous = healthStates.put(serverCode, new ServerHealthState(serverCode));
            if (previous != null)
                throw new IllegalArgumentException("Duplicate server code found while initializing health states: " + serverCode);
        }
    }

    @Override
    public void update(SimulationTick simulationTick) {
        Objects.requireNonNull(simulationTick, "tick must not be null.");
        for (Server server : datacenter.getServers()) {
            ServerHealthState healthState = healthStates.computeIfAbsent(server.getCode(), ServerHealthState::new);
            if (server.getStatus() == HardwareStatus.OFFLINE) {
                healthState.clearAlertReasons();
                continue;
            }
            updateUtilizationAlert(server, healthState);
            updateTemperatureAlert(server, healthState);
            server.setStatus(healthState.hasAlertReasons() ? HardwareStatus.ALERT : HardwareStatus.OK);
        }
    }

    private void updateUtilizationAlert(Server server, ServerHealthState healthState) {
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, server.getUtilization(), options.utilizationThreshold());
    }

    private void updateTemperatureAlert(Server server, ServerHealthState healthState) {
        ServerThermalState thermalState = temperatureSystem.getThermalState(server.getCode());
        if (thermalState == null)
            throw new IllegalStateException("Missing thermal state for server: " + server.getCode());
        healthState.updateAlertReason(ServerAlertReason.HIGH_TEMPERATURE, thermalState.getTemperatureCelsius(), options.temperatureThreshold());
    }

    public ServerHealthState getHealthState(String serverCode) {
        return healthStates.get(requireNonBlank(serverCode, "serverCode"));
    }

    /**
     * Returns the current health state of all installed servers.
     */
    public Collection<ServerHealthState> getHealthStates() {
        return Collections.unmodifiableCollection(healthStates.values());
    }

    @Override
    public void reset() {
        for (Server server : datacenter.getServers()) {
            ServerHealthState healthState = healthStates.computeIfAbsent(server.getCode(), ServerHealthState::new);
            healthState.clearAlertReasons();
            if (server.getStatus() != HardwareStatus.OFFLINE) server.setStatus(HardwareStatus.OK);
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be null or blank.");
        return value;
    }

}

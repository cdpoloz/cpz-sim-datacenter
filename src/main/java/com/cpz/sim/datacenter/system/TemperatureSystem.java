package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.temperature.ServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.ServerThermalState;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.*;

/**
 * Advances the simplified per-server thermal state for each simulation tick.
 *
 * <p>This system reads the current server power already computed by
 * {@link PowerConsumptionSystem} and updates a representative internal server
 * temperature for each installed server. It does not model room temperature,
 * rack inlet temperature, airflow, or cooling equipment.
 */
public final class TemperatureSystem implements Simulatable {

    private final Datacenter datacenter;
    private final TemperatureSystemOptions options;
    private final ServerTemperatureModel temperatureModel;
    private final Map<String, ServerThermalState> thermalStates = new HashMap<>();

    public TemperatureSystem(
            Datacenter datacenter,
            TemperatureSystemOptions options,
            ServerTemperatureModel temperatureModel
    ) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null.");
        this.options = Objects.requireNonNull(options, "options must not be null.");
        this.temperatureModel = Objects.requireNonNull(
                temperatureModel,
                "temperatureModel must not be null."
        );

        initializeThermalStates();
    }

    private void initializeThermalStates() {
        for (Server server : datacenter.getServers()) {
            String serverCode = requireNonBlank(server.getCode(), "server.code");
            ServerThermalState previous = thermalStates.put(
                    serverCode,
                    new ServerThermalState(
                            serverCode,
                            options.defaultInitialTemperatureCelsius()
                    )
            );
            if (previous != null)
                throw new IllegalArgumentException("Duplicate server code found while initializing thermal states: " + serverCode
            );
        }
    }

    @Override
    public void update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null.");
        double deltaSeconds = tick.deltaSeconds();
        for (Server server : datacenter.getServers()) {
            ServerThermalState state = thermalStates.computeIfAbsent(
                    server.getCode(),
                    code -> new ServerThermalState(
                            code,
                            options.defaultInitialTemperatureCelsius()
                    )
            );
            double currentPowerWatts = server.getCurrentPowerWatts();
            double nextTemperature = temperatureModel.nextTemperatureCelsius(
                    state.getTemperatureCelsius(),
                    currentPowerWatts,
                    deltaSeconds,
                    options
            );
            state.setTemperatureCelsius(nextTemperature);
        }
    }

    public ServerThermalState getThermalState(String serverCode) {
        return thermalStates.get(requireNonBlank(serverCode, "serverCode"));
    }

    /**
     * Returns the current thermal state of all installed servers.
     */
    public Collection<ServerThermalState> getThermalStates() {
        return Collections.unmodifiableCollection(thermalStates.values());
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be null or blank.");
        return value;
    }
}

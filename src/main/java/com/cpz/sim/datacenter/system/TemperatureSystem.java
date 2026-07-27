package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerThermalProperties;
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
 *
 * <p>Thermal capacity and heat dissipation are resolved per server from
 * {@link com.cpz.sim.datacenter.model.ServerConfig#thermalProperties()} first,
 * falling back to the global {@link TemperatureSystemOptions}. Ambient and
 * initial temperatures always come from the global options.</p>
 */
public final class TemperatureSystem implements Simulatable {

    private final Datacenter datacenter;
    private final TemperatureSystemOptions options;
    private final ServerTemperatureModel temperatureModel;
    private final Map<String, ServerThermalState> thermalStates = new HashMap<>();
    private final Map<String, TemperatureSystemOptions> effectiveOptionsByServer = new HashMap<>();

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
            effectiveOptionsByServer.put(serverCode, resolveEffectiveOptions(server));
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
            TemperatureSystemOptions effectiveOptions = effectiveOptionsByServer.computeIfAbsent(
                    server.getCode(),
                    code -> resolveEffectiveOptions(server)
            );
            double nextTemperature = temperatureModel.nextTemperatureCelsius(
                    state.getTemperatureCelsius(),
                    currentPowerWatts,
                    deltaSeconds,
                    effectiveOptions
            );
            state.setTemperatureCelsius(nextTemperature);
        }
    }

    private TemperatureSystemOptions resolveEffectiveOptions(Server server) {
        ServerThermalProperties thermalProperties = server.getConfig().thermalProperties();
        if (thermalProperties == null) return options;
        return new TemperatureSystemOptions(
                options.ambientTemperatureCelsius(),
                options.defaultInitialTemperatureCelsius(),
                thermalProperties.thermalCapacityJoulesPerCelsius(),
                thermalProperties.heatDissipationWattsPerCelsius()
        );
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

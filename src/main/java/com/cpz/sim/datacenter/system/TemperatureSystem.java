package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerThermalProperties;
import com.cpz.sim.datacenter.temperature.ConstantServerTemperatureReferenceProvider;
import com.cpz.sim.datacenter.temperature.ServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.ServerTemperatureReferenceProvider;
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
 * temperature for each installed server. The external reference temperature
 * used by the thermal model is supplied independently for every server through
 * a {@link ServerTemperatureReferenceProvider}.</p>
 *
 * <p>Thermal capacity and heat dissipation are resolved per server from
 * {@link com.cpz.sim.datacenter.model.ServerConfig#thermalProperties()} first,
 * falling back to the global {@link TemperatureSystemOptions}. The initial
 * server temperature always comes from the global options.</p>
 *
 * @author CPZ
 */
public final class TemperatureSystem implements Simulatable {

    private final Datacenter datacenter;
    private final TemperatureSystemOptions options;
    private final ServerTemperatureModel temperatureModel;
    private final Map<String, ServerThermalState> thermalStates = new HashMap<>();
    private final ServerTemperatureReferenceProvider temperatureReferenceProvider;

    /**
     * Creates a temperature system using the globally configured ambient
     * temperature as the reference temperature for every server.
     */
    public TemperatureSystem(
            Datacenter datacenter,
            TemperatureSystemOptions options,
            ServerTemperatureModel temperatureModel
    ) {
        this(
                datacenter,
                options,
                temperatureModel,
                defaultTemperatureReferenceProvider(options)
        );
    }

    /**
     * Creates a temperature system using an external reference-temperature
     * provider.
     *
     * <p>The provider is consulted independently for every installed server on
     * every simulation tick.</p>
     */
    public TemperatureSystem(
            Datacenter datacenter,
            TemperatureSystemOptions options,
            ServerTemperatureModel temperatureModel,
            ServerTemperatureReferenceProvider temperatureReferenceProvider
    ) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null.");
        this.options = Objects.requireNonNull(options, "options must not be null.");
        this.temperatureModel = Objects.requireNonNull(temperatureModel, "temperatureModel must not be null.");
        this.temperatureReferenceProvider = Objects.requireNonNull(temperatureReferenceProvider, "temperatureReferenceProvider must not be null.");
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
            if (previous != null) throw new IllegalArgumentException("Duplicate server code found while initializing thermal states: " + serverCode);
        }
    }

    @Override
    public void update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null.");
        double deltaSeconds = tick.deltaSeconds();
        for (Server server : datacenter.getServers()) {
            ServerThermalState state = thermalStates.computeIfAbsent(
                    server.getCode(),
                    code -> new ServerThermalState(code, options.defaultInitialTemperatureCelsius())
            );
            double currentPowerWatts = server.getCurrentPowerWatts();
            double referenceTemperatureCelsius = temperatureReferenceProvider.temperatureCelsiusFor(server);
            if (!Double.isFinite(referenceTemperatureCelsius))
                throw new IllegalStateException("temperatureReferenceProvider returned a non-finite temperature for server: " + server.getCode());
            TemperatureSystemOptions effectiveOptions = resolveEffectiveOptions(server, referenceTemperatureCelsius);
            double nextTemperature = temperatureModel.nextTemperatureCelsius(
                    state.getTemperatureCelsius(),
                    currentPowerWatts,
                    deltaSeconds,
                    effectiveOptions
            );
            state.setTemperatureCelsius(nextTemperature);
        }
    }

    private TemperatureSystemOptions resolveEffectiveOptions(Server server, double referenceTemperatureCelsius) {
        ServerThermalProperties thermalProperties = server.getConfig().thermalProperties();
        double thermalCapacityJoulesPerCelsius =
                thermalProperties != null
                        ? thermalProperties.thermalCapacityJoulesPerCelsius()
                        : options.thermalCapacityJoulesPerCelsius();
        double heatDissipationWattsPerCelsius =
                thermalProperties != null
                        ? thermalProperties.heatDissipationWattsPerCelsius()
                        : options.heatDissipationWattsPerCelsius();
        return new TemperatureSystemOptions(
                referenceTemperatureCelsius,
                options.defaultInitialTemperatureCelsius(),
                thermalCapacityJoulesPerCelsius,
                heatDissipationWattsPerCelsius
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

    private static ServerTemperatureReferenceProvider defaultTemperatureReferenceProvider(TemperatureSystemOptions options) {
        Objects.requireNonNull(options, "options must not be null.");
        return new ConstantServerTemperatureReferenceProvider(options.ambientTemperatureCelsius());
    }
}

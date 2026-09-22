package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.input.ServerTemperatureInputSource;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;

/**
 * Updates server thermal states from an external source for
 * temperature-driven simulations.
 *
 * <p>This system is the temperature-driven counterpart of
 * {@link TemperatureSystem}. It should be registered only when temperature
 * readings are the authoritative input variable.</p>
 *
 * @author CPZ
 */
public final class TemperatureInputSystem implements Simulatable {

    private final Datacenter datacenter;
    private final TemperatureSystem temperatureSystem;
    private final ServerTemperatureInputSource temperatureInputSource;

    /**
     * Creates a temperature input system.
     *
     * @param datacenter datacenter whose installed servers are updated
     * @param temperatureSystem destination temperature state owner
     * @param temperatureInputSource source of server temperatures
     */
    public TemperatureInputSystem(
            Datacenter datacenter,
            TemperatureSystem temperatureSystem,
            ServerTemperatureInputSource temperatureInputSource
    ) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
        this.temperatureSystem = Objects.requireNonNull(temperatureSystem, "temperatureSystem must not be null");
        this.temperatureInputSource = Objects.requireNonNull(temperatureInputSource, "temperatureInputSource must not be null");
    }

    @Override
    public void update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null");
        for (Server server : datacenter.getServers()) {
            temperatureSystem.setTemperatureCelsius(
                    server.getCode(),
                    temperatureInputSource.temperatureCelsius(server, tick)
            );
        }
    }
}

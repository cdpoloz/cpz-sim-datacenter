package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.time.SimulationTick;

/**
 * Supplies externally measured or simulated server temperature for
 * temperature-driven datacenter simulations.
 *
 * @author CPZ
 */
@FunctionalInterface
public interface ServerTemperatureInputSource {

    /**
     * Returns the server temperature for the current tick.
     *
     * @param server server whose temperature is being requested
     * @param tick current simulation tick
     * @return temperature in degrees Celsius
     */
    double temperatureCelsius(Server server, SimulationTick tick);
}

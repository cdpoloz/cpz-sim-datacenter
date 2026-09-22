package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.time.SimulationTick;

/**
 * Supplies externally measured or simulated server power for power-driven
 * datacenter simulations.
 *
 * @author CPZ
 */
@FunctionalInterface
public interface ServerPowerInputSource {

    /**
     * Returns the server electrical power for the current tick.
     *
     * @param server server whose power is being requested
     * @param tick current simulation tick
     * @return power in watts
     */
    double currentPowerWatts(Server server, SimulationTick tick);
}

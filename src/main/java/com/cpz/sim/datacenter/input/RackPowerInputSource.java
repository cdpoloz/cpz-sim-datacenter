package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.foundation.time.SimulationTick;

/**
 * Supplies externally measured or simulated rack power for power-driven
 * datacenter simulations.
 *
 * @author CPZ
 */
@FunctionalInterface
public interface RackPowerInputSource {

    /**
     * Returns the rack electrical power for the current tick.
     *
     * @param rackLocation rack whose power is being requested
     * @param tick current simulation tick
     * @return total rack power in watts
     */
    double currentPowerWatts(RackLocation rackLocation, SimulationTick tick);
}

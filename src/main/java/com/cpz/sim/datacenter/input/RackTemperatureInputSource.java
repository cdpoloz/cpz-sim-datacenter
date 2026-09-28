package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.foundation.time.SimulationTick;

/**
 * Supplies externally measured or simulated rack temperature for
 * temperature-driven datacenter simulations.
 *
 * @author CPZ
 */
@FunctionalInterface
public interface RackTemperatureInputSource {

    /**
     * Returns the observed rack temperature for the current tick.
     *
     * @param rackLocation rack whose temperature is being requested
     * @param tick current simulation tick
     * @return temperature in degrees Celsius
     */
    double temperatureCelsius(RackLocation rackLocation, SimulationTick tick);
}

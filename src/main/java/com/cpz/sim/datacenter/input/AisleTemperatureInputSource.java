package com.cpz.sim.datacenter.input;

import com.cpz.sim.foundation.time.SimulationTick;

/**
 * Supplies externally measured or simulated aisle temperature for
 * temperature-driven datacenter simulations.
 *
 * @author CPZ
 */
@FunctionalInterface
public interface AisleTemperatureInputSource {

    /**
     * Returns the observed aisle temperature for the current tick.
     *
     * @param aisleCode stable aisle identifier
     * @param tick current simulation tick
     * @return temperature in degrees Celsius
     */
    double temperatureCelsius(String aisleCode, SimulationTick tick);
}

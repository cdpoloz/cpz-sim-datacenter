package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.model.Server;

/**
 * Provides the external reference temperature used by the thermal model of an
 * installed server.
 *
 * <p>In the basic temperature model, this value corresponds to the globally
 * configured ambient temperature. When cooling integration is enabled, it can
 * represent the inlet-air temperature calculated for the cooling zone that
 * contains the server.</p>
 *
 * <p>Implementations may return a different value on every simulation tick.</p>
 *
 * @author CPZ
 */
@FunctionalInterface
public interface ServerTemperatureReferenceProvider {

    /**
     * Returns the external reference temperature for a server.
     *
     * @param server installed server whose reference temperature is requested
     * @return finite reference temperature in degrees Celsius
     */
    double temperatureCelsiusFor(Server server);
}
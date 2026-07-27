package com.cpz.sim.datacenter.model;

/**
 * Defines the thermal properties of a server configuration.
 *
 * @param thermalCapacityJoulesPerCelsius
 *        amount of energy required to increase the server temperature
 *        by one degree Celsius
 * @param heatDissipationWattsPerCelsius
 *        heat dissipation rate relative to the temperature difference
 *        between the server and the ambient environment
 *
 * @author CPZ
 */
public record ServerThermalProperties(
        double thermalCapacityJoulesPerCelsius,
        double heatDissipationWattsPerCelsius
) {

    /**
     * Creates validated thermal properties for a server configuration.
     *
     * @throws IllegalArgumentException
     *         if either value is not finite or is not greater than zero
     */
    public ServerThermalProperties {
        if (!Double.isFinite(thermalCapacityJoulesPerCelsius)
                || thermalCapacityJoulesPerCelsius <= 0.0) {
            throw new IllegalArgumentException(
                    "thermalCapacityJoulesPerCelsius must be finite and greater than zero"
            );
        }

        if (!Double.isFinite(heatDissipationWattsPerCelsius)
                || heatDissipationWattsPerCelsius <= 0.0) {
            throw new IllegalArgumentException(
                    "heatDissipationWattsPerCelsius must be finite and greater than zero"
            );
        }
    }
}

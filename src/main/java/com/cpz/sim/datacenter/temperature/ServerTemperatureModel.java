package com.cpz.sim.datacenter.temperature;

/**
 * Computes the next representative internal server temperature.
 */
public interface ServerTemperatureModel {

    /**
     * Calculates the next temperature from the current temperature, current
     * server power, tick duration, and model options.
     */
    double nextTemperatureCelsius(
            double currentTemperatureCelsius,
            double currentPowerWatts,
            double deltaSeconds,
            TemperatureSystemOptions options
    );
}

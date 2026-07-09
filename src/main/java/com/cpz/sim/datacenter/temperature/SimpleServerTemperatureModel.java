package com.cpz.sim.datacenter.temperature;

/**
 * First-order server thermal model based on power input and linear heat loss.
 *
 * <p>The model uses current server power as thermal input and applies a heat
 * dissipation term proportional to the difference between the current internal
 * temperature and the configured ambient temperature.
 */
public final class SimpleServerTemperatureModel implements ServerTemperatureModel {

    @Override
    public double nextTemperatureCelsius(
            double currentTemperatureCelsius,
            double currentPowerWatts,
            double deltaSeconds,
            TemperatureSystemOptions options
    ) {
        double heatLossWatts =
                options.heatDissipationWattsPerCelsius()
                        * (currentTemperatureCelsius - options.ambientTemperatureCelsius());
        double netThermalPowerWatts = currentPowerWatts - heatLossWatts;
        double deltaTemperature =
                netThermalPowerWatts / options.thermalCapacityJoulesPerCelsius()
                        * deltaSeconds;
        return currentTemperatureCelsius + deltaTemperature;
    }

}

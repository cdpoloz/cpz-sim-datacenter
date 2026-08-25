package com.cpz.sim.datacenter.config.definition;

/**
 * JSON definition for cooling-system physical and environmental options.
 *
 * @param airDensityKilogramsPerCubicMeter air density
 * @param airSpecificHeatJoulesPerKilogramKelvin specific heat of air
 * @param initialInletAirTemperatureCelsius initial zone inlet temperature
 * @param maximumRecirculationFraction maximum permitted recirculation
 * @param residualRecirculationFraction minimum residual recirculation
 * @param effectiveZoneAirVolumeCubicMeters effective air volume per cooling zone
 * @param recirculationResponseTimeSeconds time constant used to smooth
 *        recirculation changes over time
 *
 * @author CPZ
 */
public record CoolingSystemOptionsDefinition(
        double airDensityKilogramsPerCubicMeter,
        double airSpecificHeatJoulesPerKilogramKelvin,
        double initialInletAirTemperatureCelsius,
        double maximumRecirculationFraction,
        double residualRecirculationFraction,
        double effectiveZoneAirVolumeCubicMeters,
        double recirculationResponseTimeSeconds
) {
}
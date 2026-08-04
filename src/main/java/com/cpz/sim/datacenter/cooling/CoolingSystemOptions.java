package com.cpz.sim.datacenter.cooling;

/**
 * Defines the physical and environmental parameters used by the cooling
 * system.
 *
 * <p>The default values provide a simplified representation of air near
 * normal indoor conditions. Applications may provide different values when
 * constructing the cooling system.</p>
 *
 * @param airDensityKilogramsPerCubicMeter air density
 * @param airSpecificHeatJoulesPerKilogramKelvin specific heat capacity
 *        of air
 * @param initialInletAirTemperatureCelsius initial inlet-air temperature
 *        assigned to every cooling zone
 * @param maximumRecirculationFraction upper bound applied to the calculated
 *        hot-air recirculation fraction
 *
 * @author CPZ
 */
public record CoolingSystemOptions(
        double airDensityKilogramsPerCubicMeter,
        double airSpecificHeatJoulesPerKilogramKelvin,
        double initialInletAirTemperatureCelsius,
        double maximumRecirculationFraction
) {

    /**
     * Standard air density used by the default options.
     */
    public static final double DEFAULT_AIR_DENSITY_KG_PER_M3 = 1.204;

    /**
     * Standard specific heat capacity of dry air used by the default
     * options.
     */
    public static final double DEFAULT_AIR_SPECIFIC_HEAT_JOULES_PER_KG_KELVIN = 1_005.0;

    /**
     * Default initial inlet-air temperature.
     */
    public static final double DEFAULT_INITIAL_INLET_AIR_TEMPERATURE_CELSIUS = 24.0;

    /**
     * Default maximum recirculation fraction.
     */
    public static final double DEFAULT_MAXIMUM_RECIRCULATION_FRACTION = 0.95;

    /**
     * Creates cooling-system options.
     *
     * @throws IllegalArgumentException if a value is not finite, if either
     *         physical air property is not greater than zero, or if the
     *         maximum recirculation fraction is outside {@code [0.0, 1.0]}
     */
    public CoolingSystemOptions {
        if (!Double.isFinite(airDensityKilogramsPerCubicMeter) || airDensityKilogramsPerCubicMeter <= 0.0)
            throw new IllegalArgumentException("airDensityKilogramsPerCubicMeter must be finite and greater than 0.0");
        if (!Double.isFinite(airSpecificHeatJoulesPerKilogramKelvin) || airSpecificHeatJoulesPerKilogramKelvin <= 0.0)
            throw new IllegalArgumentException("airSpecificHeatJoulesPerKilogramKelvin must be finite and greater than 0.0");
        if (!Double.isFinite(initialInletAirTemperatureCelsius))
            throw new IllegalArgumentException("initialInletAirTemperatureCelsius must be finite");
        if (!Double.isFinite(maximumRecirculationFraction) || maximumRecirculationFraction < 0.0 || maximumRecirculationFraction > 1.0)
            throw new IllegalArgumentException("maximumRecirculationFraction must be finite and between 0.0 and 1.0");
    }

    /**
     * Returns options using the standard physical and environmental values.
     *
     * @return default cooling-system options
     */
    public static CoolingSystemOptions defaults() {
        return new CoolingSystemOptions(
                DEFAULT_AIR_DENSITY_KG_PER_M3,
                DEFAULT_AIR_SPECIFIC_HEAT_JOULES_PER_KG_KELVIN,
                DEFAULT_INITIAL_INLET_AIR_TEMPERATURE_CELSIUS,
                DEFAULT_MAXIMUM_RECIRCULATION_FRACTION
        );
    }

    /**
     * Returns the volumetric heat capacity of air.
     *
     * <p>This value corresponds to {@code density × specific heat} and is
     * used in the cooling equation:</p>
     *
     * <pre>
     * deltaT = heatWatts
     *         / (volumetricHeatCapacity * airflow)
     * </pre>
     *
     * @return volumetric heat capacity in joules per cubic metre-kelvin
     */
    public double airVolumetricHeatCapacityJoulesPerCubicMeterKelvin() {
        return airDensityKilogramsPerCubicMeter * airSpecificHeatJoulesPerKilogramKelvin;
    }

}
package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.cooling.CoolingUnitType;

import java.util.Objects;

/**
 * Represents the operational result of a cooling unit at a simulation tick.
 *
 * <p>Disabled units report zero current airflow and zero current cooling
 * power. Exhaust units also report zero current cooling power because they
 * remove air but do not provide refrigeration capacity.</p>
 *
 * @param unitCode cooling-unit code
 * @param type functional type of the cooling unit
 * @param enabled whether the unit was enabled at this tick
 * @param currentAirflowCubicMetersPerSecond current airflow
 * @param currentCoolingPowerWatts current cooling power
 *
 * @author CPZ
 */
public record CoolingUnitSnapshot(
        String unitCode,
        CoolingUnitType type,
        boolean enabled,
        double currentAirflowCubicMetersPerSecond,
        double currentCoolingPowerWatts
) {

    /**
     * Creates a cooling-unit snapshot.
     *
     * @throws NullPointerException if {@code unitCode} or {@code type} is
     *         {@code null}
     * @throws IllegalArgumentException if the code is blank, a numeric value
     *         is not finite or is negative, a disabled unit reports activity,
     *         or an exhaust unit reports cooling power
     */
    public CoolingUnitSnapshot {
        Objects.requireNonNull(unitCode, "unitCode must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (unitCode.isBlank()) throw new IllegalArgumentException("unitCode must not be blank");
        validateNonNegativeFinite(currentAirflowCubicMetersPerSecond, "currentAirflowCubicMetersPerSecond");
        validateNonNegativeFinite(currentCoolingPowerWatts, "currentCoolingPowerWatts");
        if (!enabled && (currentAirflowCubicMetersPerSecond != 0.0 || currentCoolingPowerWatts != 0.0))
            throw new IllegalArgumentException("disabled cooling unit must report zero airflow and zero cooling power");
        if (type == CoolingUnitType.EXHAUST && currentCoolingPowerWatts != 0.0)
            throw new IllegalArgumentException("exhaust cooling unit must report zero cooling power");
    }

    /**
     * Returns whether the unit was actively moving air.
     *
     * @return {@code true} if current airflow is greater than zero
     */
    public boolean isOperating() {
        return enabled && currentAirflowCubicMetersPerSecond > 0.0;
    }

    private static void validateNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException(name + " must be finite and greater than or equal to 0.0");
    }

}
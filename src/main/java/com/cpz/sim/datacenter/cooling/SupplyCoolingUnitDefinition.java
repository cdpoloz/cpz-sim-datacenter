package com.cpz.sim.datacenter.cooling;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Defines a cooling unit that supplies cooled air.
 *
 * @param code unique code of the cooling unit
 * @param ratedAirflowCubicMetersPerSecond nominal airflow
 * @param ratedCoolingCapacityWatts nominal cooling capacity
 * @param supplyAirTemperatureCelsius nominal supplied-air temperature
 * @param influences cooling zones affected by the unit
 * @param initiallyEnabled whether the unit is initially enabled
 *
 * @author CPZ
 */
public record SupplyCoolingUnitDefinition(
        String code,
        double ratedAirflowCubicMetersPerSecond,
        double ratedCoolingCapacityWatts,
        double supplyAirTemperatureCelsius,
        List<CoolingZoneInfluence> influences,
        boolean initiallyEnabled
) implements CoolingUnitDefinition {

    /**
     * Creates a supply cooling-unit definition.
     *
     * @throws NullPointerException if {@code code}, {@code influences}
     *         or one of its elements is {@code null}
     * @throws IllegalArgumentException if a code or numeric value is invalid,
     *         the influence list is empty, or a zone occurs more than once
     */
    public SupplyCoolingUnitDefinition {
        validateCommon(code, ratedAirflowCubicMetersPerSecond, influences);
        if (!Double.isFinite(ratedCoolingCapacityWatts) || ratedCoolingCapacityWatts <= 0.0)
            throw new IllegalArgumentException("ratedCoolingCapacityWatts must be finite and greater than 0.0");
        if (!Double.isFinite(supplyAirTemperatureCelsius))
            throw new IllegalArgumentException("supplyAirTemperatureCelsius must be finite");
        influences = List.copyOf(influences);
    }

    @Override
    public CoolingUnitType type() {
        return CoolingUnitType.SUPPLY;
    }

    private static void validateCommon(String code, double ratedAirflowCubicMetersPerSecond, List<CoolingZoneInfluence> influences) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(influences, "influences must not be null");
        if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (!Double.isFinite(ratedAirflowCubicMetersPerSecond) || ratedAirflowCubicMetersPerSecond <= 0.0)
            throw new IllegalArgumentException("ratedAirflowCubicMetersPerSecond must be finite and greater than 0.0");
        if (influences.isEmpty())
            throw new IllegalArgumentException("influences must not be empty");
        if (influences.stream().anyMatch(Objects::isNull))
            throw new NullPointerException("influences must not contain null");
        Set<String> zoneCodes = new HashSet<>();
        for (CoolingZoneInfluence influence : influences) {
            if (!zoneCodes.add(influence.zoneCode()))
                throw new IllegalArgumentException("duplicate cooling-zone influence: " + influence.zoneCode());
        }
    }

}
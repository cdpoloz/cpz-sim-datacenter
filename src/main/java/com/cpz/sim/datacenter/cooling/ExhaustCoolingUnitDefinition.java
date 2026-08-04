package com.cpz.sim.datacenter.cooling;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Defines a cooling unit that extracts hot air.
 *
 * <p>An exhaust unit provides airflow but does not provide cooling capacity
 * or supply air at a configured temperature.</p>
 *
 * @param code unique code of the cooling unit
 * @param ratedAirflowCubicMetersPerSecond nominal extraction airflow
 * @param influences cooling zones affected by the unit
 * @param initiallyEnabled whether the unit is initially enabled
 *
 * @author CPZ
 */
public record ExhaustCoolingUnitDefinition(
        String code,
        double ratedAirflowCubicMetersPerSecond,
        List<CoolingZoneInfluence> influences,
        boolean initiallyEnabled
) implements CoolingUnitDefinition {

    /**
     * Creates an exhaust cooling-unit definition.
     *
     * @throws NullPointerException if {@code code}, {@code influences}
     *         or one of its elements is {@code null}
     * @throws IllegalArgumentException if a code or numeric value is invalid,
     *         the influence list is empty, or a zone occurs more than once
     */
    public ExhaustCoolingUnitDefinition {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(influences, "influences must not be null");
        if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (!Double.isFinite(ratedAirflowCubicMetersPerSecond) || ratedAirflowCubicMetersPerSecond <= 0.0)
            throw new IllegalArgumentException("ratedAirflowCubicMetersPerSecond must be finite and greater than 0.0");
        if (influences.isEmpty()) throw new IllegalArgumentException("influences must not be empty");
        if (influences.stream().anyMatch(Objects::isNull)) throw new NullPointerException("influences must not contain null");
        Set<String> zoneCodes = new HashSet<>();
        for (CoolingZoneInfluence influence : influences) {
            if (!zoneCodes.add(influence.zoneCode()))
                throw new IllegalArgumentException("duplicate cooling-zone influence: " + influence.zoneCode());
        }
        influences = List.copyOf(influences);
    }

    @Override
    public CoolingUnitType type() {
        return CoolingUnitType.EXHAUST;
    }

}
package com.cpz.sim.datacenter.cooling;

import java.util.Objects;

/**
 * Defines how strongly a cooling unit affects a cooling zone.
 *
 * <p>The influence weight represents the fraction of the unit's nominal
 * airflow and cooling capacity assigned to the zone. A value of
 * {@code 1.0} represents full influence.</p>
 *
 * @param zoneCode code of the affected cooling zone
 * @param weight fraction of the unit resources assigned to the zone
 *
 * @author CPZ
 */
public record CoolingZoneInfluence(
        String zoneCode,
        double weight
) {

    /**
     * Creates a cooling-zone influence.
     *
     * @throws NullPointerException if {@code zoneCode} is {@code null}
     * @throws IllegalArgumentException if the code is blank or the weight
     *         is not finite or is outside {@code (0.0, 1.0]}
     */
    public CoolingZoneInfluence {
        Objects.requireNonNull(zoneCode, "zoneCode must not be null");
        if (zoneCode.isBlank()) throw new IllegalArgumentException("zoneCode must not be blank");
        if (!Double.isFinite(weight)) throw new IllegalArgumentException("weight must be finite");
        if (weight <= 0.0 || weight > 1.0) throw new IllegalArgumentException("weight must be greater than 0.0 and less than or equal to 1.0");
    }

}
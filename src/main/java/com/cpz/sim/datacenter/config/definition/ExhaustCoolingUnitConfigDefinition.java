package com.cpz.sim.datacenter.config.definition;

import java.util.List;

/**
 * JSON definition for a cooling unit that extracts hot air.
 *
 * <p>An exhaust unit provides extraction airflow but has no cooling capacity
 * or configured supply-air temperature.</p>
 *
 * @param code unique cooling-unit code
 * @param ratedAirflowCubicMetersPerSecond nominal extraction airflow
 * @param ratedElectricalPowerWatts nominal electrical power consumed while enabled
 * @param influences cooling zones affected by the unit
 * @param initiallyEnabled whether the unit starts enabled
 *
 * @author CPZ
 */
public record ExhaustCoolingUnitConfigDefinition(
        String code,
        double ratedAirflowCubicMetersPerSecond,
        double ratedElectricalPowerWatts,
        List<CoolingZoneInfluenceConfigDefinition> influences,
        boolean initiallyEnabled
) {

    public ExhaustCoolingUnitConfigDefinition(
            String code,
            double ratedAirflowCubicMetersPerSecond,
            List<CoolingZoneInfluenceConfigDefinition> influences,
            boolean initiallyEnabled
    ) {
        this(code, ratedAirflowCubicMetersPerSecond, 0.0, influences, initiallyEnabled);
    }
}

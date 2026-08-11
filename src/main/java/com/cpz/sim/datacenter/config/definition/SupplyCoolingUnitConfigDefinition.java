package com.cpz.sim.datacenter.config.definition;

import java.util.List;

/**
 * JSON definition for a cooling unit that supplies cooled air.
 *
 * @param code unique cooling-unit code
 * @param ratedAirflowCubicMetersPerSecond nominal supplied airflow
 * @param ratedCoolingCapacityWatts nominal cooling capacity
 * @param supplyAirTemperatureCelsius nominal supplied-air temperature
 * @param influences cooling zones affected by the unit
 * @param initiallyEnabled whether the unit starts enabled
 *
 * @author CPZ
 */
public record SupplyCoolingUnitConfigDefinition(
        String code,
        double ratedAirflowCubicMetersPerSecond,
        double ratedCoolingCapacityWatts,
        double supplyAirTemperatureCelsius,
        List<CoolingZoneInfluenceConfigDefinition> influences,
        boolean initiallyEnabled
) {
}
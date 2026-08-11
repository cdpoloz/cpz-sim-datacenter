package com.cpz.sim.datacenter.config.definition;

/**
 * JSON definition for the influence of a cooling unit over a zone.
 *
 * @param zoneCode code of the affected cooling zone
 * @param weight fraction of the unit resources assigned to the zone
 *
 * @author CPZ
 */
public record CoolingZoneInfluenceConfigDefinition(
        String zoneCode,
        double weight
) {
}
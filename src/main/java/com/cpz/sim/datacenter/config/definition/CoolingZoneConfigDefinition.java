package com.cpz.sim.datacenter.config.definition;

import java.util.List;

/**
 * JSON definition for a logical cooling zone.
 *
 * <p>The zone contains the installed servers whose locations match one of the
 * configured columns and one of the configured rack codes.</p>
 *
 * @param code unique cooling-zone code
 * @param columns datacenter columns included in the zone
 * @param rackCodes rack codes included in the zone
 *
 * @author CPZ
 */
public record CoolingZoneConfigDefinition(
        String code,
        List<String> columns,
        List<String> rackCodes
) {
}
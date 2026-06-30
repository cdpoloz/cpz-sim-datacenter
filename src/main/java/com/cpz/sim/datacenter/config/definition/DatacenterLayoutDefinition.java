package com.cpz.sim.datacenter.config.definition;

import java.util.List;

/**
 * @author CPZ
 */
public record DatacenterLayoutDefinition(
        List<RackDefinition> racks
) {
}

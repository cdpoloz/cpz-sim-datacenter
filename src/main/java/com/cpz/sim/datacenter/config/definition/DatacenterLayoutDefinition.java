package com.cpz.sim.datacenter.config.definition;

import java.util.List;

/**
 * @param room optional room metadata for the active layout
 * @param racks physical racks declared in the layout
 *
 * @author CPZ
 */
public record DatacenterLayoutDefinition(
        RoomDefinition room,
        List<RackDefinition> racks
) {

    /**
     * Preserves the constructor used before optional room metadata
     * was introduced.
     */
    public DatacenterLayoutDefinition(List<RackDefinition> racks) {
        this(null, racks);
    }
}

package com.cpz.sim.datacenter.config.definition;

import java.util.List;

/**
 * @param room optional room metadata for the active layout
 * @param hotAisles optional hot-aisle mapping for temperature-driven aisle input
 * @param racks physical racks declared in the layout
 *
 * @author CPZ
 */
public record DatacenterLayoutDefinition(
        RoomDefinition room,
        List<HotAisleDefinition> hotAisles,
        List<RackDefinition> racks
) {

    /**
     * Preserves the constructor used before configurable hot aisles were
     * introduced.
     */
    public DatacenterLayoutDefinition(RoomDefinition room, List<RackDefinition> racks) {
        this(room, null, racks);
    }

    /**
     * Preserves the constructor used before optional room metadata
     * was introduced.
     */
    public DatacenterLayoutDefinition(List<RackDefinition> racks) {
        this(null, null, racks);
    }
}

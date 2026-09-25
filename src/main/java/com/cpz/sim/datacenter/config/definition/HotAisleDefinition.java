package com.cpz.sim.datacenter.config.definition;

import java.util.List;

/**
 * Defines a configurable hot aisle and the rack columns that share it.
 *
 * @param code stable hot-aisle code used by aisle temperature input
 * @param columns rack columns that belong to the hot aisle
 *
 * @author CPZ
 */
public record HotAisleDefinition(
        String code,
        List<String> columns
) {
}

package com.cpz.sim.datacenter.config.definition;

/**
 * @author CPZ
 */
public record RackDefinition(
        String code,
        String column,
        String row,
        int slotCount
) {
}

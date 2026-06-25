package com.cpz.sim.datacenter.config.definition;

/**
 * @author CPZ
 */
public record ServerDefinition(
        String column,
        String row,
        String slot,
        String modelCode,
        String status
) {
}

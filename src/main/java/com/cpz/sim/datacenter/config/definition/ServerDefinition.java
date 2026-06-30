package com.cpz.sim.datacenter.config.definition;

/**
 * @author CPZ
 */
public record ServerDefinition(
        String column,
        String row,
        String slot,
        String modelCode,
        String status,
        float workloadFactor
) {

    public ServerDefinition(
            String column,
            String row,
            String slot,
            String modelCode,
            String status
    ) {
        this(column, row, slot, modelCode, status, 1.0f);
    }
}

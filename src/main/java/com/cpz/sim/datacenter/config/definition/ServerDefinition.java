package com.cpz.sim.datacenter.config.definition;

/**
 * @author CPZ
 */
public record ServerDefinition(
        String rackCode,
        String slot,
        String modelCode,
        String status,
        float workloadFactor
) {

    public ServerDefinition(
            String rackCode,
            String slot,
            String modelCode,
            String status
    ) {
        this(rackCode, slot, modelCode, status, 1.0f);
    }
}

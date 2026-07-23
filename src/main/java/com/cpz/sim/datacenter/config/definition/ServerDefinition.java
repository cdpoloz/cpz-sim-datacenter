package com.cpz.sim.datacenter.config.definition;

import com.cpz.sim.datacenter.config.json.ServerDefinitionDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * @author CPZ
 */
@JsonDeserialize(using = ServerDefinitionDeserializer.class)
public final class ServerDefinition {

    private final String column;
    private final String rackCode;
    private final String slot;
    private final String modelCode;
    private final String status;
    private final float workloadFactor;

    public ServerDefinition(
            String rackCode,
            String slot,
            String modelCode,
            String status,
            float workloadFactor
    ) {
        this(null, rackCode, slot, modelCode, status, workloadFactor);
    }

    public ServerDefinition(
            String column,
            String rackCode,
            String slot,
            String modelCode,
            String status,
            float workloadFactor
    ) {
        this.column = column;
        this.rackCode = rackCode;
        this.slot = slot;
        this.modelCode = modelCode;
        this.status = status;
        this.workloadFactor = workloadFactor;
    }

    public ServerDefinition(
            String rackCode,
            String slot,
            String modelCode,
            String status
    ) {
        this(null, rackCode, slot, modelCode, status, 1.0f);
    }

    public ServerDefinition(
            String column,
            String rackCode,
            String slot,
            String modelCode,
            String status
    ) {
        this(column, rackCode, slot, modelCode, status, 1.0f);
    }

    public String column() {
        return column;
    }

    public String rackCode() {
        return rackCode;
    }

    public String slot() {
        return slot;
    }

    public String modelCode() {
        return modelCode;
    }

    public String status() {
        return status;
    }

    public float workloadFactor() {
        return workloadFactor;
    }
}

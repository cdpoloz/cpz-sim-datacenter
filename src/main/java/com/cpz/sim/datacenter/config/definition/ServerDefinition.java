package com.cpz.sim.datacenter.config.definition;

import com.cpz.sim.datacenter.config.json.ServerDefinitionDeserializer;
import com.cpz.sim.datacenter.model.ServerRole;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Configuration definition for one installed server.
 *
 * <p>The role remains {@code null} when the JSON property is omitted; domain
 * normalization is owned by {@link com.cpz.sim.datacenter.factory.DatacenterFactory}.
 *
 * @author CPZ
 */
@JsonDeserialize(using = ServerDefinitionDeserializer.class)
public final class ServerDefinition {

    private final String column;
    private final String rackCode;
    private final String slot;
    private final String modelCode;
    private final String status;
    private final ServerRole role;
    private final float workloadFactor;

    public ServerDefinition(
            String rackCode,
            String slot,
            String modelCode,
            String status,
            ServerRole role,
            float workloadFactor
    ) {
        this(null, rackCode, slot, modelCode, status, role, workloadFactor);
    }

    public ServerDefinition(
            String column,
            String rackCode,
            String slot,
            String modelCode,
            String status,
            ServerRole role,
            float workloadFactor
    ) {
        this.column = column;
        this.rackCode = rackCode;
        this.slot = slot;
        this.modelCode = modelCode;
        this.status = status;
        this.role = role;
        this.workloadFactor = workloadFactor;
    }

    public ServerDefinition(
            String rackCode,
            String slot,
            String modelCode,
            String status,
            ServerRole role
    ) {
        this(null, rackCode, slot, modelCode, status, role, 1.0f);
    }

    public ServerDefinition(
            String column,
            String rackCode,
            String slot,
            String modelCode,
            String status,
            ServerRole role
    ) {
        this(column, rackCode, slot, modelCode, status, role, 1.0f);
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

    /**
     * Returns the declared server role, or {@code null} when the JSON property
     * was omitted. {@link com.cpz.sim.datacenter.factory.DatacenterFactory}
     * normalizes an omitted role to {@link ServerRole#GENERAL_PURPOSE}.
     */
    public ServerRole role() {
        return role;
    }
}

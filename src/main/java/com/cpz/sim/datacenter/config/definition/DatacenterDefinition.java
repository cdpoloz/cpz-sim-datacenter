package com.cpz.sim.datacenter.config.definition;

import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.input.PowerInputGranularity;

import java.util.List;
import java.util.Objects;

/**
 * Complete JSON definition of a datacenter.
 *
 * @param name datacenter name
 * @param layout physical datacenter layout
 * @param serverModels available server-model definitions
 * @param servers installed-server definitions
 * @param temperature optional temperature-system configuration
 * @param health optional server-health configuration
 * @param cooling optional cooling-system configuration
 * @param dataInputMode authoritative input variable for the simulation pipeline
 * @param powerInputGranularity telemetry granularity for power-driven input
 *
 * @author CPZ
 */
public record DatacenterDefinition(
        String name,
        DatacenterLayoutDefinition layout,
        List<ServerModelDefinition> serverModels,
        List<ServerDefinition> servers,
        TemperatureSystemOptionsDefinition temperature,
        HealthSystemOptionsDefinition health,
        CoolingConfigDefinition cooling,
        DatacenterDataInputMode dataInputMode,
        PowerInputGranularity powerInputGranularity
) {

    public DatacenterDefinition {
        dataInputMode = Objects.requireNonNullElse(dataInputMode, DatacenterDataInputMode.UTILIZATION_DRIVEN);
        powerInputGranularity = Objects.requireNonNullElse(powerInputGranularity, PowerInputGranularity.SERVER);
    }

    /**
     * Preserves the constructor introduced with data input modes.
     */
    public DatacenterDefinition(
            String name,
            DatacenterLayoutDefinition layout,
            List<ServerModelDefinition> serverModels,
            List<ServerDefinition> servers,
            TemperatureSystemOptionsDefinition temperature,
            HealthSystemOptionsDefinition health,
            CoolingConfigDefinition cooling,
            DatacenterDataInputMode dataInputMode
    ) {
        this(name, layout, serverModels, servers, temperature, health, cooling, dataInputMode, PowerInputGranularity.SERVER);
    }

    /**
     * Preserves the constructor introduced with optional temperature, health,
     * and cooling configuration.
     */
    public DatacenterDefinition(
            String name,
            DatacenterLayoutDefinition layout,
            List<ServerModelDefinition> serverModels,
            List<ServerDefinition> servers,
            TemperatureSystemOptionsDefinition temperature,
            HealthSystemOptionsDefinition health,
            CoolingConfigDefinition cooling
    ) {
        this(name, layout, serverModels, servers, temperature, health, cooling, DatacenterDataInputMode.UTILIZATION_DRIVEN);
    }

    /**
     * Preserves the original constructor used before optional system
     * configurations were introduced.
     */
    public DatacenterDefinition(
            String name,
            DatacenterLayoutDefinition layout,
            List<ServerModelDefinition> serverModels,
            List<ServerDefinition> servers
    ) {
        this(name, layout, serverModels, servers, null, null, null, DatacenterDataInputMode.UTILIZATION_DRIVEN);
    }

    /**
     * Preserves the constructor introduced with optional temperature
     * configuration.
     */
    public DatacenterDefinition(
            String name,
            DatacenterLayoutDefinition layout,
            List<ServerModelDefinition> serverModels,
            List<ServerDefinition> servers,
            TemperatureSystemOptionsDefinition temperature
    ) {
        this(name, layout, serverModels, servers, temperature, null, null, DatacenterDataInputMode.UTILIZATION_DRIVEN);
    }

    /**
     * Preserves the constructor introduced with optional temperature and
     * health configuration.
     */
    public DatacenterDefinition(
            String name,
            DatacenterLayoutDefinition layout,
            List<ServerModelDefinition> serverModels,
            List<ServerDefinition> servers,
            TemperatureSystemOptionsDefinition temperature,
            HealthSystemOptionsDefinition health
    ) {
        this(name, layout, serverModels, servers, temperature, health, null, DatacenterDataInputMode.UTILIZATION_DRIVEN);
    }

}

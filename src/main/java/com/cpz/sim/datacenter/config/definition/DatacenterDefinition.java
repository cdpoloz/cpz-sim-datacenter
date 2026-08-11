package com.cpz.sim.datacenter.config.definition;

import java.util.List;

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
        CoolingConfigDefinition cooling
) {

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
        this(name, layout, serverModels, servers, null, null, null);
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
        this(name, layout, serverModels, servers, temperature, null, null);
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
        this(name, layout, serverModels, servers, temperature, health, null);
    }
}
package com.cpz.sim.datacenter.config.definition;

import java.util.List;

/**
 * @author CPZ
 */
public record DatacenterDefinition(
        String name,
        DatacenterLayoutDefinition layout,
        List<ServerModelDefinition> serverModels,
        List<ServerDefinition> servers,
        TemperatureSystemOptionsDefinition temperature,
        HealthSystemOptionsDefinition health
) {

    /**
     * Preserves the original constructor used before temperature and health
     * configuration were introduced.
     */
    public DatacenterDefinition(
            String name,
            DatacenterLayoutDefinition layout,
            List<ServerModelDefinition> serverModels,
            List<ServerDefinition> servers
    ) {
        this(
                name,
                layout,
                serverModels,
                servers,
                null,
                null
        );
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
        this(
                name,
                layout,
                serverModels,
                servers,
                temperature,
                null
        );
    }

}

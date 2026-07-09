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
        TemperatureSystemOptionsDefinition temperature
) {

    public DatacenterDefinition(
            String name,
            DatacenterLayoutDefinition layout,
            List<ServerModelDefinition> serverModels,
            List<ServerDefinition> servers
    ) {
        this(name, layout, serverModels, servers, null);
    }
}

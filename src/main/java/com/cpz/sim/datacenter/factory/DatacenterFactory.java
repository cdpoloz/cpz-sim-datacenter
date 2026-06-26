package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author CPZ
 */
public final class DatacenterFactory {

    private final DatacenterConfigValidator validator;

    public DatacenterFactory() {
        this(new DatacenterConfigValidator());
    }

    public DatacenterFactory(DatacenterConfigValidator validator) {
        this.validator = validator;
    }

    private static Map<String, ServerConfig> createServerConfigMap(List<ServerModelDefinition> serverModels) {
        Map<String, ServerConfig> configs = new HashMap<>();
        for (ServerModelDefinition modelDefinition : serverModels) {
            ServerConfig config = new ServerConfig(
                    modelDefinition.modelCode(),
                    modelDefinition.manufacturer(),
                    modelDefinition.model(),
                    modelDefinition.idlePowerWatts(),
                    modelDefinition.maxPowerWatts()
            );
            configs.put(modelDefinition.modelCode(), config);
        }
        return configs;
    }

    private static List<Server> createServers(List<ServerDefinition> serverDefinitions, Map<String, ServerConfig> serverConfigsByModelCode) {
        List<Server> servers = new ArrayList<>();
        for (ServerDefinition serverDefinition : serverDefinitions)
            servers.add(createServer(serverDefinition, serverConfigsByModelCode));
        return servers;
    }

    private static Server createServer(ServerDefinition definition, Map<String, ServerConfig> serverConfigsByModelCode) {
        Column column = Column.valueOf(definition.column());
        Row row = Row.valueOf(definition.row());
        Slot slot = Slot.valueOf(definition.slot());
        HardwareStatus status = HardwareStatus.valueOf(definition.status());
        ServerLocation location = new ServerLocation(column, row, slot);
        ServerConfig config = serverConfigsByModelCode.get(definition.modelCode());
        return new Server(location, config, status);
    }

    public Datacenter create(DatacenterDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition cannot be null");
        validator.validate(definition);
        try {
            Map<String, ServerConfig> serverConfigsByModelCode = createServerConfigMap(definition.serverModels());
            List<Server> servers = createServers(definition.servers(), serverConfigsByModelCode);
            return new Datacenter(servers);
        } catch (RuntimeException exception) {
            throw new DatacenterBuildException("Could not build datacenter from definition", exception);
        }
    }
}

package com.cpz.sim.datacenter.config.definition;

import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.List;
import java.util.Objects;

/**
 * Resolves the effective domain server location for definitions that may omit
 * {@code column} when the rack code is globally unambiguous.
 *
 * @author CPZ
 */
public final class ServerDefinitionLocationResolver {

    private ServerDefinitionLocationResolver() {
    }

    public static ServerLocation resolve(DatacenterDefinition definition, ServerDefinition serverDefinition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(serverDefinition, "serverDefinition cannot be null");
        return resolve(definition.layout().racks(), serverDefinition);
    }

    public static ServerLocation resolve(List<RackDefinition> racks, ServerDefinition serverDefinition) {
        Objects.requireNonNull(racks, "racks cannot be null");
        Objects.requireNonNull(serverDefinition, "serverDefinition cannot be null");
        String column = resolveColumn(racks, serverDefinition);
        return new ServerLocation(column, new RackCode(serverDefinition.rackCode()), serverDefinition.slot());
    }

    public static String resolveColumn(List<RackDefinition> racks, ServerDefinition serverDefinition) {
        Objects.requireNonNull(racks, "racks cannot be null");
        Objects.requireNonNull(serverDefinition, "serverDefinition cannot be null");
        if (!isBlank(serverDefinition.column())) return serverDefinition.column();
        String resolvedColumn = null;
        for (RackDefinition rack : racks) {
            if (rack == null || isBlank(rack.code()) || isBlank(rack.column())) continue;
            if (!rack.code().equals(serverDefinition.rackCode())) continue;
            if (resolvedColumn != null) {
                throw new IllegalArgumentException(
                        "rack code " + serverDefinition.rackCode() + " exists in multiple columns"
                );
            }
            resolvedColumn = rack.column();
        }
        if (resolvedColumn == null) {
            throw new IllegalArgumentException("unknown rack code: " + serverDefinition.rackCode());
        }
        return resolvedColumn;
    }

    public static String serverCode(DatacenterDefinition definition, ServerDefinition serverDefinition) {
        return resolve(definition, serverDefinition).code();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

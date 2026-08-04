package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.Objects;
import java.util.Set;

/**
 * Defines an application-owned group of installed servers to aggregate.
 *
 * <p>The datacenter backend assigns no physical meaning to a group. Consumers
 * may use groups for aisles, zones, clusters or any other stable selection.</p>
 *
 * @author CPZ
 */
public record ServerGroupDefinition(
        String code,
        Set<ServerLocation> serverLocations
) {

    public ServerGroupDefinition {
        Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        Objects.requireNonNull(serverLocations, "serverLocations must not be null");
        if (serverLocations.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("serverLocations must not contain null");
        serverLocations = Set.copyOf(serverLocations);
    }
}

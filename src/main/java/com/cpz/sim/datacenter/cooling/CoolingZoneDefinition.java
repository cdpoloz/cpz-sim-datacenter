package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.Objects;
import java.util.Set;

/**
 * Defines a logical cooling zone and the servers located within it.
 *
 * <p>A cooling zone represents an independently observable thermal area,
 * such as one rack row section of a hot aisle. A zone may be empty.</p>
 *
 * @param code unique code of the cooling zone
 * @param serverLocations exact server locations belonging to the zone
 *
 * @author CPZ
 */
public record CoolingZoneDefinition(
        String code,
        Set<ServerLocation> serverLocations
) {

    /**
     * Creates a cooling-zone definition.
     *
     * @throws NullPointerException if {@code code}, {@code serverLocations}
     *         or one of its elements is {@code null}
     * @throws IllegalArgumentException if {@code code} is blank
     */
    public CoolingZoneDefinition {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(serverLocations, "serverLocations must not be null");
        if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (serverLocations.stream().anyMatch(Objects::isNull)) throw new NullPointerException("serverLocations must not contain null");
        serverLocations = Set.copyOf(serverLocations);
    }

    /**
     * Returns whether this zone contains the given server location.
     *
     * @param location server location to check
     * @return {@code true} if the location belongs to this zone
     *
     * @throws NullPointerException if {@code location} is {@code null}
     */
    public boolean contains(ServerLocation location) {
        Objects.requireNonNull(location, "location must not be null");
        return serverLocations.contains(location);
    }

    /**
     * Returns whether this zone contains no server locations.
     *
     * @return {@code true} if the zone is empty
     */
    public boolean isEmpty() {
        return serverLocations.isEmpty();
    }

}
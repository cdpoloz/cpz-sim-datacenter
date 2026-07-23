package com.cpz.sim.datacenter.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author CPZ
 */
public final class Datacenter {

    private final List<Rack> racks;
    private final Map<RackLocation, Rack> racksByLocation;
    private final Map<ServerLocation, Server> serversByLocation;
    private final Map<RackLocation, List<Server>> serversByRackLocation;
    private final List<Server> servers;

    public Datacenter(List<Rack> racks, List<Server> servers) {
        Objects.requireNonNull(racks, "racks cannot be null");
        Objects.requireNonNull(servers, "servers cannot be null");
        if (racks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("racks cannot contain null elements");
        }
        if (servers.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("servers cannot contain null elements");
        }
        validateUniqueRacks(racks);
        Map<RackLocation, Rack> racksByLocation = createRackMap(racks);
        validateServerLocations(servers, racksByLocation);
        validateUniqueServerLocations(servers);
        Map<ServerLocation, Server> serversByLocation = createServerMap(servers);
        this.racks = List.copyOf(racks);
        this.servers = List.copyOf(servers);
        this.racksByLocation = Map.copyOf(racksByLocation);
        this.serversByLocation = Map.copyOf(serversByLocation);
        this.serversByRackLocation = Map.copyOf(createServersByRackLocation(this.racks, serversByLocation));
    }

    private static Map<RackLocation, Rack> createRackMap(List<Rack> racks) {
        return racks.stream().collect(Collectors.toMap(Rack::getLocation, rack -> rack));
    }

    private static Map<ServerLocation, Server> createServerMap(List<Server> servers) {
        return servers.stream().collect(Collectors.toMap(Server::getLocation, server -> server));
    }

    private static Map<RackLocation, List<Server>> createServersByRackLocation(
            List<Rack> racks,
            Map<ServerLocation, Server> serversByLocation
    ) {
        return racks.stream().collect(Collectors.toMap(
                Rack::getLocation,
                rack -> serversInPhysicalSlotOrder(rack, serversByLocation)
        ));
    }

    private static List<Server> serversInPhysicalSlotOrder(Rack rack, Map<ServerLocation, Server> serversByLocation) {
        List<Server> rackServers = new ArrayList<>();
        RackLocation location = rack.getLocation();
        for (String slotCode : rack.getSlotCodes()) {
            Server server = serversByLocation.get(new ServerLocation(location.column(), location.rackCode(), slotCode));
            if (server != null) rackServers.add(server);
        }
        return List.copyOf(rackServers);
    }

    private static void validateUniqueRacks(List<Rack> racks) {
        Set<RackLocation> locations = new HashSet<>();
        for (Rack rack : racks) {
            if (!locations.add(rack.getLocation())) {
                throw new IllegalArgumentException("duplicate rack location: " + rack.getLocation().code());
            }
        }
    }

    private static void validateServerLocations(List<Server> servers, Map<RackLocation, Rack> racksByLocation) {
        for (Server server : servers) {
            RackLocation rackLocation = server.getLocation().rackLocation();
            Rack rack = racksByLocation.get(rackLocation);
            if (rack == null) {
                throw new IllegalArgumentException("server references unknown rack location: " + rackLocation.code());
            }
            validateServerSlot(server, rack);
        }
    }

    private static void validateServerSlot(Server server, Rack rack) {
        String slot = server.getLocation().slot();
        if (!rack.hasSlot(slot)) {
            throw new IllegalArgumentException(
                    "server references unknown slot " + slot + " in rack " + rack.getCode().value()
                            + ", column " + rack.getColumn()
            );
        }
    }

    private static void validateUniqueServerLocations(List<Server> servers) {
        Objects.requireNonNull(servers, "servers cannot be null");
        Set<ServerLocation> locations = new HashSet<>();
        for (Server server : servers) {
            if (!locations.add(server.getLocation()))
                throw new IllegalArgumentException("duplicate server location: " + server.getCode());
        }
    }

    private static void validateNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
    }

    public List<Rack> getRacks() {
        return racks;
    }

    public int getRackCount() {
        return racks.size();
    }

    public List<Server> getServers() {
        return servers;
    }

    /**
     * Returns installed servers in the rack identified by {@code column + rack},
     * ordered by the rack's physical slot order.
     *
     * @throws IllegalArgumentException if the rack does not exist
     */
    public List<Server> getServers(String column, String rack) {
        return getServers(new RackLocation(column, rack));
    }

    /**
     * Returns installed servers in the rack identified by {@link RackLocation},
     * ordered by the rack's physical slot order.
     *
     * @throws IllegalArgumentException if the rack does not exist
     */
    public List<Server> getServers(RackLocation location) {
        Objects.requireNonNull(location, "location cannot be null");
        requireRack(location);
        return serversByRackLocation.getOrDefault(location, List.of());
    }

    /**
     * Returns the installed server at {@code column + rack + slot}.
     *
     * @return an empty optional when the rack and slot exist but no server is installed
     * @throws IllegalArgumentException if the rack or slot does not exist
     */
    public Optional<Server> getServer(String column, String rack, String slot) {
        return getServer(new ServerLocation(column, rack, slot));
    }

    /**
     * Returns the installed server at a complete server location.
     *
     * @return an empty optional when the rack and slot exist but no server is installed
     * @throws IllegalArgumentException if the rack or slot does not exist
     */
    public Optional<Server> getServer(ServerLocation location) {
        Objects.requireNonNull(location, "location cannot be null");
        Rack rack = requireRack(location.rackLocation());
        if (!rack.hasSlot(location.slot())) {
            throw new IllegalArgumentException(
                    "unknown slot " + location.slot() + " in rack " + location.rackCode().value()
                            + ", column " + location.column()
            );
        }
        return Optional.ofNullable(serversByLocation.get(location));
    }

    /**
     * Looks up a rack by {@code column + rackCode}.
     */
    public Optional<Rack> findRack(String column, String rack) {
        return findRack(new RackLocation(column, rack));
    }

    /**
     * Looks up a rack by physical rack identity.
     */
    public Optional<Rack> findRack(RackLocation location) {
        Objects.requireNonNull(location, "location cannot be null");
        return Optional.ofNullable(racksByLocation.get(location));
    }

    private Rack requireRack(RackLocation location) {
        Rack rack = racksByLocation.get(location);
        if (rack == null) throw new IllegalArgumentException("unknown rack location: " + location.code());
        return rack;
    }

    public int getServerCount() {
        return servers.size();
    }

    public float getTotalItPowerWatts() {
        float totalPowerWatts = 0.0f;
        for (Server server : servers) totalPowerWatts += server.getCurrentPowerWatts();
        return totalPowerWatts;
    }

    public float getItPowerWattsByRackCode(RackCode rackCode) {
        Objects.requireNonNull(rackCode, "rackCode cannot be null");
        float totalPowerWatts = 0.0f;
        for (Server server : servers) {
            if (!server.getLocation().rackCode().equals(rackCode)) continue;
            totalPowerWatts += server.getCurrentPowerWatts();
        }
        return totalPowerWatts;
    }

    public float getItPowerWattsByColumn(String column) {
        validateNonBlank(column, "column");
        float totalPowerWatts = 0.0f;
        for (Server server : servers) {
            if (!server.getLocation().column().equals(column)) continue;
            totalPowerWatts += server.getCurrentPowerWatts();
        }
        return totalPowerWatts;
    }

    public float getItPowerWattsByRow(String row) {
        validateNonBlank(row, "row");
        float totalPowerWatts = 0.0f;
        for (Server server : servers) {
            Rack rack = racksByLocation.get(server.getLocation().rackLocation());
            if (!rack.getRow().equals(row)) continue;
            totalPowerWatts += server.getCurrentPowerWatts();
        }
        return totalPowerWatts;
    }
}

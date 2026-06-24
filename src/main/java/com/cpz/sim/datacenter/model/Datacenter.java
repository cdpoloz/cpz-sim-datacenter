package com.cpz.sim.datacenter.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author CPZ
 */
public final class Datacenter {

    private final List<Server> servers;

    public Datacenter(List<Server> servers) {
        Objects.requireNonNull(servers, "servers cannot be null");
        if (servers.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("servers cannot contain null elements");
        }
        validateUniqueLocations(servers);
        this.servers = List.copyOf(servers);
    }

    private static void validateUniqueLocations(List<Server> servers) {
        Objects.requireNonNull(servers, "servers cannot be null");
        Set<String> codes = new HashSet<>();
        for (Server server : servers) {
            if (!codes.add(server.getCode()))
                throw new IllegalArgumentException("duplicate server location: " + server.getCode());
        }
    }

    public List<Server> getServers() {
        return servers;
    }

    public int getServerCount() {
        return servers.size();
    }

    public float getTotalItPowerWatts() {
        float totalPowerWatts = 0.0f;
        for (Server server : servers) totalPowerWatts += server.getCurrentPowerWatts();
        return totalPowerWatts;
    }

    public float getItPowerWatts(Column column) {
        Objects.requireNonNull(column, "column cannot be null");
        float totalPowerWatts = 0.0f;
        for (Server server : servers) {
            if (server.getLocation().column() != column) continue;
            totalPowerWatts += server.getCurrentPowerWatts();
        }
        return totalPowerWatts;
    }

    public float getItPowerWatts(Row row) {
        Objects.requireNonNull(row, "row cannot be null");
        float totalPowerWatts = 0.0f;
        for (Server server : servers) {
            if (server.getLocation().row() != row) continue;
            totalPowerWatts += server.getCurrentPowerWatts();
        }
        return totalPowerWatts;
    }
}

package com.cpz.sim.datacenter.model;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author CPZ
 */
public final class Datacenter {

    private static final Pattern SLOT_PATTERN = Pattern.compile("^U(\\d+)$");

    private final List<Rack> racks;
    private final Map<RackCode, Rack> racksByCode;
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
        Map<RackCode, Rack> racksByCode = createRackMap(racks);
        validateServerLocations(servers, racksByCode);
        validateUniqueServerLocations(servers);
        this.racks = List.copyOf(racks);
        this.racksByCode = Map.copyOf(racksByCode);
        this.servers = List.copyOf(servers);
    }

    private static Map<RackCode, Rack> createRackMap(List<Rack> racks) {
        return racks.stream().collect(Collectors.toMap(Rack::getCode, Function.identity()));
    }

    private static void validateUniqueRacks(List<Rack> racks) {
        Set<RackCode> codes = new HashSet<>();
        for (Rack rack : racks) {
            if (!codes.add(rack.getCode())) {
                throw new IllegalArgumentException("duplicate rack code: " + rack.getCode().value());
            }
        }
    }

    private static void validateServerLocations(List<Server> servers, Map<RackCode, Rack> racksByCode) {
        for (Server server : servers) {
            RackCode rackCode = server.getLocation().rackCode();
            Rack rack = racksByCode.get(rackCode);
            if (rack == null) {
                throw new IllegalArgumentException("server references unknown rack code: " + rackCode.value());
            }
            validateServerSlot(server, rack);
        }
    }

    private static void validateServerSlot(Server server, Rack rack) {
        String slot = server.getLocation().slot();
        Matcher matcher = SLOT_PATTERN.matcher(slot);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid server slot: " + server.getCode());
        }
        int slotNumber;
        try {
            slotNumber = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid server slot: " + server.getCode(), exception);
        }
        if (slotNumber < 1 || slotNumber > rack.getSlotCount()) {
            throw new IllegalArgumentException("server slot outside rack range: " + server.getCode());
        }
    }

    private static void validateUniqueServerLocations(List<Server> servers) {
        Objects.requireNonNull(servers, "servers cannot be null");
        Set<String> codes = new HashSet<>();
        for (Server server : servers) {
            if (!codes.add(server.getCode()))
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
            Rack rack = racksByCode.get(server.getLocation().rackCode());
            if (!rack.getLocation().column().equals(column)) continue;
            totalPowerWatts += server.getCurrentPowerWatts();
        }
        return totalPowerWatts;
    }

    public float getItPowerWattsByRow(String row) {
        validateNonBlank(row, "row");
        float totalPowerWatts = 0.0f;
        for (Server server : servers) {
            Rack rack = racksByCode.get(server.getLocation().rackCode());
            if (!rack.getLocation().row().equals(row)) continue;
            totalPowerWatts += server.getCurrentPowerWatts();
        }
        return totalPowerWatts;
    }
}

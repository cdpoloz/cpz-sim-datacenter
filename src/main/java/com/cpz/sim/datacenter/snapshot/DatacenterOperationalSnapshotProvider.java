package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds an immutable operational snapshot by combining energy,
 * temperature and health snapshots captured for the same simulation tick.
 *
 * <p>This provider does not advance the simulation and does not capture the
 * specialized snapshots itself. Its input snapshots must already represent
 * the same completed tick.</p>
 *
 * @author CPZ
 */
public final class DatacenterOperationalSnapshotProvider {

    private final Datacenter datacenter;
    private final List<ServerGroupDefinition> serverGroups;

    public DatacenterOperationalSnapshotProvider(Datacenter datacenter) {
        this(datacenter, List.of());
    }

    public DatacenterOperationalSnapshotProvider(
            Datacenter datacenter,
            List<ServerGroupDefinition> serverGroups
    ) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
        Objects.requireNonNull(serverGroups, "serverGroups must not be null");
        if (serverGroups.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("serverGroups must not contain null");
        validateServerGroups(serverGroups);
        this.serverGroups = List.copyOf(serverGroups);
    }

    private static <T> Map<ServerLocation, T> indexByLocation(
            Iterable<T> snapshots,
            Function<T, ServerLocation> locationExtractor,
            String snapshotName
    ) {
        Map<ServerLocation, T> snapshotsByLocation = new LinkedHashMap<>();
        for (T snapshot : snapshots) {
            Objects.requireNonNull(snapshot, snapshotName + " snapshots must not contain null");
            ServerLocation location = Objects.requireNonNull(locationExtractor.apply(snapshot), snapshotName + " snapshot location must not be null");
            T previous = snapshotsByLocation.put(location, snapshot);
            if (previous != null) throw new IllegalStateException("Duplicate " + snapshotName + " snapshot for server: " + location);
        }
        return Map.copyOf(snapshotsByLocation);
    }

    private static <T> T requireSnapshot(Map<ServerLocation, T> snapshots, ServerLocation location, String snapshotName) {
        T snapshot = snapshots.get(location);
        if (snapshot == null) throw new IllegalStateException("Missing " + snapshotName + " snapshot for server: " + location);
        return snapshot;
    }

    /**
     * Combines specialized snapshots captured for the same simulation tick.
     *
     * @param energySnapshot energy snapshot
     * @param temperatureSnapshot temperature snapshot
     * @param healthSnapshot health snapshot
     * @return complete operational snapshot
     * @throws IllegalArgumentException if the snapshots do not represent
     *                                  the same tick or elapsed time
     * @throws IllegalStateException if the server locations do not match
     *                               the current datacenter topology or if
     *                               specialized snapshots contain inconsistent
     *                               data for the same server
     */
    public DatacenterOperationalSnapshot snapshot(
            EnergyConsumptionSnapshot energySnapshot,
            TemperatureSnapshot temperatureSnapshot,
            HealthSnapshot healthSnapshot
    ) {
        Objects.requireNonNull(energySnapshot, "energySnapshot must not be null");
        Objects.requireNonNull(temperatureSnapshot, "temperatureSnapshot must not be null");
        Objects.requireNonNull(healthSnapshot, "healthSnapshot must not be null");
        validateSameCapture(energySnapshot, temperatureSnapshot, healthSnapshot);
        Map<ServerLocation, ServerEnergySnapshot> energyByLocation =
                indexByLocation(energySnapshot.servers(), ServerEnergySnapshot::location, "energy");
        Map<ServerLocation, ServerTemperatureSnapshot>
                temperatureByLocation = indexByLocation(temperatureSnapshot.servers(), ServerTemperatureSnapshot::location, "temperature");
        Map<ServerLocation, ServerHealthSnapshot> healthByLocation = indexByLocation(healthSnapshot.servers(), ServerHealthSnapshot::location, "health");
        validateLocations(energyByLocation.keySet(), temperatureByLocation.keySet(), healthByLocation.keySet());
        validateServerConsistency(energyByLocation, temperatureByLocation, healthByLocation);
        Map<RackLocation, RackOperationalSnapshot> rackSnapshots = snapshotRacks(energyByLocation, temperatureByLocation, healthByLocation);
        Map<String, ColumnOperationalSnapshot> columnSnapshots = snapshotColumns(rackSnapshots);
        Map<String, ServerGroupOperationalSnapshot> serverGroupSnapshots = snapshotServerGroups(energyByLocation, temperatureByLocation, healthByLocation);
        Optional<RackLocation> hottestRackLocation = findHottestRackLocation(rackSnapshots);
        double hottestRackAverageTemperatureCelsius = hottestRackLocation.map(rackSnapshots::get).map(RackOperationalSnapshot::averageOnlineTemperatureCelsius).orElse(Double.NaN);
        int totalOnlineServerCount = rackSnapshots.values().stream().mapToInt(RackOperationalSnapshot::onlineServerCount).sum();
        double totalOnlineUtilizationSum =
                rackSnapshots
                        .values()
                        .stream()
                        .filter(RackOperationalSnapshot::hasOnlineServers)
                        .mapToDouble(rackSnapshot -> rackSnapshot.averageOnlineUtilization() * rackSnapshot.onlineServerCount())
                        .sum();
        double totalItUtilization = totalOnlineServerCount == 0 ? Double.NaN : totalOnlineUtilizationSum / totalOnlineServerCount;
        double idleItPowerWatts = rackSnapshots.values().stream().mapToDouble(RackOperationalSnapshot::idlePowerWatts).sum();
        double maxItPowerWatts = rackSnapshots.values().stream().mapToDouble(RackOperationalSnapshot::maxPowerWatts).sum();
        double currentItPowerWatts = rackSnapshots.values().stream().mapToDouble(RackOperationalSnapshot::currentPowerWatts).sum();
        return new DatacenterOperationalSnapshot(
                energySnapshot.tickIndex(),
                energySnapshot.elapsedSeconds(),
                rackSnapshots,
                columnSnapshots,
                serverGroupSnapshots,
                temperatureSnapshot.ambientTemperatureCelsius(),
                hottestRackLocation,
                hottestRackAverageTemperatureCelsius,
                totalItUtilization,
                idleItPowerWatts,
                maxItPowerWatts,
                currentItPowerWatts,
                Double.NaN,
                Double.NaN,
                Double.NaN
        );
    }

    private void validateServerGroups(List<ServerGroupDefinition> definitions) {
        Set<String> codes = new LinkedHashSet<>();
        Set<ServerLocation> datacenterLocations = datacenter
                .getServers()
                .stream()
                .map(Server::getLocation)
                .collect(Collectors.toUnmodifiableSet());
        for (ServerGroupDefinition definition : definitions) {
            if (!codes.add(definition.code()))
                throw new IllegalArgumentException("Duplicate server group code: " + definition.code());
            for (ServerLocation location : definition.serverLocations()) {
                if (!datacenterLocations.contains(location))
                    throw new IllegalArgumentException("Server group " + definition.code() + " references unknown server location: " + location);
            }
        }
    }

    private Map<String, ServerGroupOperationalSnapshot> snapshotServerGroups(
            Map<ServerLocation, ServerEnergySnapshot> energyByLocation,
            Map<ServerLocation, ServerTemperatureSnapshot> temperatureByLocation,
            Map<ServerLocation, ServerHealthSnapshot> healthByLocation
    ) {
        Map<String, ServerGroupOperationalSnapshot> snapshots = new LinkedHashMap<>();
        for (ServerGroupDefinition definition : serverGroups)
            snapshots.put(definition.code(), snapshotServerGroup(definition, energyByLocation, temperatureByLocation, healthByLocation));
        return Map.copyOf(snapshots);
    }

    private ServerGroupOperationalSnapshot snapshotServerGroup(
            ServerGroupDefinition definition,
            Map<ServerLocation, ServerEnergySnapshot> energyByLocation,
            Map<ServerLocation, ServerTemperatureSnapshot> temperatureByLocation,
            Map<ServerLocation, ServerHealthSnapshot> healthByLocation
    ) {
        int installedServerCount = 0;
        int onlineServerCount = 0;
        double idlePowerWatts = 0.0;
        double maxPowerWatts = 0.0;
        double currentPowerWatts = 0.0;
        double onlineTemperatureSumCelsius = 0.0;
        double onlineUtilizationSum = 0.0;
        double maximumTemperatureCelsius = Double.NEGATIVE_INFINITY;
        ServerLocation maximumTemperatureLocation = null;
        List<ServerLocation> orderedLocations = definition
                .serverLocations()
                .stream()
                .sorted(Comparator.comparing(ServerLocation::code))
                .toList();
        for (ServerLocation location : orderedLocations) {
            ServerEnergySnapshot energy = requireSnapshot(energyByLocation, location, "energy");
            ServerTemperatureSnapshot temperature = requireSnapshot(temperatureByLocation, location, "temperature");
            ServerHealthSnapshot health = requireSnapshot(healthByLocation, location, "health");
            installedServerCount++;
            idlePowerWatts += energy.idlePowerWatts();
            maxPowerWatts += energy.maxPowerWatts();
            currentPowerWatts += energy.currentPowerWatts();
            if (maximumTemperatureLocation == null || temperature.temperatureCelsius() > maximumTemperatureCelsius) {
                maximumTemperatureCelsius = temperature.temperatureCelsius();
                maximumTemperatureLocation = location;
            }
            if (health.status() != HardwareStatus.OFFLINE) {
                onlineServerCount++;
                onlineTemperatureSumCelsius += temperature.temperatureCelsius();
                onlineUtilizationSum += energy.utilization();
            }
        }
        double averageOnlineTemperatureCelsius = onlineServerCount == 0 ? Double.NaN : onlineTemperatureSumCelsius / onlineServerCount;
        double averageOnlineUtilization = onlineServerCount == 0 ? Double.NaN : onlineUtilizationSum / onlineServerCount;
        return new ServerGroupOperationalSnapshot(
                definition.code(),
                installedServerCount,
                onlineServerCount,
                idlePowerWatts,
                maxPowerWatts,
                currentPowerWatts,
                averageOnlineTemperatureCelsius,
                averageOnlineUtilization,
                installedServerCount == 0 ? Double.NaN : maximumTemperatureCelsius,
                Optional.ofNullable(maximumTemperatureLocation)
        );
    }

    private void validateServerConsistency(
            Map<ServerLocation, ServerEnergySnapshot> energyByLocation,
            Map<ServerLocation, ServerTemperatureSnapshot> temperatureByLocation,
            Map<ServerLocation, ServerHealthSnapshot> healthByLocation
    ) {
        for (ServerLocation location : energyByLocation.keySet()) {
            ServerEnergySnapshot energy = requireSnapshot(energyByLocation, location, "energy");
            ServerTemperatureSnapshot temperature = requireSnapshot(temperatureByLocation, location, "temperature");
            ServerHealthSnapshot health = requireSnapshot(healthByLocation, location, "health");
            if (!energy.serverCode().equals(temperature.serverCode()) || !energy.serverCode().equals(health.serverCode()))
                throw new IllegalStateException("Inconsistent serverCode for server: " + location);
            if (energy.status() != temperature.status() || energy.status() != health.status())
                throw new IllegalStateException("Inconsistent status for server: " + location);
            if (Double.compare(energy.utilization(), temperature.utilization()) != 0 || Double.compare(energy.utilization(), health.utilization()) != 0)
                throw new IllegalStateException("Inconsistent utilization for server: " + location);
            if (Double.compare(energy.currentPowerWatts(), temperature.currentPowerWatts()) != 0)
                throw new IllegalStateException("Inconsistent currentPowerWatts for server: " + location);
            if (Double.compare(temperature.temperatureCelsius(), health.temperatureCelsius()) != 0)
                throw new IllegalStateException("Inconsistent temperatureCelsius for server: " + location);
        }
    }

    private Map<RackLocation, RackOperationalSnapshot> snapshotRacks(
            Map<ServerLocation, ServerEnergySnapshot> energyByLocation,
            Map<ServerLocation, ServerTemperatureSnapshot> temperatureByLocation,
            Map<ServerLocation, ServerHealthSnapshot> healthByLocation
    ) {
        Map<RackLocation, RackOperationalSnapshot> rackSnapshots = new LinkedHashMap<>();
        for (Rack rack : datacenter.getRacks()) {
            RackLocation rackLocation = rack.getLocation();
            RackOperationalSnapshot rackSnapshot = snapshotRack(rackLocation, energyByLocation, temperatureByLocation, healthByLocation);
            rackSnapshots.put(rackLocation, rackSnapshot);
        }
        return rackSnapshots;
    }

    private RackOperationalSnapshot snapshotRack(
            RackLocation rackLocation,
            Map<ServerLocation, ServerEnergySnapshot> energyByLocation,
            Map<ServerLocation, ServerTemperatureSnapshot> temperatureByLocation,
            Map<ServerLocation, ServerHealthSnapshot> healthByLocation
    ) {
        int installedServerCount = 0;
        int onlineServerCount = 0;
        double idlePowerWatts = 0.0;
        double maxPowerWatts = 0.0;
        double currentPowerWatts = 0.0;
        double onlineTemperatureSumCelsius = 0.0;
        double onlineUtilizationSum = 0.0;
        for (Server server : datacenter.getServers(rackLocation)) {
            ServerLocation serverLocation = server.getLocation();
            ServerEnergySnapshot energy = requireSnapshot(energyByLocation, serverLocation, "energy");
            ServerTemperatureSnapshot temperature = requireSnapshot(temperatureByLocation, serverLocation, "temperature");
            ServerHealthSnapshot health = requireSnapshot(healthByLocation, serverLocation, "health");
            installedServerCount++;
            idlePowerWatts += energy.idlePowerWatts();
            maxPowerWatts += energy.maxPowerWatts();
            currentPowerWatts += energy.currentPowerWatts();
            if (health.status() != HardwareStatus.OFFLINE) {
                onlineServerCount++;
                onlineTemperatureSumCelsius += temperature.temperatureCelsius();
                onlineUtilizationSum += energy.utilization();
            }
        }
        double averageOnlineTemperatureCelsius = onlineServerCount == 0 ? Double.NaN : onlineTemperatureSumCelsius / onlineServerCount;
        double averageOnlineUtilization = onlineServerCount == 0 ? Double.NaN : onlineUtilizationSum / onlineServerCount;
        return new RackOperationalSnapshot(
                rackLocation,
                installedServerCount,
                onlineServerCount,
                idlePowerWatts,
                maxPowerWatts,
                currentPowerWatts,
                averageOnlineTemperatureCelsius,
                averageOnlineUtilization
        );
    }

    private Map<String, ColumnOperationalSnapshot> snapshotColumns(Map<RackLocation, RackOperationalSnapshot> rackSnapshots) {
        Set<String> columnCodes = new LinkedHashSet<>();
        for (Rack rack : datacenter.getRacks()) columnCodes.add(rack.getLocation().column());
        Map<String, ColumnOperationalSnapshot> columnSnapshots = new LinkedHashMap<>();
        for (String columnCode : columnCodes) columnSnapshots.put(columnCode, snapshotColumn(columnCode, rackSnapshots));
        return columnSnapshots;
    }

    private ColumnOperationalSnapshot snapshotColumn(String columnCode, Map<RackLocation, RackOperationalSnapshot> rackSnapshots) {
        int installedServerCount = 0;
        int onlineServerCount = 0;
        double idlePowerWatts = 0.0;
        double maxPowerWatts = 0.0;
        double currentPowerWatts = 0.0;
        double onlineTemperatureSumCelsius = 0.0;
        double onlineUtilizationSum = 0.0;
        for (Map.Entry<RackLocation, RackOperationalSnapshot> entry : rackSnapshots.entrySet()) {
            if (!entry.getKey().column().equals(columnCode)) continue;
            RackOperationalSnapshot rackSnapshot = entry.getValue();
            installedServerCount += rackSnapshot.installedServerCount();
            onlineServerCount += rackSnapshot.onlineServerCount();
            idlePowerWatts += rackSnapshot.idlePowerWatts();
            maxPowerWatts += rackSnapshot.maxPowerWatts();
            currentPowerWatts += rackSnapshot.currentPowerWatts();
            if (rackSnapshot.hasOnlineServers()) {
                onlineTemperatureSumCelsius += rackSnapshot.averageOnlineTemperatureCelsius() * rackSnapshot.onlineServerCount();
                onlineUtilizationSum += rackSnapshot.averageOnlineUtilization() * rackSnapshot.onlineServerCount();
            }
        }
        double averageOnlineTemperatureCelsius = onlineServerCount == 0 ? Double.NaN : onlineTemperatureSumCelsius / onlineServerCount;
        double averageOnlineUtilization = onlineServerCount == 0 ? Double.NaN : onlineUtilizationSum / onlineServerCount;
        return new ColumnOperationalSnapshot(
                columnCode,
                installedServerCount,
                onlineServerCount,
                idlePowerWatts,
                maxPowerWatts,
                currentPowerWatts,
                averageOnlineTemperatureCelsius,
                averageOnlineUtilization
        );
    }

    private Optional<RackLocation> findHottestRackLocation(Map<RackLocation, RackOperationalSnapshot> rackSnapshots) {
        RackLocation hottestLocation = null;
        double hottestAverageTemperatureCelsius = Double.NEGATIVE_INFINITY;
        for (Map.Entry<RackLocation, RackOperationalSnapshot> entry : rackSnapshots.entrySet()) {
            RackOperationalSnapshot rackSnapshot = entry.getValue();
            if (!rackSnapshot.hasOnlineServers()) continue;
            double averageTemperatureCelsius = rackSnapshot.averageOnlineTemperatureCelsius();
            if (hottestLocation == null || averageTemperatureCelsius > hottestAverageTemperatureCelsius) {
                hottestLocation = entry.getKey();
                hottestAverageTemperatureCelsius = averageTemperatureCelsius;
            }
        }
        return Optional.ofNullable(hottestLocation);
    }

    private void validateSameCapture(
            EnergyConsumptionSnapshot energySnapshot,
            TemperatureSnapshot temperatureSnapshot,
            HealthSnapshot healthSnapshot
    ) {
        long tickIndex = energySnapshot.tickIndex();
        if (temperatureSnapshot.tickIndex() != tickIndex || healthSnapshot.tickIndex() != tickIndex)
            throw new IllegalArgumentException("All snapshots must have the same tickIndex");
        double elapsedSeconds = energySnapshot.elapsedSeconds();
        if (Double.compare(temperatureSnapshot.elapsedSeconds(), elapsedSeconds) != 0 || Double.compare(healthSnapshot.elapsedSeconds(), elapsedSeconds) != 0)
            throw new IllegalArgumentException("All snapshots must have the same elapsedSeconds");
    }

    private void validateLocations(
            Set<ServerLocation> energyLocations,
            Set<ServerLocation> temperatureLocations,
            Set<ServerLocation> healthLocations
    ) {
        Set<ServerLocation> datacenterLocations = datacenter.getServers().stream().map(Server::getLocation).collect(Collectors.toUnmodifiableSet());
        if (!energyLocations.equals(datacenterLocations))
            throw new IllegalStateException("Energy snapshot locations do not match the datacenter topology");
        if (!temperatureLocations.equals(datacenterLocations))
            throw new IllegalStateException("Temperature snapshot locations do not match the datacenter topology");
        if (!healthLocations.equals(datacenterLocations))
            throw new IllegalStateException("Health snapshot locations do not match the datacenter topology");
    }
}

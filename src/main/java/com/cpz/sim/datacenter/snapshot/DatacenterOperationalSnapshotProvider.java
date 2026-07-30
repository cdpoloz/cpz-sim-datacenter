package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds an immutable operational snapshot by combining energy,
 * temperature and health snapshots captured for the same simulation tick.
 *
 * <p>This provider does not advance the simulation and does not capture the
 * specialized snapshots itself. Its input snapshots must already represent
 * the same completed tick.
 *
 * @author CPZ
 */
public final class DatacenterOperationalSnapshotProvider {

    private final Datacenter datacenter;

    public DatacenterOperationalSnapshotProvider(Datacenter datacenter) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
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
            if (previous != null)
                throw new IllegalStateException("Duplicate " + snapshotName + " snapshot for server: " + location);
        }
        return Map.copyOf(snapshotsByLocation);
    }

    private static <T> T requireSnapshot(
            Map<ServerLocation, T> snapshots,
            ServerLocation location,
            String snapshotName
    ) {
        T snapshot = snapshots.get(location);
        if (snapshot == null)
            throw new IllegalStateException("Missing " + snapshotName + " snapshot for server: " + location);
        return snapshot;
    }

    /**
     * Combines specialized snapshots captured for the same simulation tick.
     *
     * @throws IllegalArgumentException if the snapshots do not represent
     *                                  the same tick or elapsed time
     * @throws IllegalStateException    if their server locations do not match
     *                                  the current datacenter topology
     */
    public DatacenterOperationalSnapshot snapshot(EnergyConsumptionSnapshot energySnapshot,
                                                  TemperatureSnapshot temperatureSnapshot,
                                                  HealthSnapshot healthSnapshot
    ) {
        Objects.requireNonNull(energySnapshot, "energySnapshot must not be null");
        Objects.requireNonNull(temperatureSnapshot, "temperatureSnapshot must not be null");
        Objects.requireNonNull(healthSnapshot, "healthSnapshot must not be null");
        validateSameCapture(energySnapshot, temperatureSnapshot, healthSnapshot);
        Map<ServerLocation, ServerEnergySnapshot> energyByLocation =
                indexByLocation(energySnapshot.servers(), ServerEnergySnapshot::location, "energy");
        Map<ServerLocation, ServerTemperatureSnapshot> temperatureByLocation =
                indexByLocation(temperatureSnapshot.servers(), ServerTemperatureSnapshot::location, "temperature");
        Map<ServerLocation, ServerHealthSnapshot> healthByLocation =
                indexByLocation(healthSnapshot.servers(), ServerHealthSnapshot::location, "health");
        validateLocations(energyByLocation.keySet(), temperatureByLocation.keySet(), healthByLocation.keySet());
        Map<RackLocation, RackOperationalSnapshot> rackSnapshots = new LinkedHashMap<>();
        for (Rack rack : datacenter.getRacks()) {
            RackLocation rackLocation = rack.getLocation();
            RackOperationalSnapshot rackSnapshot = snapshotRack(rackLocation, energyByLocation, temperatureByLocation, healthByLocation);
            rackSnapshots.put(rackLocation, rackSnapshot);
        }
        return new DatacenterOperationalSnapshot(energySnapshot.tickIndex(), energySnapshot.elapsedSeconds(), rackSnapshots);
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

    private void validateSameCapture(
            EnergyConsumptionSnapshot energySnapshot,
            TemperatureSnapshot temperatureSnapshot,
            HealthSnapshot healthSnapshot
    ) {
        long tickIndex = energySnapshot.tickIndex();
        if (temperatureSnapshot.tickIndex() != tickIndex || healthSnapshot.tickIndex() != tickIndex)
            throw new IllegalArgumentException("All snapshots must have the same tickIndex");
        double elapsedSeconds = energySnapshot.elapsedSeconds();
        if (Double.compare(temperatureSnapshot.elapsedSeconds(), elapsedSeconds) != 0
                || Double.compare(healthSnapshot.elapsedSeconds(), elapsedSeconds) != 0) {
            throw new IllegalArgumentException("All snapshots must have the same elapsedSeconds");
        }
    }

    private void validateLocations(
            Set<ServerLocation> energyLocations,
            Set<ServerLocation> temperatureLocations,
            Set<ServerLocation> healthLocations
    ) {
        Set<ServerLocation> datacenterLocations =
                datacenter.getServers()
                        .stream()
                        .map(Server::getLocation)
                        .collect(Collectors.toUnmodifiableSet());
        if (!energyLocations.equals(datacenterLocations))
            throw new IllegalStateException("Energy snapshot locations do not match the datacenter topology");
        if (!temperatureLocations.equals(datacenterLocations))
            throw new IllegalStateException("Temperature snapshot locations do not match the datacenter topology");
        if (!healthLocations.equals(datacenterLocations))
            throw new IllegalStateException("Health snapshot locations do not match the datacenter topology");
    }
}

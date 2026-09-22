package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.RackLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable operational view of the datacenter for one simulation tick.
 *
 * <p>The snapshot contains operational aggregates by rack, by column and for
 * the complete datacenter. Temperature and utilization averages include online
 * servers only.</p>
 *
 * <p>Cooling power represents the electrical power consumed by enabled
 * cooling units. Cooling power, total facility power and PUE are represented
 * by {@link Double#NaN} until a cooling system is available.</p>
 *
 * @author CPZ
 */
public record DatacenterOperationalSnapshot(
        long tickIndex,
        double elapsedSeconds,
        Map<RackLocation, RackOperationalSnapshot> racks,
        Map<String, ColumnOperationalSnapshot> columns,
        Map<String, ServerGroupOperationalSnapshot> serverGroups,
        double roomTemperatureCelsius,
        Optional<RackLocation> hottestRackLocation,
        double hottestRackAverageTemperatureCelsius,
        double totalItUtilization,
        double idleItPowerWatts,
        double maxItPowerWatts,
        double currentItPowerWatts,
        double coolingPowerWatts,
        double totalFacilityPowerWatts,
        double pue
) {

    private static final double POWER_EPSILON = 1.0e-9;

    public DatacenterOperationalSnapshot {
        if (tickIndex < 0L) throw new IllegalArgumentException("tickIndex must be >= 0");
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0)
            throw new IllegalArgumentException("elapsedSeconds must be finite and >= 0");
        Objects.requireNonNull(racks, "racks must not be null");
        for (Map.Entry<RackLocation, RackOperationalSnapshot> entry : racks.entrySet()) {
            RackLocation location = Objects.requireNonNull(entry.getKey(), "rack location must not be null");
            RackOperationalSnapshot rackSnapshot = Objects.requireNonNull(entry.getValue(), "rack snapshot must not be null");
            if (!location.equals(rackSnapshot.location()))
                throw new IllegalArgumentException("Rack map key does not match snapshot location: " + location);
        }
        racks = Map.copyOf(racks);
        Objects.requireNonNull(columns, "columns must not be null");
        for (Map.Entry<String, ColumnOperationalSnapshot> entry : columns.entrySet()) {
            String columnCode = Objects.requireNonNull(entry.getKey(), "column code must not be null");
            ColumnOperationalSnapshot columnSnapshot = Objects.requireNonNull(entry.getValue(), "column snapshot must not be null");
            if (!columnCode.equals(columnSnapshot.columnCode()))
                throw new IllegalArgumentException("Column map key does not match snapshot code: " + columnCode);
        }
        columns = Map.copyOf(columns);
        Objects.requireNonNull(serverGroups, "serverGroups must not be null");
        for (Map.Entry<String, ServerGroupOperationalSnapshot> entry : serverGroups.entrySet()) {
            String groupCode = Objects.requireNonNull(entry.getKey(), "server group code must not be null");
            ServerGroupOperationalSnapshot groupSnapshot = Objects.requireNonNull(entry.getValue(), "server group snapshot must not be null");
            if (!groupCode.equals(groupSnapshot.groupCode()))
                throw new IllegalArgumentException("Server group map key does not match snapshot code: " + groupCode);
        }
        serverGroups = Map.copyOf(serverGroups);
        if (!Double.isFinite(roomTemperatureCelsius)) throw new IllegalArgumentException("roomTemperatureCelsius must be finite");
        Objects.requireNonNull(hottestRackLocation, "hottestRackLocation must not be null");
        if (hottestRackLocation.isPresent()) {
            RackLocation location = hottestRackLocation.orElseThrow();
            RackOperationalSnapshot rackSnapshot = racks.get(location);
            if (rackSnapshot == null) throw new IllegalArgumentException("Hottest rack location does not exist in racks: " + location);
            if (!rackSnapshot.hasOnlineServers()) throw new IllegalArgumentException("Hottest rack must contain online servers");
            if (!Double.isFinite(hottestRackAverageTemperatureCelsius))
                throw new IllegalArgumentException("hottestRackAverageTemperatureCelsius must be finite when a hottest rack exists");
        } else if (!Double.isNaN(hottestRackAverageTemperatureCelsius))
            throw new IllegalArgumentException("hottestRackAverageTemperatureCelsius must be NaN when there is no hottest rack");
        int onlineServerCount = racks.values().stream().mapToInt(RackOperationalSnapshot::onlineServerCount).sum();
        if (onlineServerCount == 0) {
            if (!Double.isNaN(totalItUtilization))
                throw new IllegalArgumentException("totalItUtilization must be NaN when there are no online servers");
            if (hottestRackLocation.isPresent())
                throw new IllegalArgumentException("hottestRackLocation must be empty when there are no online servers");
        } else {
            if (!Double.isFinite(totalItUtilization) || totalItUtilization < 0.0 || totalItUtilization > 1.0)
                throw new IllegalArgumentException("totalItUtilization must be finite and between 0 and 1");
            if (hottestRackLocation.isEmpty())
                throw new IllegalArgumentException("hottestRackLocation must be present when online servers exist");
        }
        requireFiniteAndNonNegative(idleItPowerWatts, "idleItPowerWatts");
        requireFiniteAndNonNegative(maxItPowerWatts, "maxItPowerWatts");
        requireFiniteAndNonNegative(currentItPowerWatts, "currentItPowerWatts");
        if (idleItPowerWatts > maxItPowerWatts) throw new IllegalArgumentException("idleItPowerWatts must not exceed maxItPowerWatts");
        if (currentItPowerWatts > maxItPowerWatts) throw new IllegalArgumentException("currentItPowerWatts must not exceed maxItPowerWatts");
        validateFacilityPower(
                currentItPowerWatts,
                coolingPowerWatts,
                totalFacilityPowerWatts,
                pue
        );
    }

    public DatacenterOperationalSnapshot(
            long tickIndex,
            double elapsedSeconds,
            Map<RackLocation, RackOperationalSnapshot> racks,
            Map<String, ColumnOperationalSnapshot> columns,
            double roomTemperatureCelsius,
            Optional<RackLocation> hottestRackLocation,
            double hottestRackAverageTemperatureCelsius,
            double totalItUtilization,
            double idleItPowerWatts,
            double maxItPowerWatts,
            double currentItPowerWatts,
            double coolingPowerWatts,
            double totalFacilityPowerWatts,
            double pue
    ) {
        this(
                tickIndex,
                elapsedSeconds,
                racks,
                columns,
                Map.of(),
                roomTemperatureCelsius,
                hottestRackLocation,
                hottestRackAverageTemperatureCelsius,
                totalItUtilization,
                idleItPowerWatts,
                maxItPowerWatts,
                currentItPowerWatts,
                coolingPowerWatts,
                totalFacilityPowerWatts,
                pue
        );
    }

    public Optional<RackOperationalSnapshot> findRack(RackLocation location) {
        Objects.requireNonNull(location, "location must not be null");
        return Optional.ofNullable(racks.get(location));
    }

    public RackOperationalSnapshot getRack(RackLocation location) {
        return findRack(location).orElseThrow(() -> new IllegalArgumentException("No operational snapshot for rack: " + location));
    }

    public Optional<ColumnOperationalSnapshot> findColumn(String columnCode) {
        Objects.requireNonNull(columnCode, "columnCode must not be null");
        return Optional.ofNullable(columns.get(columnCode));
    }

    public ColumnOperationalSnapshot getColumn(String columnCode) {
        return findColumn(columnCode).orElseThrow(() -> new IllegalArgumentException("No operational snapshot for column: " + columnCode));
    }

    public Optional<ServerGroupOperationalSnapshot> findServerGroup(String groupCode) {
        Objects.requireNonNull(groupCode, "groupCode must not be null");
        return Optional.ofNullable(serverGroups.get(groupCode));
    }

    public ServerGroupOperationalSnapshot getServerGroup(String groupCode) {
        return findServerGroup(groupCode).orElseThrow(() -> new IllegalArgumentException("No operational snapshot for server group: " + groupCode));
    }

    public int serverGroupCount() {
        return serverGroups.size();
    }

    public int rackCount() {
        return racks.size();
    }

    public int columnCount() {
        return columns.size();
    }

    public boolean hasOnlineServers() {
        return racks.values().stream().anyMatch(RackOperationalSnapshot::hasOnlineServers);
    }

    public boolean hasCoolingData() {
        return !Double.isNaN(coolingPowerWatts);
    }

    public boolean hasFacilityPowerData() {
        return !Double.isNaN(totalFacilityPowerWatts);
    }

    public boolean hasPue() {
        return !Double.isNaN(pue);
    }

    private static void requireFiniteAndNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException(fieldName + " must be finite and >= 0");
    }

    private static void validateFacilityPower(
            double currentItPowerWatts,
            double coolingPowerWatts,
            double totalFacilityPowerWatts,
            double pue
    ) {
        boolean hasCoolingPower = !Double.isNaN(coolingPowerWatts);
        boolean hasTotalFacilityPower = !Double.isNaN(totalFacilityPowerWatts);
        boolean hasPue = !Double.isNaN(pue);
        if (!hasCoolingPower && !hasTotalFacilityPower && !hasPue) return;
        if (!hasCoolingPower || !hasTotalFacilityPower)
            throw new IllegalArgumentException("coolingPowerWatts and totalFacilityPowerWatts must both be available when cooling data exists");
        requireFiniteAndNonNegative(coolingPowerWatts, "coolingPowerWatts");
        requireFiniteAndNonNegative(totalFacilityPowerWatts, "totalFacilityPowerWatts");
        double expectedTotalFacilityPowerWatts = currentItPowerWatts + coolingPowerWatts;
        if (!nearlyEqual(totalFacilityPowerWatts, expectedTotalFacilityPowerWatts))
            throw new IllegalArgumentException("totalFacilityPowerWatts must equal currentItPowerWatts plus coolingPowerWatts");
        if (currentItPowerWatts == 0.0) {
            if (hasPue) throw new IllegalArgumentException("pue must be NaN when currentItPowerWatts is zero");
            return;
        }
        if (!hasPue) throw new IllegalArgumentException("pue must be available when currentItPowerWatts is greater than zero and cooling data exists");
        if (!Double.isFinite(pue) || pue < 1.0)
            throw new IllegalArgumentException("pue must be finite and greater than or equal to 1.0");
        double expectedPue = totalFacilityPowerWatts / currentItPowerWatts;
        if (!nearlyEqual(pue, expectedPue))
            throw new IllegalArgumentException("pue must equal totalFacilityPowerWatts divided by currentItPowerWatts");
    }

    private static boolean nearlyEqual(double left, double right) {
        return Math.abs(left - right) <= POWER_EPSILON;
    }
}

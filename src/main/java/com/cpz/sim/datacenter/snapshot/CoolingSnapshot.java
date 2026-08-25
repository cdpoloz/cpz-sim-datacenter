package com.cpz.sim.datacenter.snapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Represents the complete cooling-system state captured at a simulation tick.
 *
 * @param tickIndex simulation tick index
 * @param units cooling-unit snapshots in configuration order
 * @param zones cooling-zone snapshots in configuration order
 *
 * @author CPZ
 */
public record CoolingSnapshot(long tickIndex, List<CoolingUnitSnapshot> units, List<CoolingZoneSnapshot> zones) {

    /**
     * Creates a cooling snapshot.
     *
     * @throws NullPointerException if a collection or one of its elements is
     *         {@code null}
     * @throws IllegalArgumentException if {@code tickIndex} is negative, a
     *         collection is empty, or a unit or zone code is duplicated
     */
    public CoolingSnapshot {
        Objects.requireNonNull(units, "units must not be null");
        Objects.requireNonNull(zones, "zones must not be null");
        if (tickIndex < 0L) throw new IllegalArgumentException("tickIndex must be greater than or equal to 0");
        if (units.isEmpty()) throw new IllegalArgumentException("units must not be empty");
        if (zones.isEmpty()) throw new IllegalArgumentException("zones must not be empty");
        if (units.stream().anyMatch(Objects::isNull)) throw new NullPointerException("units must not contain null");
        if (zones.stream().anyMatch(Objects::isNull)) throw new NullPointerException("zones must not contain null");
        validateUniqueUnitCodes(units);
        validateUniqueZoneCodes(zones);
        units = List.copyOf(units);
        zones = List.copyOf(zones);
    }

    /**
     * Finds a cooling-unit snapshot by code.
     *
     * @param unitCode cooling-unit code
     * @return snapshot of the requested unit, if present
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     */
    public Optional<CoolingUnitSnapshot> findUnit(String unitCode) {
        Objects.requireNonNull(unitCode, "unitCode must not be null");
        return units.stream().filter(unit -> unit.unitCode().equals(unitCode)).findFirst();
    }

    /**
     * Finds a cooling-zone snapshot by code.
     *
     * @param zoneCode cooling-zone code
     * @return snapshot of the requested zone, if present
     *
     * @throws NullPointerException if {@code zoneCode} is {@code null}
     */
    public Optional<CoolingZoneSnapshot> findZone(String zoneCode) {
        Objects.requireNonNull(zoneCode, "zoneCode must not be null");
        return zones.stream().filter(zone -> zone.zoneCode().equals(zoneCode)).findFirst();
    }

    /**
     * Returns whether at least one cooling zone has a deficit.
     *
     * @return {@code true} if any zone has a cooling deficit
     */
    public boolean hasCoolingDeficit() {
        return zones.stream().anyMatch(CoolingZoneSnapshot::hasCoolingDeficit);
    }

    /**
     * Returns the total generated heat across all cooling zones.
     *
     * @return total generated heat in watts
     */
    public double totalGeneratedHeatWatts() {
        return zones.stream().mapToDouble(CoolingZoneSnapshot::generatedHeatWatts).sum();
    }

    /**
     * Returns the total uncovered thermal load.
     *
     * @return total cooling deficit in watts
     */
    public double totalCoolingDeficitWatts() {
        return zones.stream().mapToDouble(CoolingZoneSnapshot::coolingDeficitWatts).sum();
    }

    private static void validateUniqueUnitCodes(List<CoolingUnitSnapshot> units) {
        Set<String> unitCodes = new HashSet<>();
        for (CoolingUnitSnapshot unit : units) {
            if (!unitCodes.add(unit.unitCode())) throw new IllegalArgumentException("duplicate cooling-unit snapshot code: " + unit.unitCode());
        }
    }

    private static void validateUniqueZoneCodes(List<CoolingZoneSnapshot> zones) {
        Set<String> zoneCodes = new HashSet<>();
        for (CoolingZoneSnapshot zone : zones) {
            if (!zoneCodes.add(zone.zoneCode())) throw new IllegalArgumentException("duplicate cooling-zone snapshot code: " + zone.zoneCode());
        }
    }

    /**
     * Aggregates a subset of cooling zones into a higher-level cooling snapshot.
     *
     * @param groupCode external group code
     * @param zoneCodes cooling-zone codes to aggregate
     * @return aggregated cooling-zone group snapshot
     *
     * @throws NullPointerException if {@code groupCode}, {@code zoneCodes}, or one
     *         of its elements is {@code null}
     * @throws IllegalArgumentException if {@code groupCode} is blank,
     *         {@code zoneCodes} is empty, or a requested zone does not exist
     */
    public CoolingZoneGroupSnapshot aggregateZones(String groupCode, Collection<String> zoneCodes) {
        Objects.requireNonNull(groupCode, "groupCode must not be null");
        Objects.requireNonNull(zoneCodes, "zoneCodes must not be null");
        if (groupCode.isBlank()) throw new IllegalArgumentException("groupCode must not be blank");
        if (zoneCodes.isEmpty()) throw new IllegalArgumentException("zoneCodes must not be empty");
        LinkedHashSet<String> uniqueZoneCodes = new LinkedHashSet<>();
        for (String zoneCode : zoneCodes) {
            Objects.requireNonNull(zoneCode, "zoneCodes must not contain null");
            if (zoneCode.isBlank()) throw new IllegalArgumentException("zoneCodes must not contain blank values");
            uniqueZoneCodes.add(zoneCode);
        }
        List<CoolingZoneSnapshot> selectedZones = uniqueZoneCodes
                .stream()
                .map(zoneCode -> findZone(zoneCode).orElseThrow(() -> new IllegalArgumentException("unknown cooling-zone code: " + zoneCode)))
                .toList();
        double generatedHeatWatts = selectedZones.stream().mapToDouble(CoolingZoneSnapshot::generatedHeatWatts).sum();
        double availableCoolingCapacityWatts = selectedZones.stream().mapToDouble(CoolingZoneSnapshot::availableCoolingCapacityWatts).sum();
        double usedCoolingCapacityWatts = selectedZones.stream().mapToDouble(CoolingZoneSnapshot::usedCoolingCapacityWatts).sum();
        double coolingDeficitWatts = selectedZones.stream().mapToDouble(CoolingZoneSnapshot::coolingDeficitWatts).sum();
        double averageInletAirTemperatureCelsius = selectedZones.stream().mapToDouble(CoolingZoneSnapshot::inletAirTemperatureCelsius).average().orElseThrow();
        double averageExhaustAirTemperatureCelsius = selectedZones.stream().mapToDouble(CoolingZoneSnapshot::exhaustAirTemperatureCelsius).average().orElseThrow();
        double averageRecirculationFraction = selectedZones.stream().mapToDouble(CoolingZoneSnapshot::recirculationFraction).average().orElseThrow();
        return new CoolingZoneGroupSnapshot(
                groupCode,
                List.copyOf(uniqueZoneCodes),
                generatedHeatWatts,
                availableCoolingCapacityWatts,
                usedCoolingCapacityWatts,
                coolingDeficitWatts,
                averageInletAirTemperatureCelsius,
                averageExhaustAirTemperatureCelsius,
                averageRecirculationFraction
        );
    }

}
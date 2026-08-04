package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Defines the complete immutable configuration of the cooling system.
 *
 * @param zones cooling-zone definitions
 * @param units cooling-unit definitions
 * @param options physical and environmental system options
 *
 * @author CPZ
 */
public record CoolingConfiguration(
        List<CoolingZoneDefinition> zones,
        List<CoolingUnitDefinition> units,
        CoolingSystemOptions options
) {

    /**
     * Creates and validates a cooling-system configuration.
     *
     * @throws NullPointerException if an argument or collection element is
     *         {@code null}
     * @throws IllegalArgumentException if zones or units are empty, a code is
     *         duplicated, an influence references an unknown zone, or a server
     *         location belongs to more than one zone
     */
    public CoolingConfiguration {
        Objects.requireNonNull(zones, "zones must not be null");
        Objects.requireNonNull(units, "units must not be null");
        Objects.requireNonNull(options, "options must not be null");
        if (zones.stream().anyMatch(Objects::isNull)) throw new NullPointerException("zones must not contain null");
        if (units.stream().anyMatch(Objects::isNull)) throw new NullPointerException("units must not contain null");
        if (zones.isEmpty()) throw new IllegalArgumentException("zones must not be empty");
        if (units.isEmpty()) throw new IllegalArgumentException("units must not be empty");
        validateUniqueZoneCodes(zones);
        validateUniqueServerLocations(zones);
        validateUnits(units, zones);
        zones = List.copyOf(zones);
        units = List.copyOf(units);
    }

    private static void validateUniqueZoneCodes(List<CoolingZoneDefinition> zones) {
        Set<String> zoneCodes = new HashSet<>();
        for (CoolingZoneDefinition zone : zones) {
            if (!zoneCodes.add(zone.code())) throw new IllegalArgumentException("duplicate cooling-zone code: " + zone.code());
        }
    }

    private static void validateUniqueServerLocations(List<CoolingZoneDefinition> zones) {
        Set<ServerLocation> locations = new HashSet<>();
        for (CoolingZoneDefinition zone : zones) {
            for (ServerLocation location : zone.serverLocations()) {
                if (!locations.add(location)) throw new IllegalArgumentException("server location belongs to more than one cooling zone: " + location);
            }
        }
    }

    private static void validateUnits(List<CoolingUnitDefinition> units, List<CoolingZoneDefinition> zones) {
        Set<String> knownZoneCodes = new HashSet<>();
        Set<String> unitCodes = new HashSet<>();
        for (CoolingZoneDefinition zone : zones) knownZoneCodes.add(zone.code());
        for (CoolingUnitDefinition unit : units) {
            if (!unitCodes.add(unit.code())) throw new IllegalArgumentException("duplicate cooling-unit code: " + unit.code());
            for (CoolingZoneInfluence influence : unit.influences()) {
                if (!knownZoneCodes.contains(influence.zoneCode()))
                    throw new IllegalArgumentException("cooling unit " + unit.code() + " references unknown zone: " + influence.zoneCode());
            }
        }
    }

}
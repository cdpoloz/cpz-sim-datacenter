package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.RackLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable rack equipment configuration, independent of room cooling zones.
 * @author CPZ
 */
public record RackCoolingConfiguration(
        double airDensityKilogramsPerCubicMeter,
        double airSpecificHeatJoulesPerKilogramKelvin,
        List<RackCoolingUnitDefinition> units
) {
    public RackCoolingConfiguration {
        if (!Double.isFinite(airDensityKilogramsPerCubicMeter) || airDensityKilogramsPerCubicMeter <= 0.0)
            throw new IllegalArgumentException("airDensityKilogramsPerCubicMeter must be finite and > 0");
        if (!Double.isFinite(airSpecificHeatJoulesPerKilogramKelvin) || airSpecificHeatJoulesPerKilogramKelvin <= 0.0)
            throw new IllegalArgumentException("airSpecificHeatJoulesPerKilogramKelvin must be finite and > 0");
        Objects.requireNonNull(units, "units must not be null");
        units = List.copyOf(units);
        if (units.isEmpty()) throw new IllegalArgumentException("units must not be empty");
        Set<String> codes = new HashSet<>();
        Set<RackLocation> racks = new HashSet<>();
        for (RackCoolingUnitDefinition unit : units) {
            if (!codes.add(unit.code())) throw new IllegalArgumentException("duplicate rack cooling unit code: " + unit.code());
            if (!racks.add(unit.rackLocation()))
                throw new IllegalArgumentException("multiple cooling units for rack: " + unit.rackLocation().code());
        }
    }
}

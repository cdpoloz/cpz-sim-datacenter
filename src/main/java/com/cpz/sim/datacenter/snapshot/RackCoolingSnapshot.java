package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.RackLocation;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable completed-tick rack cooling capture; contains no room SUPPLY/EXHAUST units.
 * @author CPZ
 */
public record RackCoolingSnapshot(long tickIndex, double elapsedSeconds, List<RackCoolingUnitThermalSnapshot> units) {
    public RackCoolingSnapshot {
        if (tickIndex < 0 || !Double.isFinite(elapsedSeconds) || elapsedSeconds < 0)
            throw new IllegalArgumentException("rack cooling capture needs a valid tick and elapsed time");
        units = List.copyOf(Objects.requireNonNull(units, "units must not be null"));
        if (units.isEmpty()) throw new IllegalArgumentException("rack cooling units must not be empty");
        var codes = new HashSet<String>();
        var racks = new HashSet<RackLocation>();
        for (var unit : units) {
            if (!codes.add(unit.unitCode()) || !racks.add(unit.rackLocation()))
                throw new IllegalArgumentException("duplicate unit or rack in rack cooling capture");
        }
    }
    public Optional<RackCoolingUnitThermalSnapshot> findUnit(String code) {
        Objects.requireNonNull(code, "code must not be null");
        return units.stream().filter(unit -> unit.unitCode().equals(code)).findFirst();
    }
    public Optional<RackCoolingUnitThermalSnapshot> findRack(RackLocation rack) {
        Objects.requireNonNull(rack, "rack must not be null");
        return units.stream().filter(unit -> unit.rackLocation().equals(rack)).findFirst();
    }
    public double totalElectricalPowerWatts() { return units.stream().mapToDouble(RackCoolingUnitThermalSnapshot::currentElectricalPowerWatts).sum(); }
    public double totalActualCoolingPowerWatts() { return units.stream().mapToDouble(RackCoolingUnitThermalSnapshot::actualCoolingPowerWatts).sum(); }
    public double totalElectricalEnergyJoules() { return units.stream().mapToDouble(RackCoolingUnitThermalSnapshot::electricalEnergyJoules).sum(); }
}

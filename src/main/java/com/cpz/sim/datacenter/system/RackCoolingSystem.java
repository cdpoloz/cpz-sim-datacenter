package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.cooling.CoolingUnitState;
import com.cpz.sim.datacenter.cooling.RackCoolingConfiguration;
import com.cpz.sim.datacenter.cooling.RackCoolingUnitDefinition;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.snapshot.CoolingUnitSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative equipment controls for dedicated rack cooling. It does not
 * advance thermal state or integrate energy; the coupled solver will consume
 * enabled states and electrical power in a later stage. Commands affect future
 * captures only. Like CoolingSystem, use from the simulation's owning thread.
 *
 * @author CPZ
 */
public final class RackCoolingSystem {
    private final RackCoolingConfiguration configuration;
    private final Map<String, RackCoolingUnitDefinition> definitionsByCode = new LinkedHashMap<>();
    private final Map<RackLocation, RackCoolingUnitDefinition> definitionsByRack = new LinkedHashMap<>();
    private final Map<String, CoolingUnitState> statesByCode = new LinkedHashMap<>();

    public RackCoolingSystem(RackCoolingConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        for (var unit : configuration.units()) {
            definitionsByCode.put(unit.code(), unit);
            definitionsByRack.put(unit.rackLocation(), unit);
        }
        reset();
    }

    public RackCoolingConfiguration configuration() {
        return configuration;
    }

    public RackCoolingUnitDefinition definitionOf(String unitCode) {
        Objects.requireNonNull(unitCode, "unitCode must not be null");
        var definition = definitionsByCode.get(unitCode);
        if (definition == null) throw new IllegalArgumentException("unknown rack cooling unit code: " + unitCode);
        return definition;
    }

    public Optional<RackCoolingUnitDefinition> findUnitForRack(RackLocation location) {
        Objects.requireNonNull(location, "location must not be null");
        return Optional.ofNullable(definitionsByRack.get(location));
    }

    public CoolingUnitState stateOf(String unitCode) {
        definitionOf(unitCode);
        return statesByCode.get(unitCode);
    }

    public boolean isEnabled(String unitCode) {
        return stateOf(unitCode).enabled();
    }

    public void setEnabled(String unitCode, boolean enabled) {
        statesByCode.put(unitCode, stateOf(unitCode).withEnabled(enabled));
    }

    public void enable(String unitCode) {
        setEnabled(unitCode, true);
    }

    public void disable(String unitCode) {
        setEnabled(unitCode, false);
    }

    public boolean toggle(String unitCode) {
        var state = stateOf(unitCode).toggled();
        statesByCode.put(unitCode, state);
        return state.enabled();
    }

    /** Restores configured enabled states. Local air reset belongs to the thermal owner. */
    public void reset() {
        for (var unit : configuration.units())
            statesByCode.put(unit.code(), new CoolingUnitState(unit.code(), unit.initiallyEnabled()));
    }

    public double currentElectricalPowerWatts(String unitCode) {
        var unit = definitionOf(unitCode);
        return isEnabled(unitCode) ? unit.ratedElectricalPowerWatts() : 0.0;
    }

    /** Sum of enabled-unit electricity only; never nominal refrigeration capacity. */
    public double currentElectricalPowerWatts() {
        double power = configuration.units().stream().mapToDouble(unit -> currentElectricalPowerWatts(unit.code())).sum();
        if (!Double.isFinite(power)) throw new IllegalStateException("total rack cooling electrical power must be finite");
        return power;
    }

    /**
     * Read-only equipment projection in configuration order. As for room SUPPLY
     * unit snapshots, currentCoolingPowerWatts is enabled nominal capacity, not
     * actual heat removal. No rack air temperature or thermal energy is implied.
     * Rack identity and rejection destination remain in definitionOf(unitCode).
     */
    public List<CoolingUnitSnapshot> currentUnitSnapshots() {
        return configuration.units().stream().map(unit -> {
            boolean enabled = isEnabled(unit.code());
            return new CoolingUnitSnapshot(unit.code(), unit.type(), enabled,
                    enabled ? unit.ratedAirflowCubicMetersPerSecond() : 0.0,
                    enabled ? unit.ratedElectricalPowerWatts() : 0.0,
                    enabled ? unit.ratedCoolingCapacityWatts() : 0.0,
                    unit.ratedElectricalPowerWatts());
        }).toList();
    }
}

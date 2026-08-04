package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.CoolingUnitState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages the operational state and simulation of the configured cooling
 * units and zones.
 *
 * <p>The system owns the current enabled state of every cooling unit.
 * Definitions remain immutable and describe only permanent characteristics.</p>
 *
 * <p>Commands such as {@link #setEnabled(String, boolean)} affect subsequent
 * simulation ticks. Snapshots already produced by the system remain
 * immutable.</p>
 *
 * @author CPZ
 */
public final class CoolingSystem {

    private final CoolingConfiguration configuration;
    private final Map<String, CoolingUnitDefinition> definitionsByUnitCode;
    private final Map<String, CoolingUnitState> statesByUnitCode;

    /**
     * Creates a cooling system from the given configuration.
     *
     * <p>Each operational state is initialized from
     * {@link CoolingUnitDefinition#initiallyEnabled()}.</p>
     *
     * @param configuration complete cooling-system configuration
     *
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public CoolingSystem(CoolingConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.definitionsByUnitCode = new LinkedHashMap<>();
        this.statesByUnitCode = new LinkedHashMap<>();
        for (CoolingUnitDefinition definition : configuration.units()) {
            definitionsByUnitCode.put(definition.code(), definition);
            statesByUnitCode.put(definition.code(), new CoolingUnitState(definition.code(), definition.initiallyEnabled()));
        }
    }

    /**
     * Returns the immutable configuration used by this system.
     *
     * @return cooling-system configuration
     */
    public CoolingConfiguration configuration() {
        return configuration;
    }

    /**
     * Returns whether the requested cooling unit is enabled.
     *
     * @param unitCode cooling-unit code
     * @return {@code true} if the unit is enabled
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     * @throws IllegalArgumentException if no unit has the requested code
     */
    public boolean isEnabled(String unitCode) {
        return stateOf(unitCode).enabled();
    }

    /**
     * Sets the enabled state of a cooling unit.
     *
     * <p>If the unit already has the requested state, this method has no
     * observable effect.</p>
     *
     * @param unitCode cooling-unit code
     * @param enabled requested enabled state
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     * @throws IllegalArgumentException if no unit has the requested code
     */
    public void setEnabled(String unitCode, boolean enabled) {
        CoolingUnitState currentState = stateOf(unitCode);
        statesByUnitCode.put(unitCode, currentState.withEnabled(enabled));
    }

    /**
     * Enables a cooling unit.
     *
     * @param unitCode cooling-unit code
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     * @throws IllegalArgumentException if no unit has the requested code
     */
    public void enable(String unitCode) {
        setEnabled(unitCode, true);
    }

    /**
     * Disables a cooling unit.
     *
     * @param unitCode cooling-unit code
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     * @throws IllegalArgumentException if no unit has the requested code
     */
    public void disable(String unitCode) {
        setEnabled(unitCode, false);
    }

    /**
     * Inverts the enabled state of a cooling unit.
     *
     * @param unitCode cooling-unit code
     * @return the new enabled state
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     * @throws IllegalArgumentException if no unit has the requested code
     */
    public boolean toggle(String unitCode) {
        CoolingUnitState newState = stateOf(unitCode).toggled();
        statesByUnitCode.put(unitCode, newState);
        return newState.enabled();
    }

    /**
     * Returns the current immutable state of a cooling unit.
     *
     * @param unitCode cooling-unit code
     * @return current cooling-unit state
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     * @throws IllegalArgumentException if no unit has the requested code
     */
    public CoolingUnitState stateOf(String unitCode) {
        Objects.requireNonNull(unitCode, "unitCode must not be null");
        CoolingUnitState state = statesByUnitCode.get(unitCode);
        if (state == null) throw new IllegalArgumentException("unknown cooling-unit code: " + unitCode);
        return state;
    }

    /**
     * Returns the immutable definition of a cooling unit.
     *
     * @param unitCode cooling-unit code
     * @return cooling-unit definition
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     * @throws IllegalArgumentException if no unit has the requested code
     */
    public CoolingUnitDefinition definitionOf(String unitCode) {
        Objects.requireNonNull(unitCode, "unitCode must not be null");
        CoolingUnitDefinition definition = definitionsByUnitCode.get(unitCode);
        if (definition == null) throw new IllegalArgumentException("unknown cooling-unit code: " + unitCode);
        return definition;
    }

}
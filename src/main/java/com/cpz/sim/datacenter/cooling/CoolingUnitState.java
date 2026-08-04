package com.cpz.sim.datacenter.cooling;

import java.util.Objects;

/**
 * Represents the current operational state of a cooling unit.
 *
 * <p>This value is immutable. Changing the enabled state produces a new
 * instance, allowing completed simulation ticks and snapshots to remain
 * unaffected by later commands.</p>
 *
 * @param unitCode code of the cooling unit
 * @param enabled whether the cooling unit is currently enabled
 *
 * @author CPZ
 */
public record CoolingUnitState(
        String unitCode,
        boolean enabled
) {

    /**
     * Creates a cooling-unit state.
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     * @throws IllegalArgumentException if {@code unitCode} is blank
     */
    public CoolingUnitState {
        Objects.requireNonNull(unitCode, "unitCode must not be null");
        if (unitCode.isBlank()) throw new IllegalArgumentException("unitCode must not be blank");
    }

    /**
     * Returns a state with the requested enabled value.
     *
     * <p>If the requested value is already present, this instance is
     * returned.</p>
     *
     * @param enabled requested enabled state
     * @return this state or a new state containing the requested value
     */
    public CoolingUnitState withEnabled(boolean enabled) {
        if (this.enabled == enabled) return this;
        return new CoolingUnitState(unitCode, enabled);
    }

    /**
     * Returns a state with the enabled value inverted.
     *
     * @return toggled cooling-unit state
     */
    public CoolingUnitState toggled() {
        return new CoolingUnitState(unitCode, !enabled);
    }

}
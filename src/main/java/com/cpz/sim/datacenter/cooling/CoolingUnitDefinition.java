package com.cpz.sim.datacenter.cooling;

import java.util.List;

/**
 * Defines the immutable characteristics shared by all cooling units.
 *
 * <p>The operational enabled state is intentionally not stored in this
 * definition. It will be managed by {@code CoolingSystem}.</p>
 *
 * @author CPZ
 */
public sealed interface CoolingUnitDefinition permits SupplyCoolingUnitDefinition, ExhaustCoolingUnitDefinition {

    /**
     * Returns the unique unit code.
     *
     * @return cooling-unit code
     */
    String code();

    /**
     * Returns the functional unit type.
     *
     * @return cooling-unit type
     */
    CoolingUnitType type();

    /**
     * Returns the nominal airflow produced by the unit.
     *
     * @return airflow in cubic metres per second
     */
    double ratedAirflowCubicMetersPerSecond();

    /**
     * Returns the zones affected by the unit.
     *
     * @return immutable influence list
     */
    List<CoolingZoneInfluence> influences();

    /**
     * Returns whether the unit must initially be enabled.
     *
     * @return initial enabled state
     */
    boolean initiallyEnabled();

}
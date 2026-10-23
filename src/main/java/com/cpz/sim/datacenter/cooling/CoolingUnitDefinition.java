package com.cpz.sim.datacenter.cooling;

import java.util.List;

/**
 * Defines the immutable characteristics shared by all cooling units.
 *
 * <p>The operational enabled state is intentionally not stored in this
 * definition. It is managed by {@code CoolingSystem} for room units and
 * {@code RackCoolingSystem} for rack units.</p>
 *
 * @author CPZ
 */
public sealed interface CoolingUnitDefinition permits SupplyCoolingUnitDefinition, ExhaustCoolingUnitDefinition, RackCoolingUnitDefinition {

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
     * Returns the nominal electrical power consumed while the unit is enabled.
     *
     * @return electrical power in watts
     */
    double ratedElectricalPowerWatts();

    /**
     * Returns the legacy room zones affected by the unit. Dedicated rack
     * units return an empty list and identify their rack separately.
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

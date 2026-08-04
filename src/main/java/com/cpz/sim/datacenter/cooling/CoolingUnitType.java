package com.cpz.sim.datacenter.cooling;

/**
 * Identifies the functional type of a cooling unit.
 *
 * <p>A {@link #SUPPLY} unit supplies cooled air to one or more cooling
 * zones. An {@link #EXHAUST} unit removes hot air and reduces its
 * recirculation.</p>
 *
 * @author CPZ
 */
public enum CoolingUnitType {

    /**
     * Supplies cooled air to cooling zones.
     */
    SUPPLY,

    /**
     * Extracts hot air from cooling zones.
     */
    EXHAUST

}
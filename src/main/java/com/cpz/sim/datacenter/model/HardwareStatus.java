package com.cpz.sim.datacenter.model;

/**
 * Current operational status of an installed server.
 *
 * <p>The configured value is the initial status. During simulation,
 * non-{@link #OFFLINE} values may be recalculated as {@link #OK} or
 * {@link #ALERT} by the server health system. {@code OFFLINE} has priority and
 * is preserved by that system.
 *
 * @author CPZ
 */
public enum HardwareStatus {
    OFFLINE,
    OK,
    ALERT
}

package com.cpz.sim.datacenter.health;

/**
 * Identifies a condition that can place an installed server in
 * {@link com.cpz.sim.datacenter.model.HardwareStatus#ALERT}.
 *
 * @author CPZ
 */
public enum ServerAlertReason {
    HIGH_UTILIZATION,
    HIGH_TEMPERATURE,
    HIGH_POWER,
    FAN_FAILURE,
    SENSOR_FAILURE,
    POWER_SUPPLY_FAILURE
}

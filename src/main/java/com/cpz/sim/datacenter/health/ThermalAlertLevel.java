package com.cpz.sim.datacenter.health;

/** Backend-evaluated thermal level; NORMAL does not imply overall server health. */
public enum ThermalAlertLevel {
    NORMAL,
    ELEVATED,
    CRITICAL
}

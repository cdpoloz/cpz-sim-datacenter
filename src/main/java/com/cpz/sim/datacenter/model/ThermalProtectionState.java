package com.cpz.sim.datacenter.model;

/** Independent of health severity and operator-controlled hardware availability. */
public enum ThermalProtectionState {
    NORMAL, THROTTLED, SHUTDOWN
}

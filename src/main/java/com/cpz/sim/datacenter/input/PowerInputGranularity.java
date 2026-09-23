package com.cpz.sim.datacenter.input;

/**
 * Declares the telemetry granularity used by power-driven simulations.
 *
 * @author CPZ
 */
public enum PowerInputGranularity {

    /**
     * Power is supplied directly for each installed server.
     */
    SERVER,

    /**
     * Power is supplied as an aggregate rack value and adapted to installed
     * servers.
     */
    RACK
}

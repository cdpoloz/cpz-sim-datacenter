package com.cpz.sim.datacenter.input;

/**
 * Declares the telemetry granularity used by temperature-driven simulations.
 *
 * @author CPZ
 */
public enum TemperatureInputGranularity {

    /**
     * Temperature is supplied as an aggregate rack observation and applied to
     * installed online servers in that rack.
     */
    RACK
}

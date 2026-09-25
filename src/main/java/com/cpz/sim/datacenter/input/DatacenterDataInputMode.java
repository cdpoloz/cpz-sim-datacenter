package com.cpz.sim.datacenter.input;

/**
 * Declares which measured or simulated variable is the authoritative external
 * input for a datacenter simulation pipeline.
 *
 * <p>{@link #UTILIZATION_DRIVEN} supplies utilization and lets the backend
 * derive power and temperature. {@link #POWER_DRIVEN} supplies electrical power
 * and keeps utilization as an inferred compatibility value.
 * {@link #TEMPERATURE_DRIVEN} supplies observed rack or hot-aisle temperature
 * and infers power and utilization from that temperature.</p>
 *
 * @author CPZ
 */
public enum DatacenterDataInputMode {

    /**
     * Server utilization is supplied by a workload source. IT power and server
     * temperature are calculated by the backend.
     */
    UTILIZATION_DRIVEN,

    /**
     * Server electrical power is supplied by an external source or telemetry
     * adapter. Utilization is not the authoritative driver.
     */
    POWER_DRIVEN,

    /**
     * Rack or hot-aisle temperature is supplied by an external source or
     * telemetry adapter. Thermal readings are not derived from utilization and
     * power.
     */
    TEMPERATURE_DRIVEN
}

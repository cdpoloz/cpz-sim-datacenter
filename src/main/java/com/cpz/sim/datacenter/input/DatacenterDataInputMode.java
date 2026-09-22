package com.cpz.sim.datacenter.input;

/**
 * Declares which measured or simulated variable is the authoritative external
 * input for a datacenter simulation pipeline.
 *
 * <p>The current production pipeline is {@link #UTILIZATION_DRIVEN}: a
 * utilization source feeds the backend, and the backend derives power,
 * temperature, health, energy, cooling, and aggregate snapshots. The remaining
 * modes are explicit extension points for future telemetry-backed simulations
 * and digital-twin workflows.</p>
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
     * Server or rack temperature is supplied by an external source or telemetry
     * adapter. Thermal readings are not derived from utilization and power.
     */
    TEMPERATURE_DRIVEN
}

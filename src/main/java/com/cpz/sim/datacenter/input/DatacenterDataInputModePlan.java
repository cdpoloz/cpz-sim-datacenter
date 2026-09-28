package com.cpz.sim.datacenter.input;

import java.util.Objects;

/**
 * Describes which state transitions the backend should calculate for a given
 * data input mode.
 *
 * <p>This class is intentionally descriptive. Applications may use it to
 * register the appropriate systems in the right order without inferring the
 * active model from implementation class names.</p>
 *
 * @param mode active data input mode
 * @param updatesUtilization whether the backend should update utilization from
 *                           a utilization source
 * @param updatesPowerFromUtilization whether the backend should derive server
 *                                    power from utilization
 * @param acceptsPowerInput whether a power input system may supply server
 *                          power directly
 * @param updatesTemperatureFromPower whether the backend should derive server
 *                                    temperature from power
 * @param acceptsTemperatureInput whether a temperature input system may supply
 *                                server temperature directly
 *
 * @author CPZ
 */
public record DatacenterDataInputModePlan(
        DatacenterDataInputMode mode,
        boolean updatesUtilization,
        boolean updatesPowerFromUtilization,
        boolean acceptsPowerInput,
        boolean updatesTemperatureFromPower,
        boolean acceptsTemperatureInput
) {

    public DatacenterDataInputModePlan {
        Objects.requireNonNull(mode, "mode must not be null");
        if (updatesPowerFromUtilization && acceptsPowerInput)
            throw new IllegalArgumentException("Power cannot be both derived from utilization and supplied directly");
        if (updatesTemperatureFromPower && acceptsTemperatureInput)
            throw new IllegalArgumentException("Temperature cannot be both derived from power and supplied directly");
    }
}

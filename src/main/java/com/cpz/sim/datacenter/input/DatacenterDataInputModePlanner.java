package com.cpz.sim.datacenter.input;

import java.util.Objects;

/**
 * Builds the backend calculation plan associated with each data input mode.
 *
 * @author CPZ
 */
public final class DatacenterDataInputModePlanner {

    private DatacenterDataInputModePlanner() {
    }

    /**
     * Returns the calculation plan for the requested mode.
     *
     * @param mode data input mode
     * @return immutable mode plan
     */
    public static DatacenterDataInputModePlan planFor(DatacenterDataInputMode mode) {
        Objects.requireNonNull(mode, "mode must not be null");
        return switch (mode) {
            case UTILIZATION_DRIVEN -> new DatacenterDataInputModePlan(
                    mode,
                    true,
                    true,
                    false,
                    true,
                    false
            );
            case POWER_DRIVEN -> new DatacenterDataInputModePlan(
                    mode,
                    false,
                    false,
                    true,
                    true,
                    false
            );
            case TEMPERATURE_DRIVEN -> new DatacenterDataInputModePlan(
                    mode,
                    false,
                    false,
                    false,
                    false,
                    true
            );
        };
    }
}

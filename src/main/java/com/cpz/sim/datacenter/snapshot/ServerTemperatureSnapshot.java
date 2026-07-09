package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;

/**
 * Immutable per-server temperature data for one tick.
 *
 * <p>{@code temperatureCelsius} represents a simplified internal server
 * temperature derived from server power. It is not rack inlet or room
 * temperature.
 */
public record ServerTemperatureSnapshot(
        String serverCode,
        RackCode rackCode,
        String slot,
        HardwareStatus status,
        double utilization,
        double currentPowerWatts,
        double temperatureCelsius
) {
}

package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ServerLocation;

/**
 * Immutable per-server temperature data for one tick.
 *
 * <p>{@code column + rackCode + slot} identifies the physical server location.
 *
 * <p>{@code temperatureCelsius} represents a simplified internal server
 * temperature derived from server power. It is not rack inlet or room
 * temperature.
 *
 * <p>When captured after the health system, {@code status} is the status
 * calculated for the tick, with {@code OFFLINE} preserved.
 */
public record ServerTemperatureSnapshot(
        String serverCode,
        String column,
        RackCode rackCode,
        String slot,
        HardwareStatus status,
        double utilization,
        double currentPowerWatts,
        double temperatureCelsius
) {
    /**
     * Returns the physical location represented by this snapshot.
     */
    public ServerLocation location() {
        return new ServerLocation(
                column,
                rackCode,
                slot
        );
    }
}

package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.Objects;

/**
 * Immutable per-server temperature data for one tick.
 *
 * <p>{@code column + rackCode + slot} identifies the physical server location.
 *
 * <p>{@code temperatureCelsius} represents the effective server temperature
 * included in this snapshot. Its source depends on the active data input mode:
 * in utilization-driven and power-driven simulations it is typically calculated
 * by the thermal model, while in rack-level temperature-driven simulations it
 * may be an observed rack temperature applied to the server. This snapshot does
 * not encode whether the value is source data or inferred/calculated state.
 *
 * <p>When captured after the health system, {@code status} is the status
 * calculated for the tick, with {@code OFFLINE} preserved.
 *
 * @author CPZ
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
    public ServerTemperatureSnapshot {
        Objects.requireNonNull(serverCode, "serverCode must not be null");
        if (serverCode.isBlank()) throw new IllegalArgumentException("serverCode must not be blank");
        Objects.requireNonNull(column, "column must not be null");
        if (column.isBlank()) throw new IllegalArgumentException("column must not be blank");
        Objects.requireNonNull(rackCode, "rackCode must not be null");
        Objects.requireNonNull(slot, "slot must not be null");
        if (slot.isBlank()) throw new IllegalArgumentException("slot must not be blank");
        Objects.requireNonNull(status, "status must not be null");
        if (!Double.isFinite(utilization) || utilization < 0.0 || utilization > 1.0)
            throw new IllegalArgumentException("utilization must be finite and between 0 and 1");
        if (!Double.isFinite(currentPowerWatts) || currentPowerWatts < 0.0)
            throw new IllegalArgumentException("currentPowerWatts must be finite and >= 0");
        if (!Double.isFinite(temperatureCelsius))
            throw new IllegalArgumentException("temperatureCelsius must be finite");
    }

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

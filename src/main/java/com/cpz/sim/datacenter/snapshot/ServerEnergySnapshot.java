package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.Objects;

/**
 * Immutable per-server energy data for one tick.
 *
 * <p>{@code column + rackCode + slot} identifies the physical server location.
 * When captured after the health system, {@code status} is the status calculated
 * for the tick, with {@code OFFLINE} preserved.
 *
 * @author CPZ
 */
public record ServerEnergySnapshot(
        String serverCode,
        String column,
        RackCode rackCode,
        String slot,
        HardwareStatus status,
        double utilization,
        float idlePowerWatts,
        float maxPowerWatts,
        float currentPowerWatts
) {
    public ServerEnergySnapshot {
        serverCode = requireText(serverCode, "serverCode");
        column = requireText(column, "column");
        Objects.requireNonNull(rackCode, "rackCode must not be null");
        slot = requireText(slot, "slot");
        Objects.requireNonNull(status, "status must not be null");
        if (!Double.isFinite(utilization) || utilization < 0.0 || utilization > 1.0)
            throw new IllegalArgumentException("utilization must be finite and between 0 and 1");
        if (!Float.isFinite(idlePowerWatts) || idlePowerWatts < 0.0f)
            throw new IllegalArgumentException("idlePowerWatts must be finite and >= 0");
        if (!Float.isFinite(maxPowerWatts) || maxPowerWatts <= idlePowerWatts)
            throw new IllegalArgumentException("maxPowerWatts must be finite and greater than idlePowerWatts");
        if (!Float.isFinite(currentPowerWatts) || currentPowerWatts < 0.0f)
            throw new IllegalArgumentException("currentPowerWatts must be finite and >= 0");
        if (currentPowerWatts > maxPowerWatts)
            throw new IllegalArgumentException("currentPowerWatts must not exceed maxPowerWatts");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    /**
     * Returns the physical location represented by this snapshot.
     */
    public ServerLocation location() {
        return new ServerLocation(column, rackCode, slot);
    }
}

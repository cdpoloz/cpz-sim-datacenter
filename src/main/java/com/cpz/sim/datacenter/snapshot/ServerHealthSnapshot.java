package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable per-server health data for a simulation tick.
 *
 * <p>When produced after the health system evaluates the tick, {@code status}
 * is calculated from the active reasons, except that {@code OFFLINE} is
 * preserved.
 *
 * @author CPZ
 */
public record ServerHealthSnapshot(
        String serverCode,
        String column,
        RackCode rackCode,
        String slot,
        HardwareStatus status,
        Set<ServerAlertReason> alertReasons,
        double utilization,
        double temperatureCelsius
) {
    public ServerHealthSnapshot {
        Objects.requireNonNull(serverCode, "serverCode must not be null.");
        if (serverCode.isBlank()) throw new IllegalArgumentException("serverCode must not be blank");
        Objects.requireNonNull(column, "column must not be null.");
        if (column.isBlank()) throw new IllegalArgumentException("column must not be blank");
        Objects.requireNonNull(rackCode, "rackCode must not be null.");
        Objects.requireNonNull(slot, "slot must not be null.");
        if (slot.isBlank()) throw new IllegalArgumentException("slot must not be blank");
        Objects.requireNonNull(status, "status must not be null.");
        Objects.requireNonNull(alertReasons, "alertReasons must not be null.");
        alertReasons = Set.copyOf(alertReasons);
        if (!Double.isFinite(utilization) || utilization < 0.0 || utilization > 1.0)
            throw new IllegalArgumentException("utilization must be finite and between 0 and 1");
        if (!Double.isFinite(temperatureCelsius)) throw new IllegalArgumentException("temperatureCelsius must be finite.");
    }

    public boolean hasAlerts() {
        return !alertReasons.isEmpty();
    }

    public boolean hasAlertReason(ServerAlertReason reason) {
        return alertReasons.contains(reason);
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

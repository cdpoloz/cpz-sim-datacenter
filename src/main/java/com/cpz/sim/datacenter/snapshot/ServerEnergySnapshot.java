package com.cpz.sim.datacenter.snapshot;

import java.util.Objects;

/**
 * @author CPZ
 */
public record ServerEnergySnapshot(
        String serverCode,
        String rackCode,
        String slot,
        String status,
        float utilization,
        float currentPowerWatts
) {
    public ServerEnergySnapshot {
        serverCode = requireText(serverCode, "serverCode");
        rackCode = requireText(rackCode, "rackCode");
        slot = requireText(slot, "slot");
        status = requireText(status, "status");
        if (!Float.isFinite(utilization) || utilization < 0.0f || utilization > 1.0f)
            throw new IllegalArgumentException("utilization must be finite and between 0 and 1");
        if (!Float.isFinite(currentPowerWatts) || currentPowerWatts < 0.0f)
            throw new IllegalArgumentException("currentPowerWatts must be finite and >= 0");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}

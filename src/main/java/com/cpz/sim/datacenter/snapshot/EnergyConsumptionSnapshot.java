package com.cpz.sim.datacenter.snapshot;

import java.util.List;
import java.util.Objects;

/**
 * @author CPZ
 */
public record EnergyConsumptionSnapshot(
        long tickIndex,
        double elapsedSeconds,
        double totalItPowerWatts,
        double consumedEnergyWh,
        List<ServerEnergySnapshot> servers
) {
    public EnergyConsumptionSnapshot {
        if (tickIndex < 0)
            throw new IllegalArgumentException("tickIndex must be >= 0");
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0)
            throw new IllegalArgumentException("elapsedSeconds must be finite and >= 0");
        if (!Double.isFinite(totalItPowerWatts) || totalItPowerWatts < 0.0)
            throw new IllegalArgumentException("totalItPowerWatts must be finite and >= 0");
        if (!Double.isFinite(consumedEnergyWh) || consumedEnergyWh < 0.0)
            throw new IllegalArgumentException("consumedEnergyWh must be finite and >= 0");
        servers = List.copyOf(Objects.requireNonNull(servers, "servers must not be null"));
    }

    public double consumedEnergyKWh() {
        return consumedEnergyWh / 1000.0;
    }

    public int serverCount() {
        return servers.size();
    }
}

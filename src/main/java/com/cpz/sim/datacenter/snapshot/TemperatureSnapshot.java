package com.cpz.sim.datacenter.snapshot;

import java.util.List;

/**
 * Immutable temperature view captured after a simulation tick.
 *
 * <p>{@code ambientTemperatureCelsius} is the ambient input used by the
 * simplified model. Per-server temperatures are representative internal server
 * temperatures, not room or inlet air temperatures.
 */
public record TemperatureSnapshot(
        long tickIndex,
        double elapsedSeconds,
        double ambientTemperatureCelsius,
        List<ServerTemperatureSnapshot> servers
) {
    public int serverCount() {
        return servers.size();
    }

    public double averageTemperatureCelsius() {
        if (servers.isEmpty()) return 0.0;
        return servers.stream()
                .mapToDouble(ServerTemperatureSnapshot::temperatureCelsius)
                .average()
                .orElse(0.0);
    }

    public double maxTemperatureCelsius() {
        return servers.stream()
                .mapToDouble(ServerTemperatureSnapshot::temperatureCelsius)
                .max()
                .orElse(0.0);
    }
}

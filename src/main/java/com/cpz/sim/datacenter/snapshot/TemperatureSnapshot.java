package com.cpz.sim.datacenter.snapshot;

import java.util.List;

/**
 * Immutable temperature view captured after a simulation tick.
 *
 * <p>{@code ambientTemperatureCelsius} is the ambient input used by the
 * simplified model. Per-server temperatures are representative internal server
 * temperatures, not room or inlet air temperatures.
 *
 * @author CPZ
 */
public record TemperatureSnapshot(
        long tickIndex,
        double elapsedSeconds,
        double ambientTemperatureCelsius,
        List<ServerTemperatureSnapshot> servers
) {

    public TemperatureSnapshot {
        if (tickIndex < 0L) throw new IllegalArgumentException("tickIndex must be greater than or equal to zero.");
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0)
            throw new IllegalArgumentException("elapsedSeconds must be finite and greater than or equal to zero.");
        if (!Double.isFinite(ambientTemperatureCelsius))
            throw new IllegalArgumentException("ambientTemperatureCelsius must be finite.");
        servers = List.copyOf(java.util.Objects.requireNonNull(servers, "servers must not be null."));
    }

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

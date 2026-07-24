package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.health.ServerAlertReason;

import java.util.List;
import java.util.Objects;

/**
 * Immutable server health view captured after the server health system has
 * evaluated a simulation tick.
 *
 * @author CPZ
 */
public record HealthSnapshot(
        long tickIndex,
        double elapsedSeconds,
        List<ServerHealthSnapshot> servers
) {

    public HealthSnapshot {
        if (tickIndex < 0L) throw new IllegalArgumentException("tickIndex must be greater than or equal to zero.");
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0)
            throw new IllegalArgumentException("elapsedSeconds must be finite and greater than or equal to zero.");
        Objects.requireNonNull(servers, "servers must not be null.");
        servers = List.copyOf(servers);
    }

    public int serverCount() {
        return servers.size();
    }

    public long alertServerCount() {
        return servers.stream().filter(ServerHealthSnapshot::hasAlerts).count();
    }

    public long countByReason(ServerAlertReason reason) {
        Objects.requireNonNull(reason, "reason must not be null.");
        return servers.stream().filter(server -> server.hasAlertReason(reason)).count();
    }
}

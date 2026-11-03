package com.cpz.sim.datacenter.dataset;

import com.cpz.sim.datacenter.model.ServerLocation;
import java.time.*;
import java.util.List;

/** Completed electrical comparison only; the source row includes its timestamp and optional values.
 * Utilizations are fractions in [0,1], powers are electrical watts. No thermal history is exposed.
 */
public record DatasetReplayFrame(int pointNumber, Instant replayUtc, Duration engineElapsed,
                                 Duration replayOffset, List<ServerResult> servers) {
    public DatasetReplayFrame { servers = List.copyOf(servers); }

    /** Canonical identity includes the room; original source fields are never replaced by results. */
    public record ServerResult(String roomCode, ServerLocation location, DatasetRow original,
                               double resultUtilizationFraction, double resultPowerWatts) {}
}

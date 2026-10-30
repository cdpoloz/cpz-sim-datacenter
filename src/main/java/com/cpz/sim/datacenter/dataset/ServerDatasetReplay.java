package com.cpz.sim.datacenter.dataset;

import com.cpz.sim.datacenter.input.ServerPowerInputSource;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.time.SimulationTick;
import java.time.*;
import java.util.*;

/** Stateless, explicit bounded sample-and-hold for approved instantaneous server observations.
 * No interpolation, wrapping, implicit zero fill or input-mode change is performed.
 */
public final class ServerDatasetReplay {
    private final Map<ServerLocation, NavigableMap<Instant, DatasetRow>> timelines;
    private final Instant origin;
    private final Duration maximumHold;
    private final Instant coverageEnd;

    public ServerDatasetReplay(CustomerDataset dataset, DatasetBinding binding, Duration maximumHold) {
        Objects.requireNonNull(binding, "binding");
        this.maximumHold = Objects.requireNonNull(maximumHold, "maximumHold");
        this.coverageEnd = CustomerDatasetValidator.instant(dataset.metadata().get("coverageEndExclusiveUtc"));
        if (maximumHold.isZero() || maximumHold.isNegative()) throw new IllegalArgumentException("maximumHold must be positive");
        List<DatasetIssue> issues = new CustomerDatasetValidator().validate(dataset, binding);
        if (!issues.isEmpty()) throw new IllegalArgumentException("Resolve dataset diagnostics before replay: " + issues.getFirst());
        Map<ServerLocation, NavigableMap<Instant, DatasetRow>> series = new HashMap<>();
        Instant first = null;
        for (DatasetRow row : dataset.rows("ServerSamples")) {
            if (!"INSTANTANEOUS".equals(row.text("sampleKind")))
                throw new IllegalArgumentException("Pilot replay requires INSTANTANEOUS server samples, not interval means");
            ServerLocation location = new ServerLocation(row.text("column"), row.text("rackCode"), row.text("slot"));
            Instant time = Objects.requireNonNull(CustomerDatasetValidator.instant(row.values().get("timestampUtc")));
            series.computeIfAbsent(location, ignored -> new TreeMap<>()).put(time, row);
            if (first == null || time.isBefore(first)) first = time;
        }
        if (first == null) throw new IllegalArgumentException("ServerSamples must not be empty for server replay");
        for (Server server : binding.datacenter().getServers())
            if (!series.containsKey(server.getLocation())) throw new IllegalArgumentException("No dataset series for installed server: " + server.getCode());
        Map<ServerLocation, NavigableMap<Instant, DatasetRow>> copy = new HashMap<>();
        series.forEach((key, value) -> copy.put(key, Collections.unmodifiableNavigableMap(value)));
        this.timelines = Map.copyOf(copy);
        this.origin = first;
    }

    public Instant origin() { return origin; }

    /** Selects the latest sample at or before the requested absolute timestamp.
     * A held sample expires at sample time + maximumHold (exclusive).
     */
    public DatasetRow observationAt(ServerLocation location, Instant timestamp) {
        Objects.requireNonNull(location, "location"); Objects.requireNonNull(timestamp, "timestamp");
        if (coverageEnd != null && !timestamp.isBefore(coverageEnd))
            throw new IllegalStateException("Requested time is beyond declared dataset coverage: " + timestamp);
        var timeline = timelines.get(location);
        if (timeline == null) throw new IllegalArgumentException("No dataset series for " + location.code());
        var sample = timeline.floorEntry(timestamp);
        if (sample == null || Duration.between(sample.getKey(), timestamp).compareTo(maximumHold) >= 0)
            throw new IllegalStateException("No unexpired sample for " + location.code() + " at " + timestamp);
        return sample.getValue();
    }

    public WorkloadSource workloadSource() {
        requireMetric("utilizationFraction");
        return (server, tick) -> value(server, tick, "utilizationFraction");
    }

    public ServerPowerInputSource powerSource() {
        requireMetric("electricalPowerWatts");
        return (server, tick) -> value(server, tick, "electricalPowerWatts");
    }

    private double value(Server server, SimulationTick tick, String field) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(tick, "tick");
        return observationAt(server.getLocation(), origin.plus(tick.elapsedTime())).number(field);
    }
    private void requireMetric(String field) {
        if (timelines.values().stream().flatMap(t -> t.values().stream()).anyMatch(row -> row.number(field) == null))
            throw new IllegalArgumentException("Every server sample must supply " + field + " for this replay source");
    }
}

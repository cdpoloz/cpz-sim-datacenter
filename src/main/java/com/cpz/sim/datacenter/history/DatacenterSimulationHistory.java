package com.cpz.sim.datacenter.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Append-only historical register of completed datacenter simulation steps.
 *
 * <p>The history stores complete step snapshots ordered by tick index. It is
 * intentionally unbounded; callers that run long simulations can clear it on
 * reset or copy selected windows into a separate retention strategy.</p>
 *
 * @author CPZ
 */
public final class DatacenterSimulationHistory {

    private final NavigableMap<Long, DatacenterSimulationStepSnapshot> snapshotsByTickIndex = new TreeMap<>();

    /**
     * Appends a completed step snapshot to the history.
     *
     * @param snapshot step snapshot to append
     *
     * @throws NullPointerException if {@code snapshot} is {@code null}
     * @throws IllegalArgumentException if the snapshot tick is already present
     *                                  or is older than the current latest tick
     */
    public void record(DatacenterSimulationStepSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        long tickIndex = snapshot.tickIndex();
        if (snapshotsByTickIndex.containsKey(tickIndex))
            throw new IllegalArgumentException("A snapshot for tickIndex already exists: " + tickIndex);
        if (!snapshotsByTickIndex.isEmpty() && tickIndex <= snapshotsByTickIndex.lastKey())
            throw new IllegalArgumentException("snapshot tickIndex must be greater than the latest recorded tickIndex");
        snapshotsByTickIndex.put(tickIndex, snapshot);
    }

    /**
     * Removes all recorded snapshots.
     */
    public void clear() {
        snapshotsByTickIndex.clear();
    }

    /**
     * Returns whether the history has no recorded snapshots.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return snapshotsByTickIndex.isEmpty();
    }

    /**
     * Returns the number of recorded snapshots.
     *
     * @return snapshot count
     */
    public int size() {
        return snapshotsByTickIndex.size();
    }

    /**
     * Returns all recorded snapshots in tick order.
     *
     * @return immutable ordered snapshot list
     */
    public List<DatacenterSimulationStepSnapshot> snapshots() {
        return List.copyOf(snapshotsByTickIndex.values());
    }

    /**
     * Finds a recorded snapshot by tick index.
     *
     * @param tickIndex tick index to look up
     * @return snapshot, if present
     */
    public Optional<DatacenterSimulationStepSnapshot> find(long tickIndex) {
        return Optional.ofNullable(snapshotsByTickIndex.get(tickIndex));
    }

    /**
     * Returns the latest recorded snapshot.
     *
     * @return latest snapshot, if present
     */
    public Optional<DatacenterSimulationStepSnapshot> latest() {
        if (snapshotsByTickIndex.isEmpty()) return Optional.empty();
        return Optional.of(snapshotsByTickIndex.lastEntry().getValue());
    }

    /**
     * Returns the latest snapshots in chronological order.
     *
     * @param count maximum number of snapshots to return
     * @return immutable ordered snapshot list
     *
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public List<DatacenterSimulationStepSnapshot> latest(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        if (count == 0 || snapshotsByTickIndex.isEmpty()) return List.of();
        List<DatacenterSimulationStepSnapshot> snapshots = new ArrayList<>();
        for (DatacenterSimulationStepSnapshot snapshot : snapshotsByTickIndex.descendingMap().values()) {
            snapshots.add(snapshot);
            if (snapshots.size() == count) break;
        }
        Collections.reverse(snapshots);
        return List.copyOf(snapshots);
    }

    /**
     * Returns snapshots whose tick index is inside the inclusive range.
     *
     * @param firstTickIndex first tick index to include
     * @param lastTickIndex last tick index to include
     * @return immutable ordered snapshot list
     *
     * @throws IllegalArgumentException if the range is invalid
     */
    public List<DatacenterSimulationStepSnapshot> between(long firstTickIndex, long lastTickIndex) {
        if (firstTickIndex < 0L) throw new IllegalArgumentException("firstTickIndex must be >= 0");
        if (lastTickIndex < firstTickIndex)
            throw new IllegalArgumentException("lastTickIndex must be >= firstTickIndex");
        return List.copyOf(snapshotsByTickIndex.subMap(firstTickIndex, true, lastTickIndex, true).values());
    }
}

package com.cpz.sim.datacenter.history;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded in-memory temperature history, independently retained per hot
 * aisle.
 *
 * <p>This is a visualization-oriented rolling window, not long-term
 * persistence. Samples in every aisle series are ordered by increasing tick
 * index.</p>
 *
 * @author CPZ
 */
public final class HotAisleTemperatureHistory {

    public static final int DEFAULT_CAPACITY = 60;

    private final int capacity;
    private final Map<String, Deque<HotAisleTemperatureSample>> samplesByHotAisle = new HashMap<>();

    public HotAisleTemperatureHistory() {
        this(DEFAULT_CAPACITY);
    }

    public HotAisleTemperatureHistory(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    /**
     * Records one sample, retaining only the most recent configured window
     * for that sample's hot aisle.
     *
     * @param sample sample to append
     * @throws IllegalArgumentException when its tick is not newer than the
     *                                  latest sample for the same hot aisle
     */
    public void record(HotAisleTemperatureSample sample) {
        Objects.requireNonNull(sample, "sample must not be null");
        Deque<HotAisleTemperatureSample> samples =
                samplesByHotAisle.computeIfAbsent(sample.hotAisleCode(), ignored -> new ArrayDeque<>());
        HotAisleTemperatureSample latest = samples.peekLast();
        if (latest != null && sample.tickIndex() <= latest.tickIndex())
            throw new IllegalArgumentException("sample tickIndex must be greater than the latest recorded tickIndex for hot aisle: " + sample.hotAisleCode());
        samples.addLast(sample);
        if (samples.size() > capacity) samples.removeFirst();
    }

    /**
     * Returns the retained samples for one hot aisle in ascending tick order.
     * Unknown hot aisles and hot aisles with no valid samples return an empty
     * immutable list.
     *
     * @param hotAisleCode hot-aisle identifier
     * @return immutable sample series
     */
    public List<HotAisleTemperatureSample> samples(String hotAisleCode) {
        Objects.requireNonNull(hotAisleCode, "hotAisleCode must not be null");
        Deque<HotAisleTemperatureSample> samples = samplesByHotAisle.get(hotAisleCode);
        return samples == null ? List.of() : List.copyOf(samples);
    }

    /**
     * Removes every retained series.
     */
    public void clear() {
        samplesByHotAisle.clear();
    }

    public int capacity() {
        return capacity;
    }
}

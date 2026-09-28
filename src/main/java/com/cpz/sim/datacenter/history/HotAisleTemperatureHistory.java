package com.cpz.sim.datacenter.history;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory temperature history, independently retained per hot aisle.
 *
 * <p>Samples are retained for the lifetime of this history instance. This is
 * not long-term persistence. Samples in every aisle series are ordered by
 * increasing tick index.</p>
 *
 * @author CPZ
 */
public final class HotAisleTemperatureHistory {

    private final Map<String, Deque<HotAisleTemperatureSample>> samplesByHotAisle = new HashMap<>();

    /**
     * Records one sample for its hot aisle.
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
}

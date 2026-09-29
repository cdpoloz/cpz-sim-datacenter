package com.cpz.sim.datacenter.history;

import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Records average online-server temperatures for hot aisles represented by an
 * operational snapshot.
 *
 * <p>Rack averages are weighted by their online-server count. Racks without
 * online servers do not contribute, so an aisle without valid temperature
 * data does not receive a sample for that tick.</p>
 *
 * @author CPZ
 */
public final class HotAisleTemperatureHistoryRecorder {

    private final Function<RackLocation, String> hotAisleCodeResolver;
    private final HotAisleTemperatureHistory history;

    public HotAisleTemperatureHistoryRecorder(
            Function<RackLocation, String> hotAisleCodeResolver
    ) {
        this(hotAisleCodeResolver, new HotAisleTemperatureHistory());
    }

    public HotAisleTemperatureHistoryRecorder(
            Function<RackLocation, String> hotAisleCodeResolver,
            HotAisleTemperatureHistory history
    ) {
        this.hotAisleCodeResolver = Objects.requireNonNull(hotAisleCodeResolver, "hotAisleCodeResolver must not be null");
        this.history = Objects.requireNonNull(history, "history must not be null");
    }

    /**
     * Records at most one valid average-temperature sample for every resolved
     * hot aisle in the operational snapshot.
     *
     * @param operationalSnapshot completed operational snapshot
     */
    public void record(DatacenterOperationalSnapshot operationalSnapshot) {
        Objects.requireNonNull(operationalSnapshot, "operationalSnapshot must not be null");
        Map<String, TemperatureTotal> totalsByHotAisle = new LinkedHashMap<>();
        for (RackOperationalSnapshot rack : operationalSnapshot.racks().values()) {
            if (!rack.hasOnlineServers() || !Double.isFinite(rack.averageOnlineTemperatureCelsius())) continue;
            String hotAisleCode = resolveHotAisleCode(rack.location());
            totalsByHotAisle
                    .computeIfAbsent(hotAisleCode, ignored -> new TemperatureTotal())
                    .add(rack.averageOnlineTemperatureCelsius(), rack.onlineServerCount());
        }
        for (Map.Entry<String, TemperatureTotal> entry : totalsByHotAisle.entrySet()) {
            history.record(new HotAisleTemperatureSample(
                    entry.getKey(),
                    operationalSnapshot.tickIndex(),
                    entry.getValue().averageTemperatureCelsius()
            ));
        }
    }

    public HotAisleTemperatureHistory history() {
        return history;
    }

    public void clear() {
        history.clear();
    }

    private String resolveHotAisleCode(RackLocation rackLocation) {
        String hotAisleCode = hotAisleCodeResolver.apply(rackLocation);
        Objects.requireNonNull(hotAisleCode, "resolved hotAisleCode must not be null");
        if (hotAisleCode.isBlank()) throw new IllegalArgumentException("resolved hotAisleCode must not be blank");
        return hotAisleCode;
    }

    private static final class TemperatureTotal {

        private double weightedTemperatureSum;
        private int onlineServerCount;

        private void add(double averageTemperatureCelsius, int servers) {
            weightedTemperatureSum += averageTemperatureCelsius * servers;
            onlineServerCount += servers;
        }

        private double averageTemperatureCelsius() {
            return weightedTemperatureSum / onlineServerCount;
        }
    }
}

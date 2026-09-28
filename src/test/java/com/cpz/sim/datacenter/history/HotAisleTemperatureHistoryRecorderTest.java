package com.cpz.sim.datacenter.history;

import com.cpz.sim.datacenter.config.definition.HotAisleDefinition;
import com.cpz.sim.datacenter.input.ConfiguredHotAisleCodeResolver;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotAisleTemperatureHistoryRecorderTest {

    @Test
    void shouldRecordIndependentWeightedSeriesForConfiguredHotAisles() {
        HotAisleTemperatureHistory history = new HotAisleTemperatureHistory();
        HotAisleTemperatureHistoryRecorder recorder = new HotAisleTemperatureHistoryRecorder(
                new ConfiguredHotAisleCodeResolver(List.of(
                        new HotAisleDefinition("HA01", List.of("C01")),
                        new HotAisleDefinition("HA02", List.of("C02", "C03"))
                )),
                history
        );

        recorder.record(snapshot(1L,
                rack("C01", 1, 40.0),
                rack("C02", 1, 50.0),
                rack("C03", 3, 70.0)
        ));
        recorder.record(snapshot(2L,
                rack("C01", 1, 45.0),
                rack("C02", 2, 60.0),
                rack("C03", 2, 80.0)
        ));

        assertEquals(List.of(
                new HotAisleTemperatureSample("HA01", 1L, 40.0),
                new HotAisleTemperatureSample("HA01", 2L, 45.0)
        ), history.samples("HA01"));
        assertEquals(List.of(
                new HotAisleTemperatureSample("HA02", 1L, 65.0),
                new HotAisleTemperatureSample("HA02", 2L, 70.0)
        ), history.samples("HA02"));
    }

    @Test
    void shouldKeepOnlyConfiguredCapacityInTickOrder() {
        HotAisleTemperatureHistory history = new HotAisleTemperatureHistory(2);
        HotAisleTemperatureHistoryRecorder recorder =
                new HotAisleTemperatureHistoryRecorder(location -> "HA01", history);

        recorder.record(snapshot(1L, rack("C01", 1, 40.0)));
        recorder.record(snapshot(2L, rack("C01", 1, 50.0)));
        recorder.record(snapshot(3L, rack("C01", 1, 60.0)));

        assertEquals(2, history.capacity());
        assertEquals(List.of(
                new HotAisleTemperatureSample("HA01", 2L, 50.0),
                new HotAisleTemperatureSample("HA01", 3L, 60.0)
        ), history.samples("HA01"));
    }

    @Test
    void shouldReturnImmutableEmptySeriesAndSkipAislesWithoutOnlineTemperature() {
        HotAisleTemperatureHistory history = new HotAisleTemperatureHistory();
        HotAisleTemperatureHistoryRecorder recorder =
                new HotAisleTemperatureHistoryRecorder(location -> "HA01", history);

        recorder.record(snapshot(1L, rackWithoutOnlineServers("C01")));

        assertTrue(history.samples("UNKNOWN").isEmpty());
        assertTrue(history.samples("HA01").isEmpty());
        assertThrows(
                UnsupportedOperationException.class,
                () -> history.samples("UNKNOWN").add(new HotAisleTemperatureSample("UNKNOWN", 1L, 42.0))
        );

        recorder.record(snapshot(2L, rack("C01", 1, 42.0)));
        assertThrows(
                UnsupportedOperationException.class,
                () -> history.samples("HA01").clear()
        );
    }

    private static RackOperationalSnapshot rack(String column, int onlineServerCount, double temperatureCelsius) {
        RackLocation location = new RackLocation(column, new RackCode("R01"));
        return new RackOperationalSnapshot(
                location,
                onlineServerCount,
                onlineServerCount,
                100.0 * onlineServerCount,
                500.0 * onlineServerCount,
                300.0 * onlineServerCount,
                temperatureCelsius,
                temperatureCelsius,
                0.50
        );
    }

    private static RackOperationalSnapshot rackWithoutOnlineServers(String column) {
        RackLocation location = new RackLocation(column, new RackCode("R01"));
        return new RackOperationalSnapshot(location, 1, 0, 100.0, 500.0, 0.0, Double.NaN, 24.0, Double.NaN);
    }

    private static DatacenterOperationalSnapshot snapshot(long tickIndex, RackOperationalSnapshot... racks) {
        Map<RackLocation, RackOperationalSnapshot> orderedRacks = new LinkedHashMap<>();
        for (RackOperationalSnapshot rack : racks) orderedRacks.put(rack.location(), rack);
        int onlineServerCount = orderedRacks.values().stream().mapToInt(RackOperationalSnapshot::onlineServerCount).sum();
        RackOperationalSnapshot hottestRack = orderedRacks.values().stream()
                .filter(RackOperationalSnapshot::hasOnlineServers)
                .findFirst()
                .orElse(null);
        return new DatacenterOperationalSnapshot(
                tickIndex,
                tickIndex * 60.0,
                orderedRacks,
                Map.of(),
                24.0,
                hottestRack == null ? Optional.empty() : Optional.of(hottestRack.location()),
                hottestRack == null ? Double.NaN : hottestRack.averageOnlineTemperatureCelsius(),
                onlineServerCount == 0 ? Double.NaN : 0.50,
                orderedRacks.values().stream().mapToDouble(RackOperationalSnapshot::idlePowerWatts).sum(),
                orderedRacks.values().stream().mapToDouble(RackOperationalSnapshot::maxPowerWatts).sum(),
                orderedRacks.values().stream().mapToDouble(RackOperationalSnapshot::currentPowerWatts).sum(),
                Double.NaN,
                Double.NaN,
                Double.NaN
        );
    }
}

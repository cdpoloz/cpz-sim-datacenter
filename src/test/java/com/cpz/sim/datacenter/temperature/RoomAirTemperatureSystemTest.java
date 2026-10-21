package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.snapshot.RoomAirTemperatureSnapshot;
import com.cpz.sim.datacenter.system.RoomAirTemperatureSystem;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RoomAirTemperatureSystemTest {

    private static RoomAirTemperatureSystem system() {
        return new RoomAirTemperatureSystem(new RoomAirTemperatureOptions(24.0, 2_000_000.0, 20.0, 0.0));
    }

    private static SimulationTick tick(long index) {
        return new SimulationTick(index, Duration.ofMinutes(index), Duration.ofMinutes(1));
    }

    @Test
    void advancesCapturesImmutableSnapshotsAndResets() {
        var system = system();
        assertEquals(24.0, system.temperatureCelsius());
        assertTrue(system.lastSnapshot().isEmpty());
        var input = new RoomAirThermalInput(200_000.0, 0.0);
        var first = system.tick(tick(1), input);
        assertEquals(new RoomAirTemperatureSnapshot(1, 60.0, 30.0), first);
        var second = system.tick(tick(2), input);
        assertEquals(36.0, second.temperatureCelsius());
        assertEquals(30.0, first.temperatureCelsius());
        assertEquals(second, system.lastSnapshot().orElseThrow());
        system.reset();
        assertEquals(24.0, system.temperatureCelsius());
        assertTrue(system.lastSnapshot().isEmpty());
        assertEquals(first, system.tick(tick(1), input));
    }

    @Test
    void rejectedInputsLeaveStateAndLastSnapshotUnchanged() {
        var system = system();
        var first = system.tick(tick(1), new RoomAirThermalInput(200_000.0, 0.0));
        assertThrows(NullPointerException.class, () -> system.tick(null, new RoomAirThermalInput(0.0, 0.0)));
        assertThrows(NullPointerException.class, () -> system.tick(tick(2), null));
        assertEquals(first.temperatureCelsius(), system.temperatureCelsius());
        assertEquals(first, system.lastSnapshot().orElseThrow());
        var overflow = new RoomAirTemperatureSystem(new RoomAirTemperatureOptions(24.0, 1.0, 20.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> overflow.tick(tick(1), new RoomAirThermalInput(Double.MAX_VALUE, 0.0)));
        assertEquals(24.0, overflow.temperatureCelsius());
        assertTrue(overflow.lastSnapshot().isEmpty());
        assertThrows(NullPointerException.class, () -> new RoomAirTemperatureSystem(null));
    }

    @Test
    void snapshotsRejectInvalidCaptureData() {
        assertThrows(IllegalArgumentException.class, () -> new RoomAirTemperatureSnapshot(-1, 0.0, 24.0));
        for (double value : new double[]{-1.0, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new RoomAirTemperatureSnapshot(0, value, 24.0));
        assertThrows(IllegalArgumentException.class, () -> new RoomAirTemperatureSnapshot(0, 0.0, Double.NaN));
    }
}

package com.cpz.sim.datacenter.temperature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RoomAirThermalStateTest {

    @Test
    void initializesIndependentStatesAndPreservesStateAfterRejectedUpdate() {
        var options = new RoomAirTemperatureOptions(24.0, 2_000_000.0, 20.0, 0.0);
        var first = new RoomAirThermalState(options);
        var second = new RoomAirThermalState(options);
        assertEquals(24.0, first.getTemperatureCelsius());
        first.setTemperatureCelsius(28.0);
        assertEquals(24.0, second.getTemperatureCelsius());
        assertThrows(IllegalArgumentException.class, () -> first.setTemperatureCelsius(Double.NaN));
        assertEquals(28.0, first.getTemperatureCelsius());
        assertThrows(NullPointerException.class, () -> new RoomAirThermalState(null));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteTemperaturesAndStateUpdates(double value) {
        assertThrows(IllegalArgumentException.class,
                () -> new RoomAirTemperatureOptions(value, 1.0, 20.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new RoomAirTemperatureOptions(24.0, 1.0, value, 0.0));
        var state = new RoomAirThermalState(new RoomAirTemperatureOptions(24.0, 1.0, 20.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> state.setTemperatureCelsius(value));
        assertEquals(24.0, state.getTemperatureCelsius());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidEffectiveCapacity(double value) {
        assertThrows(IllegalArgumentException.class,
                () -> new RoomAirTemperatureOptions(24.0, value, 20.0, 0.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidEnvironmentHeatTransfer(double value) {
        assertThrows(IllegalArgumentException.class,
                () -> new RoomAirTemperatureOptions(24.0, 1.0, 20.0, value));
    }
}

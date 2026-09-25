package com.cpz.sim.datacenter.input;

import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapAisleTemperatureInputSourceTest {

    @Test
    void shouldReturnExplicitAisleTemperature() {
        MapAisleTemperatureInputSource source =
                new MapAisleTemperatureInputSource(Map.of("HA01", 52.0), 25.0);

        assertEquals(52.0, source.temperatureCelsius("HA01", tick()), 0.0001);
    }

    @Test
    void shouldReturnDefaultTemperatureWhenAisleHasNoExplicitValue() {
        MapAisleTemperatureInputSource source =
                new MapAisleTemperatureInputSource(Map.of("HA01", 52.0), 25.0);

        assertEquals(25.0, source.temperatureCelsius("HA02", tick()), 0.0001);
    }

    @Test
    void shouldRejectInvalidTemperaturesAndAisleCodes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapAisleTemperatureInputSource(Map.of(), Double.NaN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapAisleTemperatureInputSource(Map.of("HA01", Double.POSITIVE_INFINITY), 25.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapAisleTemperatureInputSource(Map.of("", 52.0), 25.0)
        );
    }

    @Test
    void shouldRejectInvalidRuntimeArguments() {
        MapAisleTemperatureInputSource source =
                new MapAisleTemperatureInputSource(Map.of("HA01", 52.0), 25.0);

        assertThrows(NullPointerException.class, () -> source.temperatureCelsius(null, tick()));
        assertThrows(IllegalArgumentException.class, () -> source.temperatureCelsius("", tick()));
        assertThrows(NullPointerException.class, () -> source.temperatureCelsius("HA01", null));
    }

    private static SimulationTick tick() {
        return new SimulationTick(1L, Duration.ofMinutes(1), Duration.ofMinutes(1));
    }
}

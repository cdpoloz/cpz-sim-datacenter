package com.cpz.sim.datacenter.input;

import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedAisleTemperatureInputSourceTest {

    @Test
    void shouldReturnFiniteValues() {
        SimulatedAisleTemperatureInputSource source = new SimulatedAisleTemperatureInputSource();

        double temperatureCelsius = source.temperatureCelsius("HA01", tick(1));

        assertTrue(Double.isFinite(temperatureCelsius));
    }

    @Test
    void shouldRespectConfiguredMinAndMaxTemperatures() {
        SimulatedAisleTemperatureInputSource source =
                new SimulatedAisleTemperatureInputSource(30.0, 40.0, 20.0, 0.5);

        for (int i = 0; i < 200; i++) {
            double temperatureCelsius = source.temperatureCelsius("HA01", tick(i));
            assertTrue(temperatureCelsius >= 30.0);
            assertTrue(temperatureCelsius <= 40.0);
        }
    }

    @Test
    void shouldAllowDifferentAislesToReturnDifferentTemperatures() {
        SimulatedAisleTemperatureInputSource source =
                new SimulatedAisleTemperatureInputSource(30.0, 65.0, 3.0, 0.05);

        assertNotEquals(
                source.temperatureCelsius("HA01", tick(25)),
                source.temperatureCelsius("HA02", tick(25))
        );
    }

    @Test
    void shouldReturnSameTemperatureForSameAisleAtSameTick() {
        SimulatedAisleTemperatureInputSource source =
                new SimulatedAisleTemperatureInputSource(30.0, 65.0, 3.0, 0.05);

        assertEquals(
                source.temperatureCelsius("HA02", tick(25)),
                source.temperatureCelsius("HA02", tick(25)),
                0.0001
        );
    }

    @Test
    void shouldAllowSameAisleToVaryAcrossTicks() {
        SimulatedAisleTemperatureInputSource source =
                new SimulatedAisleTemperatureInputSource(30.0, 65.0, 3.0, 0.05);

        assertNotEquals(
                source.temperatureCelsius("HA01", tick(1)),
                source.temperatureCelsius("HA01", tick(40))
        );
    }

    @Test
    void shouldRejectInvalidParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedAisleTemperatureInputSource(Double.NaN, 65.0, 3.0, 0.05)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedAisleTemperatureInputSource(65.0, 30.0, 3.0, 0.05)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedAisleTemperatureInputSource(30.0, 65.0, -0.01, 0.05)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedAisleTemperatureInputSource(30.0, 65.0, 3.0, Double.POSITIVE_INFINITY)
        );
    }

    @Test
    void shouldAcceptDefaultConstructor() {
        assertDoesNotThrow(() -> new SimulatedAisleTemperatureInputSource());
    }

    private static SimulationTick tick(long index) {
        return new SimulationTick(index, Duration.ofSeconds(index * 60L), Duration.ofSeconds(60));
    }
}

package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedRackTemperatureInputSourceTest {

    private static final RackLocation FIRST_RACK_LOCATION = new RackLocation("A01", "RACK-A01-R01");
    private static final RackLocation SECOND_RACK_LOCATION = new RackLocation("A01", "RACK-A01-R02");

    @Test
    void shouldReturnFiniteValues() {
        SimulatedRackTemperatureInputSource source = new SimulatedRackTemperatureInputSource();

        double temperatureCelsius = source.temperatureCelsius(FIRST_RACK_LOCATION, tick(1));

        assertTrue(Double.isFinite(temperatureCelsius));
    }

    @Test
    void shouldRespectConfiguredMinAndMaxTemperatures() {
        SimulatedRackTemperatureInputSource source =
                new SimulatedRackTemperatureInputSource(30.0, 40.0, 20.0, 0.5);

        for (int i = 0; i < 200; i++) {
            double temperatureCelsius = source.temperatureCelsius(FIRST_RACK_LOCATION, tick(i));
            assertTrue(temperatureCelsius >= 30.0);
            assertTrue(temperatureCelsius <= 40.0);
        }
    }

    @Test
    void shouldAllowDifferentRacksToReturnDifferentTemperatures() {
        SimulatedRackTemperatureInputSource source =
                new SimulatedRackTemperatureInputSource(30.0, 65.0, 3.0, 0.05);

        assertNotEquals(
                source.temperatureCelsius(FIRST_RACK_LOCATION, tick(25)),
                source.temperatureCelsius(SECOND_RACK_LOCATION, tick(25))
        );
    }

    @Test
    void shouldAllowSameRackToVaryAcrossTicks() {
        SimulatedRackTemperatureInputSource source =
                new SimulatedRackTemperatureInputSource(30.0, 65.0, 3.0, 0.05);

        assertNotEquals(
                source.temperatureCelsius(FIRST_RACK_LOCATION, tick(1)),
                source.temperatureCelsius(FIRST_RACK_LOCATION, tick(40))
        );
    }

    @Test
    void shouldVarySmoothlyAcrossAdjacentTicks() {
        double amplitudeCelsius = 3.0;
        SimulatedRackTemperatureInputSource source =
                new SimulatedRackTemperatureInputSource(30.0, 65.0, amplitudeCelsius, 0.05);

        for (int i = 0; i < 100; i++) {
            double current = source.temperatureCelsius(FIRST_RACK_LOCATION, tick(i));
            double next = source.temperatureCelsius(FIRST_RACK_LOCATION, tick(i + 1));
            assertTrue(Math.abs(next - current) <= amplitudeCelsius);
        }
    }

    @Test
    void shouldRejectInvalidParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedRackTemperatureInputSource(Double.NaN, 65.0, 3.0, 0.05)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedRackTemperatureInputSource(65.0, 30.0, 3.0, 0.05)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedRackTemperatureInputSource(30.0, 65.0, -0.01, 0.05)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedRackTemperatureInputSource(30.0, 65.0, 3.0, Double.POSITIVE_INFINITY)
        );
    }

    @Test
    void shouldAcceptDefaultConstructor() {
        assertDoesNotThrow(() -> new SimulatedRackTemperatureInputSource());
    }

    private static SimulationTick tick(long index) {
        return new SimulationTick(index, Duration.ofSeconds(index * 60L), Duration.ofSeconds(60));
    }
}

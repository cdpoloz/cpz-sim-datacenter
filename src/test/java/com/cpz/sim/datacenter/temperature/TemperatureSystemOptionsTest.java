package com.cpz.sim.datacenter.temperature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
/**
 * @author CPZ
 */
class TemperatureSystemOptionsTest {

    @Test
    void createsValidOptions() {
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                25.0,
                5000.0,
                8.0
        );
        assertEquals(25.0, options.ambientTemperatureCelsius());
        assertEquals(25.0, options.defaultInitialTemperatureCelsius());
        assertEquals(5000.0, options.thermalCapacityJoulesPerCelsius());
        assertEquals(8.0, options.heatDissipationWattsPerCelsius());
    }

    @Test
    void defaultsCreatesValidOptions() {
        TemperatureSystemOptions options = TemperatureSystemOptions.defaults();
        assertTrue(Double.isFinite(options.ambientTemperatureCelsius()));
        assertTrue(Double.isFinite(options.defaultInitialTemperatureCelsius()));
        assertTrue(options.thermalCapacityJoulesPerCelsius() > 0.0);
        assertTrue(options.heatDissipationWattsPerCelsius() >= 0.0);
    }

    @Test
    void rejectsNonFiniteAmbientTemperature() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TemperatureSystemOptions(
                        Double.NaN,
                        25.0,
                        5000.0,
                        8.0
                )
        );
    }

    @Test
    void rejectsNonFiniteDefaultInitialTemperature() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TemperatureSystemOptions(
                        25.0,
                        Double.POSITIVE_INFINITY,
                        5000.0,
                        8.0
                )
        );
    }

    @Test
    void rejectsZeroThermalCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TemperatureSystemOptions(
                        25.0,
                        25.0,
                        0.0,
                        8.0
                )
        );
    }

    @Test
    void rejectsNegativeThermalCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TemperatureSystemOptions(
                        25.0,
                        25.0,
                        -1.0,
                        8.0
                )
        );
    }

    @Test
    void rejectsNonFiniteThermalCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TemperatureSystemOptions(
                        25.0,
                        25.0,
                        Double.NaN,
                        8.0
                )
        );
    }

    @Test
    void acceptsZeroHeatDissipation() {
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                25.0,
                5000.0,
                0.0
        );

        assertEquals(0.0, options.heatDissipationWattsPerCelsius());
    }

    @Test
    void rejectsNegativeHeatDissipation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TemperatureSystemOptions(
                        25.0,
                        25.0,
                        5000.0,
                        -1.0
                )
        );
    }

    @Test
    void rejectsNonFiniteHeatDissipation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TemperatureSystemOptions(
                        25.0,
                        25.0,
                        5000.0,
                        Double.POSITIVE_INFINITY
                )
        );
    }
}

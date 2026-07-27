package com.cpz.sim.datacenter.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class ServerThermalPropertiesTest {

    @Test
    void shouldAcceptFinitePositiveValues() {
        ServerThermalProperties properties = new ServerThermalProperties(7500.0, 12.5);

        assertEquals(7500.0, properties.thermalCapacityJoulesPerCelsius());
        assertEquals(12.5, properties.heatDissipationWattsPerCelsius());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void shouldRejectInvalidThermalCapacity(double value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerThermalProperties(value, 8.0)
        );
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void shouldRejectInvalidHeatDissipation(double value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerThermalProperties(5000.0, value)
        );
    }
}

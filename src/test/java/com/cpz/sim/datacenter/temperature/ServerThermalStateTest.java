package com.cpz.sim.datacenter.temperature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
/**
 * @author CPZ
 */
class ServerThermalStateTest {

    @Test
    void createsThermalStateWithServerCodeAndInitialTemperature() {
        ServerThermalState state = new ServerThermalState("server-01", 25.0);
        assertEquals("server-01", state.getServerCode());
        assertEquals(25.0, state.getTemperatureCelsius());
    }

    @Test
    void updatesTemperature() {
        ServerThermalState state = new ServerThermalState("server-01", 25.0);
        state.setTemperatureCelsius(31.5);
        assertEquals(31.5, state.getTemperatureCelsius());
    }

    @Test
    void rejectsNullServerCode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServerThermalState(null, 25.0)
        );
        assertTrue(exception.getMessage().contains("serverCode"));
    }

    @Test
    void rejectsBlankServerCode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServerThermalState("   ", 25.0)
        );
        assertTrue(exception.getMessage().contains("serverCode"));
    }

    @Test
    void rejectsNonFiniteInitialTemperature() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerThermalState("server-01", Double.NaN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerThermalState("server-01", Double.POSITIVE_INFINITY)
        );
    }

    @Test
    void rejectsNonFiniteUpdatedTemperature() {
        ServerThermalState state = new ServerThermalState("server-01", 25.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> state.setTemperatureCelsius(Double.NaN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> state.setTemperatureCelsius(Double.NEGATIVE_INFINITY)
        );
    }

}

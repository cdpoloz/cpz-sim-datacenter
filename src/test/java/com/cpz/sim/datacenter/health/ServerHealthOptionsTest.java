package com.cpz.sim.datacenter.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class ServerHealthOptionsTest {

    @Test
    void shouldCreateOptionsWithCustomThresholds() {
        HealthThreshold utilizationThreshold = new HealthThreshold(0.95, 0.90);
        HealthThreshold temperatureThreshold = new HealthThreshold(85.0, 80.0);
        ServerHealthOptions options = new ServerHealthOptions(utilizationThreshold, temperatureThreshold);
        assertEquals(utilizationThreshold, options.utilizationThreshold());
        assertEquals(temperatureThreshold, options.temperatureThreshold());
    }

    @Test
    void shouldProvideDefaultThresholds() {
        ServerHealthOptions options = ServerHealthOptions.defaults();
        assertEquals(new HealthThreshold(0.90, 0.85), options.utilizationThreshold());
        assertEquals(new HealthThreshold(80.0, 75.0), options.temperatureThreshold());
    }

    @Test
    void shouldAcceptUtilizationThresholdAtRangeBoundaries() {
        HealthThreshold utilizationThreshold = new HealthThreshold(1.0, 0.0);
        ServerHealthOptions options = new ServerHealthOptions(utilizationThreshold, new HealthThreshold(80.0, 75.0));
        assertEquals(utilizationThreshold, options.utilizationThreshold());
    }

    @Test
    void shouldRejectNullUtilizationThreshold() {
        assertThrows(NullPointerException.class, () -> new ServerHealthOptions(null, new HealthThreshold(80.0, 75.0)));
    }

    @Test
    void shouldRejectNullTemperatureThreshold() {
        assertThrows(NullPointerException.class, () -> new ServerHealthOptions( new HealthThreshold(0.90, 0.85), null));
    }

    @Test
    void shouldRejectUtilizationAlertThresholdBelowZero() {
        HealthThreshold invalidThreshold = new HealthThreshold(-0.10, -0.20);
        assertThrows(IllegalArgumentException.class, () -> new ServerHealthOptions(invalidThreshold, new HealthThreshold(80.0, 75.0)));
    }

    @Test
    void shouldRejectUtilizationAlertThresholdAboveOne() {
        HealthThreshold invalidThreshold = new HealthThreshold(1.10, 0.90);
        assertThrows(IllegalArgumentException.class, () -> new ServerHealthOptions(invalidThreshold, new HealthThreshold(80.0, 75.0)));
    }

    @Test
    void shouldRejectUtilizationClearThresholdBelowZero() {
        HealthThreshold invalidThreshold = new HealthThreshold(0.90, -0.10);
        assertThrows(IllegalArgumentException.class, () -> new ServerHealthOptions(invalidThreshold, new HealthThreshold(80.0, 75.0)));
    }

    @Test
    void shouldRejectUtilizationClearThresholdAboveOne() {
        HealthThreshold invalidThreshold = new HealthThreshold(1.20, 1.10);
        assertThrows(IllegalArgumentException.class, () -> new ServerHealthOptions(invalidThreshold, new HealthThreshold(80.0, 75.0)));
    }
}

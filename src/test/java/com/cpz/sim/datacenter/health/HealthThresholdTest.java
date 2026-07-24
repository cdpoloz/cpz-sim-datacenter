package com.cpz.sim.datacenter.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class HealthThresholdTest {

    private static final double ALERT_THRESHOLD = 0.90;
    private static final double CLEAR_THRESHOLD = 0.85;

    private final HealthThreshold threshold = new HealthThreshold(ALERT_THRESHOLD, CLEAR_THRESHOLD);

    @Test
    void shouldActivateWhenValueReachesAlertThreshold() {
        boolean active = threshold.evaluate(ALERT_THRESHOLD, false);
        assertTrue(active);
    }

    @Test
    void shouldActivateWhenValueExceedsAlertThreshold() {
        boolean active = threshold.evaluate(0.95, false);
        assertTrue(active);
    }

    @Test
    void shouldRemainInactiveBelowAlertThreshold() {
        boolean active = threshold.evaluate(0.89, false);
        assertFalse(active);
    }

    @Test
    void shouldRemainActiveInsideHysteresisBand() {
        boolean active = threshold.evaluate(0.87, true);
        assertTrue(active);
    }

    @Test
    void shouldClearWhenValueReachesClearThreshold() {
        boolean active = threshold.evaluate(CLEAR_THRESHOLD, true);
        assertFalse(active);
    }

    @Test
    void shouldClearWhenValueFallsBelowClearThreshold() {
        boolean active = threshold.evaluate(0.80, true);
        assertFalse(active);
    }

    @Test
    void shouldRejectEqualThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new HealthThreshold(0.90, 0.90));
    }

    @Test
    void shouldRejectClearThresholdAboveAlertThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new HealthThreshold(0.90, 0.91));
    }

    @Test
    void shouldRejectNonFiniteAlertThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new HealthThreshold(Double.NaN, CLEAR_THRESHOLD));
        assertThrows(IllegalArgumentException.class, () -> new HealthThreshold(Double.POSITIVE_INFINITY, CLEAR_THRESHOLD));
    }

    @Test
    void shouldRejectNonFiniteClearThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new HealthThreshold(ALERT_THRESHOLD, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new HealthThreshold(ALERT_THRESHOLD, Double.NEGATIVE_INFINITY));
    }

    @Test
    void shouldRejectNonFiniteEvaluationValue() {
        assertThrows(IllegalArgumentException.class, () -> threshold.evaluate(Double.NaN, false));
        assertThrows(IllegalArgumentException.class, () -> threshold.evaluate(Double.POSITIVE_INFINITY, true));
    }

}

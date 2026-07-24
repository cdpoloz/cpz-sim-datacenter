package com.cpz.sim.datacenter.health;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class ServerHealthStateTest {

    private static final HealthThreshold UTILIZATION_THRESHOLD = new HealthThreshold(0.90, 0.85);
    private static final HealthThreshold TEMPERATURE_THRESHOLD = new HealthThreshold(80.0, 75.0);
    private final ServerHealthState healthState = new ServerHealthState("SRV-01");

    @Test
    void shouldStartWithoutAlertReasons() {
        assertAll(
                () -> assertEquals("SRV-01", healthState.getServerCode()),
                () -> assertFalse(healthState.hasAlertReasons()),
                () -> assertTrue(healthState.getAlertReasons().isEmpty())
        );
    }

    @Test
    void shouldActivateAlertReason() {
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, 0.90, UTILIZATION_THRESHOLD);
        assertAll(
                () -> assertTrue(healthState.hasAlertReasons()),
                () -> assertTrue(healthState.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION)),
                () -> assertEquals(Set.of(ServerAlertReason.HIGH_UTILIZATION), healthState.getAlertReasons())
        );
    }

    @Test
    void shouldPreserveActiveReasonInsideHysteresisBand() {
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, 0.95, UTILIZATION_THRESHOLD);
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, 0.87, UTILIZATION_THRESHOLD);
        assertTrue(healthState.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION));
    }

    @Test
    void shouldClearReasonAtClearThreshold() {
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, 0.95, UTILIZATION_THRESHOLD);
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, 0.85, UTILIZATION_THRESHOLD);
        assertAll(
                () -> assertFalse(healthState.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION)),
                () -> assertFalse(healthState.hasAlertReasons())
        );
    }

    @Test
    void shouldTrackMultipleAlertReasonsIndependently() {
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, 0.95, UTILIZATION_THRESHOLD);
        healthState.updateAlertReason(ServerAlertReason.HIGH_TEMPERATURE, 85.0, TEMPERATURE_THRESHOLD);
        assertAll(
                () -> assertTrue(healthState.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION)),
                () -> assertTrue(healthState.hasAlertReason(ServerAlertReason.HIGH_TEMPERATURE)),
                () -> assertEquals(Set.of(ServerAlertReason.HIGH_UTILIZATION, ServerAlertReason.HIGH_TEMPERATURE), healthState.getAlertReasons())
        );
    }

    @Test
    void shouldRemoveOneReasonWithoutRemovingAnother() {
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, 0.95, UTILIZATION_THRESHOLD);
        healthState.updateAlertReason(ServerAlertReason.HIGH_TEMPERATURE, 85.0, TEMPERATURE_THRESHOLD);
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, 0.80, UTILIZATION_THRESHOLD);
        assertAll(
                () -> assertFalse(healthState.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION)),
                () -> assertTrue(healthState.hasAlertReason(ServerAlertReason.HIGH_TEMPERATURE)),
                () -> assertTrue(healthState.hasAlertReasons())
        );
    }

    @Test
    void shouldClearAllAlertReasons() {
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, 0.95, UTILIZATION_THRESHOLD);
        healthState.updateAlertReason(ServerAlertReason.HIGH_TEMPERATURE, 85.0, TEMPERATURE_THRESHOLD);
        healthState.clearAlertReasons();
        assertAll(
                () -> assertFalse(healthState.hasAlertReasons()),
                () -> assertTrue(healthState.getAlertReasons().isEmpty())
        );
    }

    @Test
    void shouldReturnImmutableIndependentAlertReasonSet() {
        healthState.updateAlertReason(ServerAlertReason.HIGH_UTILIZATION, 0.95, UTILIZATION_THRESHOLD);
        Set<ServerAlertReason> capturedReasons = healthState.getAlertReasons();
        healthState.clearAlertReasons();
        assertAll(
                () -> assertEquals(Set.of(ServerAlertReason.HIGH_UTILIZATION), capturedReasons),
                () -> assertThrows(UnsupportedOperationException.class, () -> capturedReasons.add(ServerAlertReason.HIGH_TEMPERATURE)),
                () -> assertTrue(healthState.getAlertReasons().isEmpty())
        );
    }

    @Test
    void shouldRejectNullOrBlankServerCode() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new ServerHealthState(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ServerHealthState("")),
                () -> assertThrows(IllegalArgumentException.class, () -> new ServerHealthState("   "))
        );
    }
}

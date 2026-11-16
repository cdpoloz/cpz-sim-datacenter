package com.cpz.sim.datacenter.health;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Selects operator-facing reasons without modifying diagnostic health or simulation state. */
public record ServerAlertPolicy(boolean highUtilizationEnabled) {
    /** Older configurations retain their existing operator-visible utilization alerts. */
    public static ServerAlertPolicy defaults() {
        return new ServerAlertPolicy(true);
    }

    public boolean includes(ServerAlertReason reason) {
        Objects.requireNonNull(reason, "reason");
        return highUtilizationEnabled || reason != ServerAlertReason.HIGH_UTILIZATION;
    }

    public Set<ServerAlertReason> visibleReasons(Set<ServerAlertReason> diagnosticReasons) {
        Objects.requireNonNull(diagnosticReasons, "diagnosticReasons");
        var selected = EnumSet.noneOf(ServerAlertReason.class);
        for (ServerAlertReason reason : diagnosticReasons)
            if (includes(reason)) selected.add(reason);
        return Set.copyOf(selected);
    }
}

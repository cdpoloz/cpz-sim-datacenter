package com.cpz.sim.datacenter.health;

import java.util.Objects;

/**
 * Runtime configuration for server health evaluation.
 *
 * @author CPZ
 */
public record ServerHealthOptions(
        HealthThreshold utilizationThreshold,
        HealthThreshold temperatureThreshold
) {

    public ServerHealthOptions {
        Objects.requireNonNull(utilizationThreshold, "utilizationThreshold must not be null.");
        Objects.requireNonNull(temperatureThreshold, "temperatureThreshold must not be null.");
        validateUtilizationThreshold(utilizationThreshold);
    }

    /**
     * Returns baseline health thresholds.
     *
     * <p>These defaults can be replaced by the optional health block in the
     * datacenter JSON configuration.
     */
    public static ServerHealthOptions defaults() {
        return new ServerHealthOptions(
                new HealthThreshold(0.90, 0.85),    // utilization
                new HealthThreshold(80.0, 75.0)     // temperature
        );
    }

    private static void validateUtilizationThreshold(HealthThreshold threshold) {
        if (threshold.clearAtOrBelow() < 0.0 || threshold.clearAtOrBelow() > 1.0)
            throw new IllegalArgumentException("utilization clearAtOrBelow must be within [0, 1].");
        if (threshold.alertAtOrAbove() < 0.0 || threshold.alertAtOrAbove() > 1.0)
            throw new IllegalArgumentException("utilization alertAtOrAbove must be within [0, 1].");
    }
}

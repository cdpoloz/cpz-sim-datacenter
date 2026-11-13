package com.cpz.sim.datacenter.health;

import java.util.Objects;

/**
 * Runtime configuration for server health evaluation.
 *
 * @author CPZ
 */
public record ServerHealthOptions(
        HealthThreshold utilizationThreshold,
        HealthThreshold temperatureThreshold,
        HealthThreshold criticalTemperatureThreshold
) {

    public ServerHealthOptions {
        Objects.requireNonNull(utilizationThreshold, "utilizationThreshold must not be null.");
        Objects.requireNonNull(temperatureThreshold, "temperatureThreshold must not be null.");
        validateUtilizationThreshold(utilizationThreshold);
        if (criticalTemperatureThreshold != null) {
            if (criticalTemperatureThreshold.alertAtOrAbove() <= temperatureThreshold.alertAtOrAbove()
                    || criticalTemperatureThreshold.clearAtOrBelow() < temperatureThreshold.alertAtOrAbove())
                throw new IllegalArgumentException("Critical temperature activation must exceed elevated activation; critical recovery must be at or above elevated activation.");
        }
    }

    /** Preserves the previous constructor; critical evaluation is disabled. */
    public ServerHealthOptions(HealthThreshold utilizationThreshold, HealthThreshold temperatureThreshold) {
        this(utilizationThreshold, temperatureThreshold, null);
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

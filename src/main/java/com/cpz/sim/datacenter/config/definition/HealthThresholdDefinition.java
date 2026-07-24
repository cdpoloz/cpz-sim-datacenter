package com.cpz.sim.datacenter.config.definition;

/**
 * JSON definition for one health threshold with hysteresis.
 *
 * @author CPZ
 */
public record HealthThresholdDefinition(
        double alertAtOrAbove,
        double clearAtOrBelow
) {
}

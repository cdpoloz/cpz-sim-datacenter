package com.cpz.sim.datacenter.health;

/**
 * Defines the activation and clearing limits for a health condition.
 *
 * <p>The separation between both values provides hysteresis:
 *
 * <ul>
 *     <li>A condition becomes active at or above {@code alertAtOrAbove}.</li>
 *     <li>An active condition is cleared at or below {@code clearAtOrBelow}.</li>
 *     <li>Inside the interval between both values, the previous condition
 *     state is preserved.</li>
 * </ul>
 * @author CPZ
 */
public record HealthThreshold(
        double alertAtOrAbove,
        double clearAtOrBelow
) {

    public HealthThreshold {
        requireFinite(alertAtOrAbove, "alertAtOrAbove");
        requireFinite(clearAtOrBelow, "clearAtOrBelow");
        if (clearAtOrBelow >= alertAtOrAbove) throw new IllegalArgumentException("clearAtOrBelow must be less than alertAtOrAbove.");
    }

    /**
     * Evaluates the condition while preserving its previous state inside the
     * hysteresis interval.
     *
     * @param value current measured value
     * @param currentlyActive whether the condition was active previously
     * @return the resulting condition state
     */
    public boolean evaluate(double value, boolean currentlyActive) {
        requireFinite(value, "value");
        if (currentlyActive) return value > clearAtOrBelow;
        return value >= alertAtOrAbove;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite.");
    }
}

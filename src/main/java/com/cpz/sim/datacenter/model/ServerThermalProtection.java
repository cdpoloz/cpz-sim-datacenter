package com.cpz.sim.datacenter.model;

import java.util.Objects;

/** All thresholds refer to the model's representative internal server temperature, not CPU Tjunction. */
public record ServerThermalProtection(
        boolean enabled,
        double throttleTemperatureCelsius,
        double throttleRecoveryTemperatureCelsius,
        double throttledMaxUtilization,
        double shutdownTemperatureCelsius,
        double restartTemperatureCelsius,
        double minimumShutdownSeconds,
        ThermalRestartPolicy restartPolicy,
        double shutdownPowerWatts
) {
    public ServerThermalProtection {
        Objects.requireNonNull(restartPolicy, "restartPolicy");
        if (!Double.isFinite(throttleTemperatureCelsius)
                || !Double.isFinite(throttleRecoveryTemperatureCelsius)
                || !Double.isFinite(shutdownTemperatureCelsius)
                || !Double.isFinite(restartTemperatureCelsius)
                || !(restartTemperatureCelsius <= throttleRecoveryTemperatureCelsius
                && throttleRecoveryTemperatureCelsius < throttleTemperatureCelsius
                && throttleTemperatureCelsius < shutdownTemperatureCelsius))
            throw new IllegalArgumentException("thermalProtection requires finite restart <= throttleRecovery < throttle < shutdown temperatures");
        if (!Double.isFinite(throttledMaxUtilization) || throttledMaxUtilization < 0 || throttledMaxUtilization >= 1)
            throw new IllegalArgumentException("throttledMaxUtilization must be in [0, 1)");
        if (!Double.isFinite(minimumShutdownSeconds) || minimumShutdownSeconds < 0)
            throw new IllegalArgumentException("minimumShutdownSeconds must be finite and >= 0");
        if (!Double.isFinite(shutdownPowerWatts) || shutdownPowerWatts < 0)
            throw new IllegalArgumentException("shutdownPowerWatts must be finite and >= 0");
    }
}

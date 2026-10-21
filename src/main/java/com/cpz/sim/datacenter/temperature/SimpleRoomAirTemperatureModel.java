package com.cpz.sim.datacenter.temperature;

import java.util.Objects;

/**
 * Exact constant-input solution of C dT/dt = Q - UA (T - T_environment).
 * The room is a single well-mixed thermal node. No temperature clamp is applied.
 */
public final class SimpleRoomAirTemperatureModel {

    public double nextTemperatureCelsius(
            double currentTemperatureCelsius,
            double deltaSeconds,
            RoomAirThermalInput input,
            RoomAirTemperatureOptions options
    ) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(options, "options must not be null");
        if (!Double.isFinite(currentTemperatureCelsius))
            throw new IllegalArgumentException("currentTemperatureCelsius must be finite");
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0)
            throw new IllegalArgumentException("deltaSeconds must be finite and > 0");

        double capacity = options.effectiveThermalCapacityJoulesPerCelsius();
        double conductance = options.environmentHeatTransferWattsPerCelsius();
        double nextTemperature;
        if (conductance == 0.0) {
            nextTemperature = currentTemperatureCelsius + input.netHeatWatts() / capacity * deltaSeconds;
        } else {
            double exponent = conductance / capacity * deltaSeconds;
            double relaxation = -Math.expm1(-exponent);
            // Avoid Q / UA overflow and cancellation when UA is close to zero.
            double forcingGain = exponent < 1.0e-6
                    ? deltaSeconds / capacity * (exponent == 0.0 ? 1.0 : relaxation / exponent)
                    : relaxation / conductance;
            nextTemperature = currentTemperatureCelsius
                    + (options.environmentTemperatureCelsius() - currentTemperatureCelsius) * relaxation
                    + input.netHeatWatts() * forcingGain;
        }
        if (!Double.isFinite(nextTemperature))
            throw new IllegalArgumentException("room-air temperature calculation produced a non-finite result");
        return nextTemperature;
    }
}

package com.cpz.sim.datacenter.workload;

import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.time.SimulationTick;
import com.cpz.utils.noise.NoiseSource;

import java.util.Objects;

/**
 * @author CPZ
 */
public class NoiseWorkloadSource implements WorkloadSource {

    private final NoiseSource noiseSource;
    private final double speed;
    private final float minUtilization;
    private final float maxUtilization;

    public NoiseWorkloadSource(NoiseSource noiseSource, double speed, float minUtilization, float maxUtilization) {
        this.noiseSource = Objects.requireNonNull(noiseSource, "noiseSource cannot be null");
        validateFiniteNonNegative(speed, "speed");
        validateUtilization(minUtilization, "minUtilization");
        validateUtilization(maxUtilization, "maxUtilization");
        if (minUtilization > maxUtilization)
            throw new IllegalArgumentException("minUtilization cannot be greater than maxUtilization");
        this.speed = speed;
        this.minUtilization = minUtilization;
        this.maxUtilization = maxUtilization;
    }

    private static void validateFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0f)
            throw new IllegalArgumentException(name + " must be finite and non-negative");
    }

    private static void validateUtilization(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f)
            throw new IllegalArgumentException(name + " must be finite and within [0, 1]");
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float deterministicOffset(Server server) {
        String code = server.getCode();
        int hash = stableHash(code);
        int bucket = Math.floorMod(hash, 10_000);
        return bucket / 10_000.0f * 1_000.0f;
    }

    private static int stableHash(String value) {
        if (value == null) return -1;
        int hash = 17;
        for (int i = 0; i < value.length(); i++) hash = 31 * hash + value.charAt(i);
        return hash;
    }

    @Override
    public double getUtilization(Server server, SimulationTick tick) {
        Objects.requireNonNull(server, "server cannot be null");
        Objects.requireNonNull(tick, "tick cannot be null");
        double timePosition = (float) (tick.elapsedSeconds() * speed);
        double serverOffset = deterministicOffset(server);
        float noiseValue = noiseSource.noise((float) (timePosition + serverOffset));
        float normalizedNoise = clamp(noiseValue, 0.0f, 1.0f);
        return minUtilization + normalizedNoise * (maxUtilization - minUtilization);
    }

}

package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.time.SimulationTick;
import com.cpz.utils.noise.NoiseSource;

import java.util.Objects;

/**
 * Generates variable server power for power-driven simulations.
 *
 * <p>The source produces a dynamic power ratio using noise, applies a role-based
 * factor, clamps the result to the server's configured power range, and returns
 * watts between idle and maximum power.</p>
 *
 * @author CPZ
 */
public final class NoiseServerPowerInputSource implements ServerPowerInputSource {

    private final NoiseSource noiseSource;
    private final ServerRolePowerFactorProvider factorProvider;
    private final double speed;
    private final double minDynamicPowerRatio;
    private final double maxDynamicPowerRatio;

    public NoiseServerPowerInputSource(
            NoiseSource noiseSource,
            double speed,
            double minDynamicPowerRatio,
            double maxDynamicPowerRatio
    ) {
        this(noiseSource, new ServerRolePowerFactorProvider(), speed, minDynamicPowerRatio, maxDynamicPowerRatio);
    }

    public NoiseServerPowerInputSource(
            NoiseSource noiseSource,
            ServerRolePowerFactorProvider factorProvider,
            double speed,
            double minDynamicPowerRatio,
            double maxDynamicPowerRatio
    ) {
        this.noiseSource = Objects.requireNonNull(noiseSource, "noiseSource must not be null");
        this.factorProvider = Objects.requireNonNull(factorProvider, "factorProvider must not be null");
        validateFiniteNonNegative(speed, "speed");
        validateRatio(minDynamicPowerRatio, "minDynamicPowerRatio");
        validateRatio(maxDynamicPowerRatio, "maxDynamicPowerRatio");
        if (minDynamicPowerRatio > maxDynamicPowerRatio)
            throw new IllegalArgumentException("minDynamicPowerRatio cannot be greater than maxDynamicPowerRatio");
        this.speed = speed;
        this.minDynamicPowerRatio = minDynamicPowerRatio;
        this.maxDynamicPowerRatio = maxDynamicPowerRatio;
    }

    @Override
    public double currentPowerWatts(Server server, SimulationTick tick) {
        Objects.requireNonNull(server, "server must not be null");
        Objects.requireNonNull(tick, "tick must not be null");
        double timePosition = tick.elapsedSeconds() * speed;
        double serverOffset = deterministicOffset(server);
        float noiseValue = noiseSource.noise((float) (timePosition + serverOffset));
        double normalizedNoise = clamp(noiseValue, 0.0, 1.0);
        double baseDynamicRatio = minDynamicPowerRatio + normalizedNoise * (maxDynamicPowerRatio - minDynamicPowerRatio);
        double adjustedDynamicRatio = clamp(baseDynamicRatio * factorProvider.factorFor(server), 0.0, 1.0);
        double idlePowerWatts = server.getConfig().idlePowerWatts();
        double maxPowerWatts = server.getConfig().maxPowerWatts();
        return idlePowerWatts + adjustedDynamicRatio * (maxPowerWatts - idlePowerWatts);
    }

    private static void validateFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException(name + " must be finite and non-negative");
    }

    private static void validateRatio(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) throw new IllegalArgumentException(name + " must be finite and within [0, 1]");
    }

    private static double clamp(double value, double min, double max) {
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
}
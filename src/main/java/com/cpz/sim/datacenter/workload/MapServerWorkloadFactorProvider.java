package com.cpz.sim.datacenter.workload;

import com.cpz.sim.datacenter.model.Server;

import java.util.Map;
import java.util.Objects;

/**
 * @author CPZ
 */
public final class MapServerWorkloadFactorProvider implements ServerWorkloadFactorProvider {

    private final Map<String, Float> factorsByServerCode;
    private final float defaultFactor;

    public MapServerWorkloadFactorProvider(Map<String, Float> factorsByServerCode) {
        this(factorsByServerCode, 1.0f);
    }

    public MapServerWorkloadFactorProvider(Map<String, Float> factorsByServerCode, float defaultFactor) {
        this.factorsByServerCode = Map.copyOf(
                Objects.requireNonNull(factorsByServerCode, "factorsByServerCode cannot be null")
        );
        validateFactor(defaultFactor, "defaultFactor");
        for (Map.Entry<String, Float> entry : this.factorsByServerCode.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank())
                throw new IllegalArgumentException("server code cannot be null or blank");
            if (entry.getValue() == null)
                throw new IllegalArgumentException("factor cannot be null for server code: " + entry.getKey());
            validateFactor(entry.getValue(), "factor for server code: " + entry.getKey());
        }
        this.defaultFactor = defaultFactor;
    }

    private static void validateFactor(float factor, String name) {
        if (!Float.isFinite(factor) || factor < 0.0f)
            throw new IllegalArgumentException(name + " must be finite and non-negative");
    }

    @Override
    public float getFactor(Server server) {
        Objects.requireNonNull(server, "server cannot be null");
        return factorsByServerCode.getOrDefault(server.getCode(), defaultFactor);
    }
}

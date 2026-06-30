package com.cpz.sim.datacenter.workload;

import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;

/**
 * @author CPZ
 */
public class ScaledWorkloadSource implements WorkloadSource {

    private final WorkloadSource delegate;
    private final ServerWorkloadFactorProvider factorProvider;

    public ScaledWorkloadSource(WorkloadSource delegate, ServerWorkloadFactorProvider factorProvider) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.factorProvider = Objects.requireNonNull(factorProvider, "factorProvider cannot be null");
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public float getUtilization(Server server, SimulationTick tick) {
        Objects.requireNonNull(server, "server cannot be null");
        Objects.requireNonNull(tick, "tick cannot be null");
        float baseUtilization = delegate.getUtilization(server, tick);
        float factor = factorProvider.getFactor(server);
        return clamp(baseUtilization * factor, 0.0f, 1.0f);
    }

    @Override
    public void reset() {
        delegate.reset();
    }
}

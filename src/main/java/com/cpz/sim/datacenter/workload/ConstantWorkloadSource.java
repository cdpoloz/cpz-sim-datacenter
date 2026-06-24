package com.cpz.sim.datacenter.workload;

import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.time.SimulationTick;

/**
 * @author CPZ
 */
public class ConstantWorkloadSource implements WorkloadSource {

    private final float utilization;

    public ConstantWorkloadSource(float utilization) {
        if (!Float.isFinite(utilization) || utilization < 0.0f || utilization > 1.0f) {
            throw new IllegalArgumentException("utilization must be finite and within [0, 1]");
        }
        this.utilization = utilization;
    }

    @Override
    public float getUtilization(Server server, SimulationTick tick) {
        return utilization;
    }

    public float getUtilization() {
        return utilization;
    }
}

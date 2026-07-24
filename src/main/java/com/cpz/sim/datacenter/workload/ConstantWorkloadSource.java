package com.cpz.sim.datacenter.workload;

import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.time.SimulationTick;

/**
 * @author CPZ
 */
public class ConstantWorkloadSource implements WorkloadSource {

    private final double utilization;

    public ConstantWorkloadSource(double utilization) {
        if (!Double.isFinite(utilization) || utilization < 0.0 || utilization > 1.0) {
            throw new IllegalArgumentException("utilization must be finite and within [0, 1]");
        }
        this.utilization = utilization;
    }

    @Override
    public double getUtilization(Server server, SimulationTick tick) {
        return utilization;
    }

    public double getUtilization() {
        return utilization;
    }
}

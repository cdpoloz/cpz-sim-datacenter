package com.cpz.sim.datacenter.workload;

import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.time.SimulationTick;

/**
 * @author CPZ
 */
@FunctionalInterface
public interface WorkloadSource {

    float getUtilization(Server server, SimulationTick tick);

    default void reset() {
    }
}

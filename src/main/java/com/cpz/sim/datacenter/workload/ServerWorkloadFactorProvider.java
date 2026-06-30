package com.cpz.sim.datacenter.workload;

import com.cpz.sim.datacenter.model.Server;

/**
 * @author CPZ
 */
@FunctionalInterface
public interface ServerWorkloadFactorProvider {

    float getFactor(Server server);
}

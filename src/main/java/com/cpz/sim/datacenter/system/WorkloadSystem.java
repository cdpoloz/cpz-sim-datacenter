package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;

/**
 * @author CPZ
 */
public class WorkloadSystem implements Simulatable {

    private final Datacenter datacenter;
    private final WorkloadSource workloadSource;

    public WorkloadSystem(Datacenter datacenter, WorkloadSource workloadSource) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter cannot be null");
        this.workloadSource = Objects.requireNonNull(workloadSource, "workloadSource cannot be null");
    }

    @Override
    public void update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick cannot be null");
        for (Server server : datacenter.getServers()) {
            if (server.getStatus() == HardwareStatus.OFFLINE) {
                server.setUtilization(0.0f);
                continue;
            }
            float utilization = workloadSource.getUtilization(server, tick);
            server.setUtilization(utilization);
        }
    }

    @Override
    public void reset() {
        workloadSource.reset();
        for (Server server : datacenter.getServers()) server.setUtilization(0.0f);
    }

}

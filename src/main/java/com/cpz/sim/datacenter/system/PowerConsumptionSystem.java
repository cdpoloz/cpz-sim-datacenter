package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;

/**
 * @author CPZ
 */
public final class PowerConsumptionSystem implements Simulatable {

    private final Datacenter datacenter;

    public PowerConsumptionSystem(Datacenter datacenter) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter cannot be null");
    }

    @Override
    public void update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick cannot be null");
        for (Server server : datacenter.getServers()) server.updatePowerConsumption();
    }

    @Override
    public void reset() {
        for (Server server : datacenter.getServers()) server.updatePowerConsumption();
    }

}

package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.input.ServerPowerInputSource;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;

/**
 * Updates server power from an external source for power-driven simulations.
 *
 * <p>This system is the power-driven counterpart of
 * {@link PowerConsumptionSystem}. It should be registered only when server power
 * is the authoritative input variable.</p>
 *
 * @author CPZ
 */
public final class PowerInputSystem implements Simulatable {

    private final Datacenter datacenter;
    private final ServerPowerInputSource powerInputSource;

    /**
     * Creates a power input system.
     *
     * @param datacenter datacenter to update
     * @param powerInputSource source of current server power
     */
    public PowerInputSystem(Datacenter datacenter, ServerPowerInputSource powerInputSource) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
        this.powerInputSource = Objects.requireNonNull(powerInputSource, "powerInputSource must not be null");
    }

    @Override
    public void update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null");
        for (Server server : datacenter.getServers()) {
            if (server.getStatus() == HardwareStatus.OFFLINE) {
                server.setCurrentPowerWatts(0.0);
                server.estimateUtilizationFromCurrentPower();
                continue;
            }
            server.setCurrentPowerWatts(powerInputSource.currentPowerWatts(server, tick));
            server.estimateUtilizationFromCurrentPower();
        }
    }

    @Override
    public void reset() {
        for (Server server : datacenter.getServers()) {
            server.setCurrentPowerWatts(0.0);
            server.estimateUtilizationFromCurrentPower();
        }
    }
}

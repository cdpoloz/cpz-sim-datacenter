package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;

import java.util.List;
import java.util.Objects;

/**
 * Creates instantaneous server heat loads from the current state of a
 * datacenter.
 *
 * <p>The current model assumes that all electrical power consumed by a server
 * is ultimately released as heat inside the datacenter. Therefore, one watt
 * of server power produces one watt of thermal load.</p>
 *
 * <p>This provider must be invoked after
 * {@link com.cpz.sim.datacenter.system.PowerConsumptionSystem} has updated
 * server power for the current simulation tick.</p>
 *
 * @author CPZ
 */
public final class ServerHeatLoadProvider {

    private final Datacenter datacenter;

    /**
     * Creates a heat-load provider for a datacenter.
     *
     * @param datacenter datacenter whose server loads will be produced
     *
     * @throws NullPointerException if {@code datacenter} is {@code null}
     */
    public ServerHeatLoadProvider(Datacenter datacenter) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
    }

    /**
     * Creates one heat load for every installed server, preserving the server
     * order exposed by the datacenter.
     *
     * <p>Offline servers are also represented. Because their current power is
     * zero, their generated heat load is zero.</p>
     *
     * @return immutable list of current server heat loads
     */
    public List<ServerHeatLoad> createHeatLoads() {
        return datacenter.getServers().stream().map(ServerHeatLoadProvider::createHeatLoad).toList();
    }

    private static ServerHeatLoad createHeatLoad(Server server) {
        return new ServerHeatLoad(server.getLocation(), server.getCurrentPowerWatts());
    }
}
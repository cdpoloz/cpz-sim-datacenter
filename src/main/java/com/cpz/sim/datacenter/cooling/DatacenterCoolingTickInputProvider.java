package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates cooling tick inputs from the current power consumption of the
 * servers installed in a datacenter.
 *
 * <p>Each server contributes one heat load whose generated heat is equal to
 * its current electrical power consumption. Consequently, this provider must
 * be consulted after {@code PowerConsumptionSystem} has processed the
 * simulation tick.</p>
 *
 * @author CPZ
 */
public final class DatacenterCoolingTickInputProvider {

    private final Datacenter datacenter;

    /**
     * Creates a provider for the given datacenter.
     *
     * @param datacenter datacenter containing the installed servers
     *
     * @throws NullPointerException if {@code datacenter} is {@code null}
     */
    public DatacenterCoolingTickInputProvider(Datacenter datacenter) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
    }

    /**
     * Creates the cooling input corresponding to the given simulation tick.
     *
     * @param tick current simulation tick
     * @return cooling input containing one heat load per installed server
     *
     * @throws NullPointerException if {@code tick} is {@code null}
     */
    public CoolingTickInput inputFor(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null");
        List<ServerHeatLoad> heatLoads = new ArrayList<>();
        for (Server server : datacenter.getServers())
            heatLoads.add(new ServerHeatLoad(server.getLocation(), server.getCurrentPowerWatts()));
        return new CoolingTickInput(tick.index(), tick.deltaSeconds(), heatLoads);
    }
}
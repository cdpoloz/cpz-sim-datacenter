package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerEnergySnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Converts an energy consumption snapshot into the thermal input required by
 * the cooling system.
 *
 * <p>This adapter uses the simplifying assumption that the instantaneous
 * electrical power consumed by a server is converted entirely into heat.</p>
 *
 * @author CPZ
 */
public final class EnergySnapshotCoolingTickInputAdapter {

    /**
     * Converts an energy consumption snapshot into a cooling tick input.
     *
     * @param energySnapshot energy snapshot to convert
     * @return cooling input containing one heat load per server
     *
     * @throws NullPointerException     if {@code energySnapshot} is {@code null}
     *                                  or contains a null server snapshot
     * @throws IllegalArgumentException if more than one server snapshot has the
     *                                  same location
     */
    public CoolingTickInput adapt(EnergyConsumptionSnapshot energySnapshot) {
        Objects.requireNonNull(energySnapshot, "energySnapshot must not be null");
        List<ServerEnergySnapshot> serverSnapshots = energySnapshot.servers();
        Set<Object> encounteredLocations = new HashSet<>();
        List<ServerHeatLoad> serverHeatLoads = serverSnapshots
                .stream()
                .map(serverSnapshot -> toServerHeatLoad(serverSnapshot, encounteredLocations))
                .toList();
        return new CoolingTickInput(energySnapshot.tickIndex(), serverHeatLoads);
    }

    private ServerHeatLoad toServerHeatLoad(ServerEnergySnapshot serverSnapshot, Set<Object> encounteredLocations) {
        Objects.requireNonNull(serverSnapshot, "energySnapshot must not contain null server snapshots");
        if (!encounteredLocations.add(serverSnapshot.location()))
            throw new IllegalArgumentException("energySnapshot must not contain duplicate server locations: " + serverSnapshot.location());
        return new ServerHeatLoad(serverSnapshot.location(), serverSnapshot.currentPowerWatts());
    }
}
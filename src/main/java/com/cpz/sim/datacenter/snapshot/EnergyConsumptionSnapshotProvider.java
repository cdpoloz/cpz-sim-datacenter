package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.List;
import java.util.Objects;

/**
 * @author CPZ
 */
public final class EnergyConsumptionSnapshotProvider {

    private final Datacenter datacenter;
    private final EnergyConsumptionSystem energyConsumptionSystem;

    public EnergyConsumptionSnapshotProvider(Datacenter datacenter, EnergyConsumptionSystem energyConsumptionSystem) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
        this.energyConsumptionSystem = Objects.requireNonNull(energyConsumptionSystem, "energyConsumptionSystem must not be null");
    }

    public EnergyConsumptionSnapshot snapshot(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null");
        List<ServerEnergySnapshot> serverSnapshots = datacenter.getServers()
                .stream()
                .map(this::snapshotServer)
                .toList();
        return new EnergyConsumptionSnapshot(
                tick.index(),
                tick.elapsedSeconds(),
                datacenter.getTotalItPowerWatts(),
                energyConsumptionSystem.getConsumedEnergyWh(),
                serverSnapshots
        );
    }

    private ServerEnergySnapshot snapshotServer(Server server) {
        return new ServerEnergySnapshot(
                server.getCode(),
                server.getLocation().rackCode().value(),
                server.getLocation().slot(),
                server.getStatus().name(),
                server.getUtilization(),
                server.getCurrentPowerWatts()
        );
    }
}

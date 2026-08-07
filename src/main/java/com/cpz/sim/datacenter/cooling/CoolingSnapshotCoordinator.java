package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.system.CoolingSystem;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;

/**
 * Coordinates the generation and propagation of cooling snapshots.
 *
 * <p>For each simulation tick, this coordinator obtains the current server heat
 * loads, executes the cooling system and installs the resulting snapshot in the
 * temperature reference provider.</p>
 *
 * <p>{@code PowerConsumptionSystem} must process the simulation tick before this
 * coordinator so that server heat loads reflect their current electrical power
 * consumption.</p>
 *
 * @author CPZ
 */
public final class CoolingSnapshotCoordinator {

    private final DatacenterCoolingTickInputProvider inputProvider;
    private final CoolingSystem coolingSystem;
    private final CoolingSnapshotTemperatureReferenceProvider temperatureReferenceProvider;

    /**
     * Creates a cooling snapshot coordinator.
     *
     * @param inputProvider provider of cooling inputs
     * @param coolingSystem cooling system to execute
     * @param temperatureReferenceProvider destination of generated snapshots
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoolingSnapshotCoordinator(
            DatacenterCoolingTickInputProvider inputProvider,
            CoolingSystem coolingSystem,
            CoolingSnapshotTemperatureReferenceProvider temperatureReferenceProvider
    ) {
        this.inputProvider = Objects.requireNonNull(inputProvider, "inputProvider must not be null");
        this.coolingSystem = Objects.requireNonNull(coolingSystem, "coolingSystem must not be null");
        this.temperatureReferenceProvider = Objects.requireNonNull(temperatureReferenceProvider, "temperatureReferenceProvider must not be null");
    }

    /**
     * Executes cooling for the given tick and propagates its snapshot to the
     * temperature reference provider.
     *
     * @param tick current simulation tick
     * @return generated cooling snapshot
     *
     * @throws NullPointerException if {@code tick} is {@code null}
     */
    public CoolingSnapshot update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null");
        CoolingTickInput input = inputProvider.inputFor(tick);
        CoolingSnapshot snapshot = coolingSystem.tick(input);
        temperatureReferenceProvider.updateSnapshot(snapshot);
        return snapshot;
    }
}
package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.cooling.CoolingTickInput;
import com.cpz.sim.datacenter.cooling.ServerHeatLoadProvider;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;
import java.util.Optional;

/**
 * Integrates the cooling calculation into the simulation engine.
 *
 * <p>This system reads the current server power through
 * {@link ServerHeatLoadProvider}, executes {@link CoolingSystem}, and retains
 * the immutable snapshot produced for the latest simulation tick.</p>
 *
 * <p>It must be registered after {@link PowerConsumptionSystem}, so that the
 * generated heat loads reflect the power calculated for the current tick.</p>
 *
 * @author CPZ
 */
public final class CoolingSimulationSystem implements Simulatable {

    private final CoolingSystem coolingSystem;
    private final ServerHeatLoadProvider heatLoadProvider;
    private CoolingSnapshot lastSnapshot;

    /**
     * Creates a simulation adapter for the cooling system.
     *
     * @param coolingSystem cooling calculation and operational-state owner
     * @param heatLoadProvider provider of current server thermal loads
     *
     * @throws NullPointerException if an argument is {@code null}
     */
    public CoolingSimulationSystem(CoolingSystem coolingSystem, ServerHeatLoadProvider heatLoadProvider) {
        this.coolingSystem = Objects.requireNonNull(coolingSystem, "coolingSystem must not be null");
        this.heatLoadProvider = Objects.requireNonNull(heatLoadProvider, "heatLoadProvider must not be null");
    }

    /**
     * Executes the cooling calculation for a simulation tick.
     *
     * @param tick current simulation tick
     *
     * @throws NullPointerException if {@code tick} is {@code null}
     */
    @Override
    public void update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null");
        CoolingTickInput input = new CoolingTickInput(tick.index(), heatLoadProvider.createHeatLoads());
        lastSnapshot = coolingSystem.tick(input);
    }

    /**
     * Returns the most recently produced cooling snapshot.
     *
     * <p>The result is empty before the first invocation of
     * {@link #update(SimulationTick)}.</p>
     *
     * @return latest cooling snapshot, if one has been produced
     */
    public Optional<CoolingSnapshot> lastSnapshot() {
        return Optional.ofNullable(lastSnapshot);
    }

    /**
     * Returns the underlying cooling system.
     *
     * <p>This reference allows callers such as a development UI or keyboard
     * controller to enable and disable cooling units without placing
     * operational commands inside this simulation adapter.</p>
     *
     * @return underlying cooling system
     */
    public CoolingSystem coolingSystem() {
        return coolingSystem;
    }

    /**
     * Restores the cooling system and clears the snapshot produced by the
     * previous simulation run.
     */
    @Override
    public void reset() {
        coolingSystem.reset();
        lastSnapshot = null;
    }
}
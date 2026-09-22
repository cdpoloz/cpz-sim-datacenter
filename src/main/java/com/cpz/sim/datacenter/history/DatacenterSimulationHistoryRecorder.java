package com.cpz.sim.datacenter.history;

import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshot;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.HealthSnapshot;
import com.cpz.sim.datacenter.snapshot.HealthSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshotProvider;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Captures and stores complete historical snapshots after simulation steps.
 *
 * <p>Call {@link #record(SimulationTick)} after all simulation systems for the
 * tick have run. The recorder does not advance the simulation engine.</p>
 *
 * @author CPZ
 */
public final class DatacenterSimulationHistoryRecorder {

    private final EnergyConsumptionSnapshotProvider energySnapshotProvider;
    private final TemperatureSnapshotProvider temperatureSnapshotProvider;
    private final HealthSnapshotProvider healthSnapshotProvider;
    private final DatacenterOperationalSnapshotProvider operationalSnapshotProvider;
    private final Supplier<Optional<CoolingSnapshot>> coolingSnapshotSupplier;
    private final DatacenterSimulationHistory history;

    /**
     * Creates a history recorder without cooling snapshots.
     *
     * @param energySnapshotProvider energy snapshot provider
     * @param temperatureSnapshotProvider temperature snapshot provider
     * @param healthSnapshotProvider health snapshot provider
     * @param operationalSnapshotProvider operational snapshot provider
     */
    public DatacenterSimulationHistoryRecorder(
            EnergyConsumptionSnapshotProvider energySnapshotProvider,
            TemperatureSnapshotProvider temperatureSnapshotProvider,
            HealthSnapshotProvider healthSnapshotProvider,
            DatacenterOperationalSnapshotProvider operationalSnapshotProvider
    ) {
        this(
                energySnapshotProvider,
                temperatureSnapshotProvider,
                healthSnapshotProvider,
                operationalSnapshotProvider,
                Optional::empty,
                new DatacenterSimulationHistory()
        );
    }

    /**
     * Creates a history recorder.
     *
     * @param energySnapshotProvider energy snapshot provider
     * @param temperatureSnapshotProvider temperature snapshot provider
     * @param healthSnapshotProvider health snapshot provider
     * @param operationalSnapshotProvider operational snapshot provider
     * @param coolingSnapshotSupplier latest cooling snapshot supplier
     */
    public DatacenterSimulationHistoryRecorder(
            EnergyConsumptionSnapshotProvider energySnapshotProvider,
            TemperatureSnapshotProvider temperatureSnapshotProvider,
            HealthSnapshotProvider healthSnapshotProvider,
            DatacenterOperationalSnapshotProvider operationalSnapshotProvider,
            Supplier<Optional<CoolingSnapshot>> coolingSnapshotSupplier
    ) {
        this(
                energySnapshotProvider,
                temperatureSnapshotProvider,
                healthSnapshotProvider,
                operationalSnapshotProvider,
                coolingSnapshotSupplier,
                new DatacenterSimulationHistory()
        );
    }

    /**
     * Creates a history recorder using an existing history instance.
     *
     * @param energySnapshotProvider energy snapshot provider
     * @param temperatureSnapshotProvider temperature snapshot provider
     * @param healthSnapshotProvider health snapshot provider
     * @param operationalSnapshotProvider operational snapshot provider
     * @param coolingSnapshotSupplier latest cooling snapshot supplier
     * @param history destination history
     */
    public DatacenterSimulationHistoryRecorder(
            EnergyConsumptionSnapshotProvider energySnapshotProvider,
            TemperatureSnapshotProvider temperatureSnapshotProvider,
            HealthSnapshotProvider healthSnapshotProvider,
            DatacenterOperationalSnapshotProvider operationalSnapshotProvider,
            Supplier<Optional<CoolingSnapshot>> coolingSnapshotSupplier,
            DatacenterSimulationHistory history
    ) {
        this.energySnapshotProvider = Objects.requireNonNull(energySnapshotProvider, "energySnapshotProvider must not be null");
        this.temperatureSnapshotProvider = Objects.requireNonNull(temperatureSnapshotProvider, "temperatureSnapshotProvider must not be null");
        this.healthSnapshotProvider = Objects.requireNonNull(healthSnapshotProvider, "healthSnapshotProvider must not be null");
        this.operationalSnapshotProvider = Objects.requireNonNull(operationalSnapshotProvider, "operationalSnapshotProvider must not be null");
        this.coolingSnapshotSupplier = Objects.requireNonNull(coolingSnapshotSupplier, "coolingSnapshotSupplier must not be null");
        this.history = Objects.requireNonNull(history, "history must not be null");
    }

    /**
     * Captures and records a completed simulation tick.
     *
     * @param tick completed simulation tick
     * @return recorded step snapshot
     */
    public DatacenterSimulationStepSnapshot record(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null");
        EnergyConsumptionSnapshot energySnapshot = energySnapshotProvider.snapshot(tick);
        TemperatureSnapshot temperatureSnapshot = temperatureSnapshotProvider.snapshot(tick);
        HealthSnapshot healthSnapshot = healthSnapshotProvider.snapshot(tick);
        Optional<CoolingSnapshot> coolingSnapshot =
                Objects.requireNonNull(coolingSnapshotSupplier.get(), "coolingSnapshotSupplier must not return null");
        DatacenterOperationalSnapshot operationalSnapshot =
                coolingSnapshot
                        .map(snapshot -> operationalSnapshotProvider.snapshot(
                                energySnapshot,
                                temperatureSnapshot,
                                healthSnapshot,
                                snapshot
                        ))
                        .orElseGet(() -> operationalSnapshotProvider.snapshot(
                                energySnapshot,
                                temperatureSnapshot,
                                healthSnapshot
                        ));
        DatacenterSimulationStepSnapshot stepSnapshot =
                new DatacenterSimulationStepSnapshot(
                        energySnapshot,
                        temperatureSnapshot,
                        healthSnapshot,
                        coolingSnapshot,
                        operationalSnapshot
                );
        history.record(stepSnapshot);
        return stepSnapshot;
    }

    /**
     * Returns the destination history.
     *
     * @return simulation history
     */
    public DatacenterSimulationHistory history() {
        return history;
    }

    /**
     * Clears the destination history.
     */
    public void clear() {
        history.clear();
    }
}

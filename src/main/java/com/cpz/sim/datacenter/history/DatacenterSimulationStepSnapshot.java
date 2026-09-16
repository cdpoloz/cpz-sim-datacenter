package com.cpz.sim.datacenter.history;

import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshot;
import com.cpz.sim.datacenter.snapshot.HealthSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * Complete immutable datacenter state captured after one simulation step.
 *
 * <p>The record keeps the specialized snapshots instead of flattening their
 * fields, so callers can graph detailed series without losing the original
 * per-server, per-rack, per-column, and cooling-zone data.</p>
 *
 * @param energySnapshot energy state for the completed step
 * @param temperatureSnapshot temperature state for the completed step
 * @param healthSnapshot health state for the completed step
 * @param coolingSnapshot cooling state for the completed step, when a cooling
 *                        system participates in the simulation
 * @param operationalSnapshot aggregate operational view for the completed step
 *
 * @author CPZ
 */
public record DatacenterSimulationStepSnapshot(
        EnergyConsumptionSnapshot energySnapshot,
        TemperatureSnapshot temperatureSnapshot,
        HealthSnapshot healthSnapshot,
        Optional<CoolingSnapshot> coolingSnapshot,
        DatacenterOperationalSnapshot operationalSnapshot
) {

    public DatacenterSimulationStepSnapshot {
        Objects.requireNonNull(energySnapshot, "energySnapshot must not be null");
        Objects.requireNonNull(temperatureSnapshot, "temperatureSnapshot must not be null");
        Objects.requireNonNull(healthSnapshot, "healthSnapshot must not be null");
        Objects.requireNonNull(coolingSnapshot, "coolingSnapshot must not be null");
        Objects.requireNonNull(operationalSnapshot, "operationalSnapshot must not be null");
        coolingSnapshot.ifPresent(snapshot -> Objects.requireNonNull(snapshot, "coolingSnapshot must not contain null"));
        validateSameTickIndex(energySnapshot, temperatureSnapshot, healthSnapshot, coolingSnapshot, operationalSnapshot);
        validateSameElapsedSeconds(energySnapshot, temperatureSnapshot, healthSnapshot, operationalSnapshot);
    }

    /**
     * Returns the simulation tick index represented by this snapshot.
     *
     * @return tick index
     */
    public long tickIndex() {
        return operationalSnapshot.tickIndex();
    }

    /**
     * Returns elapsed simulation time in seconds.
     *
     * @return elapsed seconds
     */
    public double elapsedSeconds() {
        return operationalSnapshot.elapsedSeconds();
    }

    /**
     * Returns whether this step includes cooling data.
     *
     * @return {@code true} when a cooling snapshot is present
     */
    public boolean hasCoolingSnapshot() {
        return coolingSnapshot.isPresent();
    }

    private static void validateSameTickIndex(
            EnergyConsumptionSnapshot energySnapshot,
            TemperatureSnapshot temperatureSnapshot,
            HealthSnapshot healthSnapshot,
            Optional<CoolingSnapshot> coolingSnapshot,
            DatacenterOperationalSnapshot operationalSnapshot
    ) {
        long tickIndex = operationalSnapshot.tickIndex();
        if (energySnapshot.tickIndex() != tickIndex
                || temperatureSnapshot.tickIndex() != tickIndex
                || healthSnapshot.tickIndex() != tickIndex)
            throw new IllegalArgumentException("All step snapshots must have the same tickIndex");
        if (coolingSnapshot.isPresent() && coolingSnapshot.orElseThrow().tickIndex() != tickIndex)
            throw new IllegalArgumentException("Cooling snapshot must have the same tickIndex");
    }

    private static void validateSameElapsedSeconds(
            EnergyConsumptionSnapshot energySnapshot,
            TemperatureSnapshot temperatureSnapshot,
            HealthSnapshot healthSnapshot,
            DatacenterOperationalSnapshot operationalSnapshot
    ) {
        double elapsedSeconds = operationalSnapshot.elapsedSeconds();
        if (Double.compare(energySnapshot.elapsedSeconds(), elapsedSeconds) != 0
                || Double.compare(temperatureSnapshot.elapsedSeconds(), elapsedSeconds) != 0
                || Double.compare(healthSnapshot.elapsedSeconds(), elapsedSeconds) != 0)
            throw new IllegalArgumentException("All elapsed step snapshots must have the same elapsedSeconds");
    }
}

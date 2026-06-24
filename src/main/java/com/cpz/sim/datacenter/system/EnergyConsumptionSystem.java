package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Objects;

/**
 * @author CPZ
 */
public class EnergyConsumptionSystem implements Simulatable {

    private final Datacenter datacenter;
    private double consumedEnergyWh;

    public EnergyConsumptionSystem(Datacenter datacenter) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter cannot be null");
    }

    @Override
    public void update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick cannot be null");
        double elapsedHours = tick.deltaSeconds() / 3600.0;
        consumedEnergyWh += datacenter.getTotalItPowerWatts() * elapsedHours;
    }

    @Override
    public void reset() {
        consumedEnergyWh = 0.0;
    }

    public double getConsumedEnergyWh() {
        return consumedEnergyWh;
    }

    public double getConsumedEnergyKWh() {
        return consumedEnergyWh / 1000.0;
    }
}

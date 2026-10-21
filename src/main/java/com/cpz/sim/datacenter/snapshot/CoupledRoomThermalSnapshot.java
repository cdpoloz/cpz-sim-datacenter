package com.cpz.sim.datacenter.snapshot;

import java.util.Objects;

/**
 * Integrated energy ledger for one coupled tick, in joules. Transfers to air
 * and losses to the environment are signed. Electrical input here is IT only;
 * HVAC electrical consumption is reported independently by cooling unit snapshots.
 */
public record CoupledRoomThermalSnapshot(
        RoomAirTemperatureSnapshot roomAir,
        double inputElectricalEnergyJoules,
        double serverEnergyChangeJoules,
        double roomAirEnergyChangeJoules,
        double heatTransferredToAirJoules,
        double removedHeatJoules,
        double environmentHeatLossJoules
) {
    public CoupledRoomThermalSnapshot {
        Objects.requireNonNull(roomAir, "roomAir must not be null");
        for (double value : new double[]{inputElectricalEnergyJoules, serverEnergyChangeJoules,
                roomAirEnergyChangeJoules, heatTransferredToAirJoules, removedHeatJoules, environmentHeatLossJoules})
            if (!Double.isFinite(value)) throw new IllegalArgumentException("energy ledger must be finite");
        if (inputElectricalEnergyJoules < 0.0 || removedHeatJoules < 0.0)
            throw new IllegalArgumentException("IT electrical input and removed heat must be >= 0");
    }

    /** Zero within numerical tolerance when the modeled boundary conserves energy. */
    public double energyBalanceResidualJoules() {
        return inputElectricalEnergyJoules - removedHeatJoules - environmentHeatLossJoules
                - serverEnergyChangeJoules - roomAirEnergyChangeJoules;
    }
}

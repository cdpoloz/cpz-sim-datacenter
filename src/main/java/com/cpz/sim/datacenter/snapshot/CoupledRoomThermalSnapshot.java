package com.cpz.sim.datacenter.snapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * Integrated energy ledger for one coupled tick, in joules. Transfers to air
 * and losses to the environment are signed. Electrical input here is IT only;
 * legacy room HVAC electrical heat remains outside this boundary. New rack unit
 * electricity and air storage are recorded separately. removedHeatJoules keeps
 * its original meaning: existing room cooling removal only. Server-to-air heat
 * includes exchange with local rack air as well as direct room air. Rack-room
 * passive exchange is signed; refrigeration and rejected heat are non-negative.
 */
public record CoupledRoomThermalSnapshot(
        RoomAirTemperatureSnapshot roomAir,
        double inputElectricalEnergyJoules,
        double serverEnergyChangeJoules,
        double roomAirEnergyChangeJoules,
        double heatTransferredToAirJoules,
        double removedHeatJoules,
        double environmentHeatLossJoules,
        double rackAirEnergyChangeJoules,
        double rackCoolingElectricalEnergyJoules,
        double rackRemovedHeatJoules,
        double rackHeatRejectedToRoomJoules,
        double rackHeatRejectedExternalJoules,
        double rackHeatTransferredToRoomJoules,
        Optional<RackCoolingSnapshot> rackCoolingSnapshot,
        Optional<FacilityEnergySnapshot> facilityEnergySnapshot
) {
    public CoupledRoomThermalSnapshot {
        Objects.requireNonNull(roomAir, "roomAir must not be null");
        Objects.requireNonNull(rackCoolingSnapshot, "rackCoolingSnapshot must not be null");
        Objects.requireNonNull(facilityEnergySnapshot, "facilityEnergySnapshot must not be null");
        rackCoolingSnapshot.ifPresent(rack -> {
            if (rack.tickIndex() != roomAir.tickIndex() || Double.compare(rack.elapsedSeconds(), roomAir.elapsedSeconds()) != 0)
                throw new IllegalArgumentException("rack cooling and thermal ledger must represent the same capture");
        });
        facilityEnergySnapshot.ifPresent(energy -> {
            if (energy.tickIndex() != roomAir.tickIndex() || Double.compare(energy.elapsedSeconds(), roomAir.elapsedSeconds()) != 0)
                throw new IllegalArgumentException("facility electricity and thermal ledger must represent the same capture");
        });
        for (double value : new double[]{inputElectricalEnergyJoules, serverEnergyChangeJoules,
                roomAirEnergyChangeJoules, heatTransferredToAirJoules, removedHeatJoules, environmentHeatLossJoules, rackAirEnergyChangeJoules,
                rackCoolingElectricalEnergyJoules, rackRemovedHeatJoules, rackHeatRejectedToRoomJoules,
                rackHeatRejectedExternalJoules, rackHeatTransferredToRoomJoules})
            if (!Double.isFinite(value)) throw new IllegalArgumentException("energy ledger must be finite");
        if (inputElectricalEnergyJoules < 0.0 || removedHeatJoules < 0.0
                || rackCoolingElectricalEnergyJoules < 0.0 || rackRemovedHeatJoules < 0.0
                || rackHeatRejectedToRoomJoules < 0.0 || rackHeatRejectedExternalJoules < 0.0)
            throw new IllegalArgumentException("electrical inputs, removed heat and rack rejection must be >= 0");
    }

    /** Preserves the stage 4 ledger constructor without detailed electrical captures. */
    public CoupledRoomThermalSnapshot(RoomAirTemperatureSnapshot roomAir,
            double inputElectricalEnergyJoules, double serverEnergyChangeJoules,
            double roomAirEnergyChangeJoules, double heatTransferredToAirJoules,
            double removedHeatJoules, double environmentHeatLossJoules,
            double rackAirEnergyChangeJoules, double rackCoolingElectricalEnergyJoules,
            double rackRemovedHeatJoules, double rackHeatRejectedToRoomJoules,
            double rackHeatRejectedExternalJoules, double rackHeatTransferredToRoomJoules) {
        this(roomAir, inputElectricalEnergyJoules, serverEnergyChangeJoules, roomAirEnergyChangeJoules,
                heatTransferredToAirJoules, removedHeatJoules, environmentHeatLossJoules,
                rackAirEnergyChangeJoules, rackCoolingElectricalEnergyJoules, rackRemovedHeatJoules,
                rackHeatRejectedToRoomJoules, rackHeatRejectedExternalJoules, rackHeatTransferredToRoomJoules,
                Optional.empty(), Optional.empty());
    }

    /** Compatibility constructor for scenarios without local rack air. */
    public CoupledRoomThermalSnapshot(RoomAirTemperatureSnapshot roomAir,
            double inputElectricalEnergyJoules, double serverEnergyChangeJoules,
            double roomAirEnergyChangeJoules, double heatTransferredToAirJoules,
            double removedHeatJoules, double environmentHeatLossJoules) {
        this(roomAir, inputElectricalEnergyJoules, serverEnergyChangeJoules,
                roomAirEnergyChangeJoules, heatTransferredToAirJoules,
                removedHeatJoules, environmentHeatLossJoules, 0, 0, 0, 0, 0, 0);
    }

    /** Zero within numerical tolerance when the modeled boundary conserves energy. */
    public double energyBalanceResidualJoules() {
        return inputElectricalEnergyJoules + rackCoolingElectricalEnergyJoules
                - removedHeatJoules - rackHeatRejectedExternalJoules - environmentHeatLossJoules
                - serverEnergyChangeJoules - roomAirEnergyChangeJoules - rackAirEnergyChangeJoules;
    }
}

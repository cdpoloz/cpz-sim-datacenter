package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.cooling.CoolingUnitType;
import com.cpz.sim.datacenter.cooling.HeatRejectionDestination;
import com.cpz.sim.datacenter.model.RackLocation;

import java.util.Objects;

/** Completed-tick local rack air, actual final heat rates and integrated transfers.
 * Equipment nominal capacity is separate from actualCoolingPowerWatts.
 * Signed heat transferred to room is positive out of the rack.
 * @author CPZ
 */
public record RackCoolingUnitThermalSnapshot(
        CoolingUnitSnapshot equipment,
        RackLocation rackLocation,
        HeatRejectionDestination heatRejectionDestination,
        double airTemperatureCelsius,
        double actualCoolingPowerWatts,
        double heatTransferredToRoomWatts,
        double electricalEnergyJoules,
        double removedHeatJoules,
        double rejectedHeatJoules,
        double airEnergyChangeJoules,
        double serverHeatTransferredToAirJoules,
        double heatTransferredToRoomJoules
) {
    public RackCoolingUnitThermalSnapshot {
        Objects.requireNonNull(equipment, "equipment must not be null");
        Objects.requireNonNull(rackLocation, "rackLocation must not be null");
        Objects.requireNonNull(heatRejectionDestination, "heatRejectionDestination must not be null");
        if (equipment.type() != CoolingUnitType.RACK) throw new IllegalArgumentException("rack snapshot requires RACK equipment");
        for (double value : new double[]{airTemperatureCelsius, actualCoolingPowerWatts, heatTransferredToRoomWatts,
                electricalEnergyJoules, removedHeatJoules, rejectedHeatJoules, airEnergyChangeJoules,
                serverHeatTransferredToAirJoules, heatTransferredToRoomJoules})
            if (!Double.isFinite(value)) throw new IllegalArgumentException("rack snapshot values must be finite");
        if (actualCoolingPowerWatts < 0 || electricalEnergyJoules < 0 || removedHeatJoules < 0 || rejectedHeatJoules < 0)
            throw new IllegalArgumentException("rack cooling and electrical inputs must be non-negative");
        if (actualCoolingPowerWatts > equipment.currentCoolingPowerWatts() + 1e-9)
            throw new IllegalArgumentException("actual rack cooling exceeds enabled nominal capacity");
        if (!equipment.enabled() && (actualCoolingPowerWatts != 0 || electricalEnergyJoules != 0
                || removedHeatJoules != 0 || rejectedHeatJoules != 0))
            throw new IllegalArgumentException("disabled rack unit cannot remove or reject heat or consume electricity");
        if (Math.abs(rejectedHeatJoules - removedHeatJoules - electricalEnergyJoules)
                > 1e-6 + 1e-9 * rejectedHeatJoules)
            throw new IllegalArgumentException("rack rejected heat must equal removed heat plus electricity");
        double residual = serverHeatTransferredToAirJoules - removedHeatJoules
                - heatTransferredToRoomJoules - airEnergyChangeJoules;
        double scale = Math.abs(serverHeatTransferredToAirJoules) + removedHeatJoules
                + Math.abs(heatTransferredToRoomJoules) + Math.abs(airEnergyChangeJoules);
        if (!Double.isFinite(scale) || Math.abs(residual) > 1e-6 + 1e-9 * scale)
            throw new IllegalArgumentException("local rack energy balance exceeds numerical tolerance");
        if (!Double.isFinite(actualCoolingPowerWatts + equipment.currentElectricalPowerWatts()))
            throw new IllegalArgumentException("rack heat rejection power must be finite");
    }

    public String unitCode() { return equipment.unitCode(); }
    public double currentElectricalPowerWatts() { return equipment.currentElectricalPowerWatts(); }
    public double heatRejectionPowerWatts() { return actualCoolingPowerWatts + currentElectricalPowerWatts(); }
    public double localEnergyBalanceResidualJoules() {
        return serverHeatTransferredToAirJoules - removedHeatJoules - heatTransferredToRoomJoules - airEnergyChangeJoules;
    }
}

package com.cpz.sim.datacenter.snapshot;

/** Electrical energy since the coupled thermal owner's reset, in Wh, plus
 * completed-tick electrical power. Heat removal is not electrical consumption.
 * Scope is IT plus modeled room and rack cooling, excluding unconfigured loads.
 * @author CPZ
 */
public record FacilityEnergySnapshot(
        long tickIndex, double elapsedSeconds,
        double consumedItEnergyWh, double consumedRoomCoolingEnergyWh, double consumedRackCoolingEnergyWh,
        double currentItPowerWatts, double roomCoolingPowerWatts, double rackCoolingPowerWatts
) {
    public FacilityEnergySnapshot {
        if (tickIndex < 0 || !Double.isFinite(elapsedSeconds) || elapsedSeconds < 0)
            throw new IllegalArgumentException("facility energy capture needs a valid tick and elapsed time");
        for (double value : new double[]{consumedItEnergyWh, consumedRoomCoolingEnergyWh, consumedRackCoolingEnergyWh,
                currentItPowerWatts, roomCoolingPowerWatts, rackCoolingPowerWatts,
                consumedItEnergyWh + consumedRoomCoolingEnergyWh + consumedRackCoolingEnergyWh,
                currentItPowerWatts + roomCoolingPowerWatts + rackCoolingPowerWatts})
            if (!Double.isFinite(value) || value < 0)
                throw new IllegalArgumentException("facility electricity must be finite and non-negative");
    }
    public double consumedCoolingEnergyWh() { return consumedRoomCoolingEnergyWh + consumedRackCoolingEnergyWh; }
    public double consumedFacilityEnergyWh() { return consumedItEnergyWh + consumedCoolingEnergyWh(); }
    public double consumedFacilityEnergyKWh() { return consumedFacilityEnergyWh() / 1000; }
    public double coolingPowerWatts() { return roomCoolingPowerWatts + rackCoolingPowerWatts; }
    public double totalFacilityPowerWatts() { return currentItPowerWatts + coolingPowerWatts(); }
    public double pue() { return currentItPowerWatts == 0 ? Double.NaN : totalFacilityPowerWatts() / currentItPowerWatts; }
    public double energyPue() { return consumedItEnergyWh == 0 ? Double.NaN : consumedFacilityEnergyWh() / consumedItEnergyWh; }
}

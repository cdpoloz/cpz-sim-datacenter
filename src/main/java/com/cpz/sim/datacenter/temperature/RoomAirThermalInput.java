package com.cpz.sim.datacenter.temperature;

/**
 * Thermal power held constant during one room-air step, in watts.
 *
 * @param serverHeatTransferWatts signed heat actually transferred from servers
 *        into room air; negative means air transfers heat to the servers
 * @param removedHeatWatts non-negative heat actually removed from the room by
 *        cooling or ventilation; neither electrical power nor rated capacity
 */
public record RoomAirThermalInput(
        double serverHeatTransferWatts,
        double removedHeatWatts
) {

    public RoomAirThermalInput {
        if (!Double.isFinite(serverHeatTransferWatts))
            throw new IllegalArgumentException("serverHeatTransferWatts must be finite");
        if (!Double.isFinite(removedHeatWatts) || removedHeatWatts < 0.0)
            throw new IllegalArgumentException("removedHeatWatts must be finite and >= 0");
        if (!Double.isFinite(serverHeatTransferWatts - removedHeatWatts))
            throw new IllegalArgumentException("net thermal power must be finite");
    }

    public double netHeatWatts() {
        return serverHeatTransferWatts - removedHeatWatts;
    }
}

package com.cpz.sim.datacenter.temperature;

/**
 * One cold-air path into the well-mixed room, limited by both airflow and
 * refrigeration capacity. EXHAUST is represented through effective airflow,
 * never as a second heat sink. Hotter supply air does not provide heating in
 * this cooling-only approximation.
 */
public record RoomAirCoolingPath(
        double heatTransferWattsPerCelsius,
        double supplyTemperatureCelsius,
        double capacityWatts
) {
    public RoomAirCoolingPath {
        if (!Double.isFinite(heatTransferWattsPerCelsius) || heatTransferWattsPerCelsius < 0.0)
            throw new IllegalArgumentException("heatTransferWattsPerCelsius must be finite and >= 0");
        if (!Double.isFinite(supplyTemperatureCelsius))
            throw new IllegalArgumentException("supplyTemperatureCelsius must be finite");
        if (!Double.isFinite(capacityWatts) || capacityWatts < 0.0)
            throw new IllegalArgumentException("capacityWatts must be finite and >= 0");
    }

    public double removedHeatWatts(double roomTemperatureCelsius) {
        if (!Double.isFinite(roomTemperatureCelsius))
            throw new IllegalArgumentException("roomTemperatureCelsius must be finite");
        if (heatTransferWattsPerCelsius == 0.0 || roomTemperatureCelsius <= supplyTemperatureCelsius)
            return 0.0;
        return Math.min(capacityWatts,
                heatTransferWattsPerCelsius * (roomTemperatureCelsius - supplyTemperatureCelsius));
    }
}

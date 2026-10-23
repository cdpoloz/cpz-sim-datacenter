package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.RackLocation;

import java.util.List;
import java.util.Objects;

/**
 * Immutable dedicated rack equipment and local-air parameters. The local air
 * state and actual heat removal are owned by the future coupled thermal solver.
 * A rack unit has no legacy room-zone influences.
 *
 * @author CPZ
 */
public record RackCoolingUnitDefinition(
        String code,
        RackLocation rackLocation,
        double initialAirTemperatureCelsius,
        double effectiveAirThermalCapacityJoulesPerCelsius,
        double roomHeatTransferWattsPerCelsius,
        double ratedAirflowCubicMetersPerSecond,
        double ratedCoolingCapacityWatts,
        double supplyAirTemperatureCelsius,
        double ratedElectricalPowerWatts,
        HeatRejectionDestination heatRejectionDestination,
        boolean initiallyEnabled
) implements CoolingUnitDefinition {

    public RackCoolingUnitDefinition {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(rackLocation, "rackLocation must not be null");
        Objects.requireNonNull(heatRejectionDestination, "heatRejectionDestination must not be null");
        if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        requireFinite(initialAirTemperatureCelsius, "initialAirTemperatureCelsius");
        requireFinite(supplyAirTemperatureCelsius, "supplyAirTemperatureCelsius");
        requirePositive(effectiveAirThermalCapacityJoulesPerCelsius, "effectiveAirThermalCapacityJoulesPerCelsius");
        requirePositive(ratedAirflowCubicMetersPerSecond, "ratedAirflowCubicMetersPerSecond");
        requirePositive(ratedCoolingCapacityWatts, "ratedCoolingCapacityWatts");
        requirePositive(ratedElectricalPowerWatts, "ratedElectricalPowerWatts");
        requireFinite(roomHeatTransferWattsPerCelsius, "roomHeatTransferWattsPerCelsius");
        if (roomHeatTransferWattsPerCelsius < 0.0)
            throw new IllegalArgumentException("roomHeatTransferWattsPerCelsius must be >= 0");
    }

    @Override
    public CoolingUnitType type() {
        return CoolingUnitType.RACK;
    }

    @Override
    public List<CoolingZoneInfluence> influences() {
        return List.of();
    }

    private static void requirePositive(double value, String name) {
        requireFinite(value, name);
        if (value <= 0.0) throw new IllegalArgumentException(name + " must be > 0");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}

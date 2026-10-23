package com.cpz.sim.datacenter.config.definition;

import com.cpz.sim.datacenter.cooling.HeatRejectionDestination;

/** Configuration of one dedicated rack unit and its local air node.
 * Physical magnitudes are validated by DatacenterConfigValidator.
 * Runtime thermal behavior is introduced in a later stage.
 */
public record RackCoolingUnitConfigDefinition(
        String code,
        String column,
        String rackCode,
        double initialAirTemperatureCelsius,
        double effectiveAirThermalCapacityJoulesPerCelsius,
        double roomHeatTransferWattsPerCelsius,
        double ratedAirflowCubicMetersPerSecond,
        double ratedCoolingCapacityWatts,
        double supplyAirTemperatureCelsius,
        double ratedElectricalPowerWatts,
        HeatRejectionDestination heatRejectionDestination,
        boolean initiallyEnabled
) {
}

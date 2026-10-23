package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.cooling.RackCoolingConfiguration;
import com.cpz.sim.datacenter.cooling.RackCoolingUnitDefinition;
import com.cpz.sim.datacenter.model.RackLocation;

import java.util.Objects;
import java.util.Optional;

/** Builds rack equipment from a validated layout; does not start a simulation.
 * @author CPZ
 */
public final class RackCoolingConfigurationFactory {
    public Optional<RackCoolingConfiguration> create(DatacenterDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        new DatacenterConfigValidator().validate(definition);
        var config = definition.rackCooling();
        if (config == null) return Optional.empty();
        var units = config.units().stream().map(unit -> new RackCoolingUnitDefinition(
                unit.code(), new RackLocation(unit.column(), unit.rackCode()),
                unit.initialAirTemperatureCelsius(), unit.effectiveAirThermalCapacityJoulesPerCelsius(),
                unit.roomHeatTransferWattsPerCelsius(), unit.ratedAirflowCubicMetersPerSecond(),
                unit.ratedCoolingCapacityWatts(), unit.supplyAirTemperatureCelsius(),
                unit.ratedElectricalPowerWatts(), unit.heatRejectionDestination(), unit.initiallyEnabled()
        )).toList();
        return Optional.of(new RackCoolingConfiguration(config.airDensityKilogramsPerCubicMeter(),
                config.airSpecificHeatJoulesPerKilogramKelvin(), units));
    }
}

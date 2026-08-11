package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.CoolingConfigDefinition;
import com.cpz.sim.datacenter.config.definition.CoolingSystemOptionsDefinition;
import com.cpz.sim.datacenter.config.definition.CoolingZoneConfigDefinition;
import com.cpz.sim.datacenter.config.definition.CoolingZoneInfluenceConfigDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.ExhaustCoolingUnitConfigDefinition;
import com.cpz.sim.datacenter.config.definition.SupplyCoolingUnitConfigDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSystemOptions;
import com.cpz.sim.datacenter.cooling.CoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.ExhaustCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Creates a runtime cooling configuration from a validated datacenter
 * definition and its constructed datacenter model.
 *
 * @author CPZ
 */
public final class CoolingConfigurationFactory {

    private final DatacenterConfigValidator validator;

    /**
     * Creates a factory using the standard datacenter configuration
     * validator.
     */
    public CoolingConfigurationFactory() {
        this(new DatacenterConfigValidator());
    }

    /**
     * Creates a factory using the supplied validator.
     *
     * @param validator configuration validator
     *
     * @throws NullPointerException if {@code validator} is {@code null}
     */
    public CoolingConfigurationFactory(DatacenterConfigValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
    }

    /**
     * Creates the runtime cooling configuration declared by the datacenter
     * definition.
     *
     * @param definition complete datacenter definition
     * @param datacenter constructed datacenter whose installed servers are
     *                   assigned to cooling zones
     * @return the cooling configuration, or an empty optional when the
     *         datacenter definition does not declare cooling
     *
     * @throws NullPointerException if {@code definition} or
     *         {@code datacenter} is {@code null}
     */
    public Optional<CoolingConfiguration> create(DatacenterDefinition definition, Datacenter datacenter) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(datacenter, "datacenter cannot be null");
        validator.validate(definition);
        CoolingConfigDefinition cooling = definition.cooling();
        if (cooling == null) return Optional.empty();
        List<CoolingZoneDefinition> zones = createZones(cooling.zones(), datacenter);
        List<CoolingUnitDefinition> units = createUnits(cooling);
        CoolingSystemOptions options = createOptions(cooling.options());
        return Optional.of(new CoolingConfiguration(zones, units, options));
    }

    private static List<CoolingZoneDefinition> createZones(List<CoolingZoneConfigDefinition> definitions, Datacenter datacenter) {
        List<CoolingZoneDefinition> zones = new ArrayList<>();
        for (CoolingZoneConfigDefinition definition : definitions) zones.add(createZone(definition, datacenter));
        return List.copyOf(zones);
    }

    private static CoolingZoneDefinition createZone(CoolingZoneConfigDefinition definition, Datacenter datacenter) {
        Set<ServerLocation> serverLocations = new HashSet<>();
        for (Server server : datacenter.getServers()) {
            ServerLocation location = server.getLocation();
            if (belongsToZone(location, definition)) serverLocations.add(location);
        }
        return new CoolingZoneDefinition(definition.code(), serverLocations);
    }

    private static boolean belongsToZone(ServerLocation location, CoolingZoneConfigDefinition zone) {
        return zone.columns().contains(location.column()) && zone.rackCodes().contains(location.rackCode().value());
    }

    private static List<CoolingUnitDefinition> createUnits(CoolingConfigDefinition cooling) {
        List<CoolingUnitDefinition> units = new ArrayList<>();
        for (SupplyCoolingUnitConfigDefinition definition : cooling.supplyUnits()) units.add(createSupplyUnit(definition));
        for (ExhaustCoolingUnitConfigDefinition definition : cooling.exhaustUnits()) units.add(createExhaustUnit(definition));
        return List.copyOf(units);
    }

    private static SupplyCoolingUnitDefinition createSupplyUnit(SupplyCoolingUnitConfigDefinition definition) {
        return new SupplyCoolingUnitDefinition(
                definition.code(),
                definition.ratedAirflowCubicMetersPerSecond(),
                definition.ratedCoolingCapacityWatts(),
                definition.supplyAirTemperatureCelsius(),
                createInfluences(definition.influences()),
                definition.initiallyEnabled()
        );
    }

    private static ExhaustCoolingUnitDefinition createExhaustUnit(ExhaustCoolingUnitConfigDefinition definition) {
        return new ExhaustCoolingUnitDefinition(
                definition.code(),
                definition.ratedAirflowCubicMetersPerSecond(),
                createInfluences(definition.influences()),
                definition.initiallyEnabled()
        );
    }

    private static List<CoolingZoneInfluence> createInfluences(List<CoolingZoneInfluenceConfigDefinition> definitions) {
        List<CoolingZoneInfluence> influences = new ArrayList<>();
        for (CoolingZoneInfluenceConfigDefinition definition : definitions)
            influences.add(new CoolingZoneInfluence(definition.zoneCode(), definition.weight()));
        return List.copyOf(influences);
    }

    private static CoolingSystemOptions createOptions(CoolingSystemOptionsDefinition definition) {
        return new CoolingSystemOptions(
                definition.airDensityKilogramsPerCubicMeter(),
                definition.airSpecificHeatJoulesPerKilogramKelvin(),
                definition.initialInletAirTemperatureCelsius(),
                definition.maximumRecirculationFraction()
        );
    }
}
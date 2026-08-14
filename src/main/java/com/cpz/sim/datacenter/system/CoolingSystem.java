package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingTickInput;
import com.cpz.sim.datacenter.cooling.CoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.CoolingUnitState;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.ExhaustCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.ServerHeatLoad;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingUnitSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Manages the operational state and simulation of the configured cooling
 * units and zones.
 *
 * <p>The system owns the current enabled state of every cooling unit.
 * Definitions remain immutable and describe only permanent characteristics.</p>
 *
 * <p>Commands such as {@link #setEnabled(String, boolean)} affect subsequent
 * simulation ticks. Snapshots already produced by the system remain
 * immutable.</p>
 *
 * @author CPZ
 */
public final class CoolingSystem {

    private final CoolingConfiguration configuration;
    private final Map<String, CoolingUnitDefinition> definitionsByUnitCode;
    private final Map<String, CoolingUnitState> statesByUnitCode;
    private final Map<ServerLocation, String> zoneCodeByServerLocation;
    private final Map<String, Double> zoneAirTemperatureByZoneCode;

    /**
     * Creates a cooling system from the given configuration.
     *
     * <p>Each operational state is initialized from
     * {@link CoolingUnitDefinition#initiallyEnabled()}.</p>
     *
     * @param configuration complete cooling-system configuration
     *
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public CoolingSystem(CoolingConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.definitionsByUnitCode = new LinkedHashMap<>();
        this.statesByUnitCode = new LinkedHashMap<>();
        this.zoneCodeByServerLocation = new HashMap<>();
        this.zoneAirTemperatureByZoneCode = new LinkedHashMap<>();
        for (CoolingUnitDefinition definition : configuration.units()) {
            definitionsByUnitCode.put(definition.code(), definition);
            statesByUnitCode.put(definition.code(), new CoolingUnitState(definition.code(), definition.initiallyEnabled()));
        }
        for (CoolingZoneDefinition zone : configuration.zones()) {
            zoneAirTemperatureByZoneCode.put(zone.code(), configuration.options().initialInletAirTemperatureCelsius());
            for (ServerLocation location : zone.serverLocations()) zoneCodeByServerLocation.put(location, zone.code());
        }
    }

    /**
     * Processes one cooling-system tick.
     *
     * <p>The method aggregates server heat by zone and calculates the airflow,
     * cooling capacity, cooling deficit and air temperatures produced by the
     * cooling units that are enabled for this tick.</p>
     *
     * @param input thermal load processed during the tick
     * @return immutable cooling snapshot
     *
     * @throws NullPointerException if {@code input} is {@code null}
     * @throws IllegalArgumentException if a heat load references a server
     *         location not assigned to any configured cooling zone, or if the
     *         same server location occurs more than once
     */
    public CoolingSnapshot tick(CoolingTickInput input) {
        Objects.requireNonNull(input, "input must not be null");
        Map<String, Double> generatedHeatByZone = aggregateGeneratedHeatByZone(input.serverHeatLoads());
        List<CoolingUnitSnapshot> unitSnapshots = createUnitSnapshots();
        //List<CoolingZoneSnapshot> zoneSnapshots = createZoneSnapshots(generatedHeatByZone);
        List<CoolingZoneSnapshot> zoneSnapshots = createZoneSnapshots(generatedHeatByZone, input.deltaSeconds());
        return new CoolingSnapshot(input.tickIndex(), unitSnapshots, zoneSnapshots);
    }

    /**
     * Returns the immutable configuration used by this system.
     *
     * @return cooling-system configuration
     */
    public CoolingConfiguration configuration() {
        return configuration;
    }

    /**
     * Returns whether the requested cooling unit is enabled.
     *
     * @param unitCode cooling-unit code
     * @return {@code true} if the unit is enabled
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     * @throws IllegalArgumentException if no unit has the requested code
     */
    public boolean isEnabled(String unitCode) {
        return stateOf(unitCode).enabled();
    }

    /**
     * Sets the enabled state of a cooling unit.
     *
     * <p>If the unit already has the requested state, this method has no
     * observable effect.</p>
     *
     * @param unitCode cooling-unit code
     * @param enabled requested enabled state
     *
     * @throws NullPointerException if {@code unitCode} is {@code null}
     * @throws IllegalArgumentException if no unit has the requested code
     */
    public void setEnabled(String unitCode, boolean enabled) {
        CoolingUnitState currentState = stateOf(unitCode);
        statesByUnitCode.put(unitCode, currentState.withEnabled(enabled));
    }

    /**
     * Enables a cooling unit.
     *
     * @param unitCode cooling-unit code
     */
    public void enable(String unitCode) {
        setEnabled(unitCode, true);
    }

    /**
     * Disables a cooling unit.
     *
     * @param unitCode cooling-unit code
     */
    public void disable(String unitCode) {
        setEnabled(unitCode, false);
    }

    /**
     * Inverts the enabled state of a cooling unit.
     *
     * @param unitCode cooling-unit code
     * @return the new enabled state
     */
    public boolean toggle(String unitCode) {
        CoolingUnitState newState = stateOf(unitCode).toggled();
        statesByUnitCode.put(unitCode, newState);
        return newState.enabled();
    }

    /**
     * Restores every cooling unit to the enabled state declared by its
     * immutable definition.
     *
     * <p>Any operational changes made through enable, disable, setEnabled or
     * toggle are discarded.</p>
     */
    public void reset() {
        for (CoolingUnitDefinition definition : configuration.units())
            statesByUnitCode.put(definition.code(), new CoolingUnitState(definition.code(), definition.initiallyEnabled()));
        for (CoolingZoneDefinition zone : configuration.zones())
            zoneAirTemperatureByZoneCode.put(zone.code(), configuration.options().initialInletAirTemperatureCelsius());
    }

    /**
     * Returns the current immutable state of a cooling unit.
     *
     * @param unitCode cooling-unit code
     * @return current cooling-unit state
     */
    public CoolingUnitState stateOf(String unitCode) {
        Objects.requireNonNull(unitCode, "unitCode must not be null");
        CoolingUnitState state = statesByUnitCode.get(unitCode);
        if (state == null) throw new IllegalArgumentException("unknown cooling-unit code: " + unitCode);
        return state;
    }

    /**
     * Returns the immutable definition of a cooling unit.
     *
     * @param unitCode cooling-unit code
     * @return cooling-unit definition
     */
    public CoolingUnitDefinition definitionOf(String unitCode) {
        Objects.requireNonNull(unitCode, "unitCode must not be null");
        CoolingUnitDefinition definition = definitionsByUnitCode.get(unitCode);
        if (definition == null) throw new IllegalArgumentException("unknown cooling-unit code: " + unitCode);
        return definition;
    }

    private Map<String, Double> aggregateGeneratedHeatByZone(List<ServerHeatLoad> serverHeatLoads) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (CoolingZoneDefinition zone : configuration.zones()) result.put(zone.code(), 0.0);
        Set<ServerLocation> encounteredLocations = new HashSet<>();
        for (ServerHeatLoad heatLoad : serverHeatLoads) {
            ServerLocation location = heatLoad.serverLocation();
            if (!encounteredLocations.add(location))
                throw new IllegalArgumentException("cooling input must not contain duplicate server locations: " + location);
            String zoneCode = zoneCodeByServerLocation.get(location);
            if (zoneCode == null)
                throw new IllegalArgumentException("server location is not assigned to a cooling zone: " + location);
            result.merge(zoneCode, heatLoad.generatedHeatWatts(), Double::sum);
        }
        return result;
    }

    private List<CoolingUnitSnapshot> createUnitSnapshots() {
        List<CoolingUnitSnapshot> snapshots = new ArrayList<>();
        for (CoolingUnitDefinition definition : configuration.units()) {
            boolean enabled = stateOf(definition.code()).enabled();
            double currentAirflow = enabled ? definition.ratedAirflowCubicMetersPerSecond() : 0.0;
            double currentCoolingPower = 0.0;
            if (enabled && definition instanceof SupplyCoolingUnitDefinition supply)
                currentCoolingPower = supply.ratedCoolingCapacityWatts();
            snapshots.add(new CoolingUnitSnapshot(definition.code(), definition.type(), enabled, currentAirflow, currentCoolingPower));
        }
        return snapshots;
    }

    private List<CoolingZoneSnapshot> createZoneSnapshots(Map<String, Double> generatedHeatByZone, double deltaSeconds) {
        List<CoolingZoneSnapshot> snapshots = new ArrayList<>();
        for (CoolingZoneDefinition zone : configuration.zones())
            snapshots.add(createZoneSnapshot(zone, generatedHeatByZone.get(zone.code()), deltaSeconds));
        return snapshots;
    }

    private CoolingZoneSnapshot createZoneSnapshot(CoolingZoneDefinition zone, double generatedHeatWatts, double deltaSeconds) {
        ZoneCoolingResources resources = calculateZoneCoolingResources(zone.code());
        double usedCoolingCapacityWatts = Math.min(generatedHeatWatts, resources.availableCoolingCapacityWatts());
        double coolingDeficitWatts = Math.max(0.0, generatedHeatWatts - resources.availableCoolingCapacityWatts());
        double recirculationFraction = calculateRecirculationFraction(
                resources.supplyAirflowCubicMetersPerSecond(),
                resources.exhaustAirflowCubicMetersPerSecond()
        );
        double previousZoneAirTemperatureCelsius =
                zoneAirTemperatureByZoneCode.getOrDefault(zone.code(), configuration.options().initialInletAirTemperatureCelsius());
        ZoneTemperatures temperatures =
                calculateZoneTemperatures(
                        coolingDeficitWatts,
                        resources,
                        previousZoneAirTemperatureCelsius,
                        resources.weightedSupplyTemperatureCelsius(),
                        recirculationFraction,
                        deltaSeconds
                );
        zoneAirTemperatureByZoneCode.put(
                zone.code(),
                temperatures.exhaustAirTemperatureCelsius()
        );
        return new CoolingZoneSnapshot(
                zone.code(),
                generatedHeatWatts,
                resources.availableCoolingCapacityWatts(),
                usedCoolingCapacityWatts,
                coolingDeficitWatts,
                resources.supplyAirflowCubicMetersPerSecond(),
                resources.exhaustAirflowCubicMetersPerSecond(),
                temperatures.inletAirTemperatureCelsius(),
                temperatures.exhaustAirTemperatureCelsius(),
                recirculationFraction
        );
    }

    private ZoneCoolingResources calculateZoneCoolingResources(String zoneCode) {
        double supplyAirflow = 0.0;
        double exhaustAirflow = 0.0;
        double availableCoolingCapacity = 0.0;
        double supplyTemperatureAirflowProduct = 0.0;
        for (CoolingUnitDefinition definition : configuration.units()) {
            if (!stateOf(definition.code()).enabled()) continue;
            double influenceWeight = influenceWeightFor(definition, zoneCode);
            if (influenceWeight == 0.0) continue;
            double influencedAirflow = definition.ratedAirflowCubicMetersPerSecond() * influenceWeight;
            if (definition instanceof SupplyCoolingUnitDefinition supply) {
                supplyAirflow += influencedAirflow;
                availableCoolingCapacity += supply.ratedCoolingCapacityWatts() * influenceWeight;
                supplyTemperatureAirflowProduct += supply.supplyAirTemperatureCelsius() * influencedAirflow;
            } else if (definition instanceof ExhaustCoolingUnitDefinition) {
                exhaustAirflow += influencedAirflow;
            }
        }
        double weightedSupplyTemperature
                = supplyAirflow == 0.0
                ? configuration.options().initialInletAirTemperatureCelsius()
                : supplyTemperatureAirflowProduct / supplyAirflow;
        return new ZoneCoolingResources(supplyAirflow, exhaustAirflow, availableCoolingCapacity, weightedSupplyTemperature);
    }

    private double influenceWeightFor(CoolingUnitDefinition definition, String zoneCode) {
        return definition.influences().stream()
                .filter(influence -> influence.zoneCode().equals(zoneCode))
                .mapToDouble(CoolingZoneInfluence::weight)
                .findFirst()
                .orElse(0.0);
    }

    private double calculateRecirculationFraction(double supplyAirflow, double exhaustAirflow) {
        if (supplyAirflow == 0.0) return configuration.options().maximumRecirculationFraction();
        double airflowImbalanceFraction = Math.max(0.0, (supplyAirflow - exhaustAirflow) / supplyAirflow);
        return Math.min(airflowImbalanceFraction, configuration.options().maximumRecirculationFraction());
    }

    private ZoneTemperatures calculateZoneTemperatures(
            double coolingDeficitWatts,
            ZoneCoolingResources resources,
            double previousZoneAirTemperatureCelsius,
            double supplyAirTemperatureCelsius,
            double recirculationFraction,
            double deltaSeconds
    ) {
        double supplyAirflow = resources.supplyAirflowCubicMetersPerSecond();
        double exhaustAirflow = resources.exhaustAirflowCubicMetersPerSecond();
        double inletAirTemperature =
                calculateInletAirTemperature(
                        supplyAirflow,
                        previousZoneAirTemperatureCelsius,
                        supplyAirTemperatureCelsius,
                        recirculationFraction
                );
        double heatRemovalAirflow = Math.max(supplyAirflow, exhaustAirflow);
        if (heatRemovalAirflow == 0.0) return calculateStagnantZoneTemperatures(coolingDeficitWatts, previousZoneAirTemperatureCelsius, deltaSeconds);
        double airTemperatureRise = coolingDeficitWatts / (configuration.options().airVolumetricHeatCapacityJoulesPerCubicMeterKelvin() * heatRemovalAirflow);
        double exhaustAirTemperature = inletAirTemperature + airTemperatureRise;
        return new ZoneTemperatures(inletAirTemperature,exhaustAirTemperature);
    }

    private double calculateInletAirTemperature(
            double supplyAirflow,
            double previousZoneAirTemperatureCelsius,
            double supplyAirTemperatureCelsius,
            double recirculationFraction
    ) {
        if (supplyAirflow == 0.0) return previousZoneAirTemperatureCelsius;
        return (supplyAirTemperatureCelsius * (1.0 - recirculationFraction)) + (previousZoneAirTemperatureCelsius * recirculationFraction);
    }

    private ZoneTemperatures calculateStagnantZoneTemperatures(
            double coolingDeficitWatts,
            double previousZoneAirTemperatureCelsius,
            double deltaSeconds
    ) {
        double temperatureRise = coolingDeficitWatts * deltaSeconds / effectiveZoneAirThermalCapacityJoulesPerCelsius();
        double nextZoneAirTemperature = previousZoneAirTemperatureCelsius + temperatureRise;
        return new ZoneTemperatures(previousZoneAirTemperatureCelsius, nextZoneAirTemperature);
    }

    private double effectiveZoneAirThermalCapacityJoulesPerCelsius() {
        return configuration.options().airVolumetricHeatCapacityJoulesPerCubicMeterKelvin() * configuration.options().effectiveZoneAirVolumeCubicMeters();
    }

    private record ZoneCoolingResources(
            double supplyAirflowCubicMetersPerSecond,
            double exhaustAirflowCubicMetersPerSecond,
            double availableCoolingCapacityWatts,
            double weightedSupplyTemperatureCelsius
    ) {
    }

    private record ZoneTemperatures(
            double inletAirTemperatureCelsius,
            double exhaustAirTemperatureCelsius
    ) {
    }
}
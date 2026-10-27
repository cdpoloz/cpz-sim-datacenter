package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.cooling.HeatRejectionDestination;
import com.cpz.sim.datacenter.factory.*;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.datacenter.history.*;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.*;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Reproducible synthetic comparison, using only backend systems for physics and metering. */
public final class RackCoolingComparisonDemo {
    public enum Scenario {
        NO_UNIT, DISABLED, ROOM, EXTERNAL, ROOM_WITH_HVAC, EXTERNAL_WITH_HVAC;

        public boolean roomCoolingEnabled() {
            return this == ROOM_WITH_HVAC || this == EXTERNAL_WITH_HVAC;
        }
    }

    public record Result(Scenario scenario, List<DatacenterSimulationStepSnapshot> steps) {
        public Result { steps = List.copyOf(steps); }
        public DatacenterSimulationStepSnapshot last() { return steps.getLast(); }
        public double maximumEnergyResidualJoules() {
            return steps.stream().mapToDouble(step -> Math.abs(step.coupledThermalSnapshot()
                    .orElseThrow().energyBalanceResidualJoules())).max().orElseThrow();
        }
    }

    private RackCoolingComparisonDemo() { }

    /** Copies immutable settings; DISABLED retains the local air node, NO_UNIT removes it. */
    public static DatacenterDefinition definitionFor(DatacenterDefinition base, Scenario scenario) {
        Objects.requireNonNull(base, "base must not be null");
        Objects.requireNonNull(scenario, "scenario must not be null");
        var rack = Objects.requireNonNull(base.rackCooling(), "comparison requires a rackCooling template");
        var destination = scenario == Scenario.EXTERNAL || scenario == Scenario.EXTERNAL_WITH_HVAC
                ? HeatRejectionDestination.EXTERNAL : HeatRejectionDestination.ROOM;
        var units = rack.units().stream().map(unit -> new RackCoolingUnitConfigDefinition(
                unit.code(), unit.column(), unit.rackCode(), unit.initialAirTemperatureCelsius(),
                unit.effectiveAirThermalCapacityJoulesPerCelsius(), unit.roomHeatTransferWattsPerCelsius(),
                unit.ratedAirflowCubicMetersPerSecond(), unit.ratedCoolingCapacityWatts(),
                unit.supplyAirTemperatureCelsius(), unit.ratedElectricalPowerWatts(), destination,
                scenario != Scenario.DISABLED)).toList();
        return new DatacenterDefinition(base.name(), base.layout(), base.serverModels(), base.servers(),
                base.temperature(), base.health(), base.cooling(), base.dataInputMode(), base.powerInputGranularity(),
                base.temperatureInputGranularity(), base.roomAirTemperature(), scenario == Scenario.NO_UNIT ? null
                : new RackCoolingConfigDefinition(rack.airDensityKilogramsPerCubicMeter(),
                        rack.airSpecificHeatJoulesPerKilogramKelvin(), units));
    }

    /** Fixed 50% utilization, or equivalent measured server power; no workload noise/factors. */
    public static Result run(DatacenterDefinition base, Scenario scenario, int minutes) {
        if (minutes <= 0) throw new IllegalArgumentException("minutes must be positive");
        var definition = definitionFor(base, scenario);
        if (definition.dataInputMode() == DatacenterDataInputMode.TEMPERATURE_DRIVEN)
            throw new IllegalArgumentException("comparison requires utilization or power input");
        var dc = new DatacenterFactory().create(definition);
        var options = new TemperatureSystemOptionsFactory().create(definition);
        var temperatures = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel());
        var room = new CoolingConfigurationFactory().create(definition, dc).map(CoolingSystem::new);
        if (scenario.roomCoolingEnabled() && room.isEmpty())
            throw new IllegalArgumentException("HVAC scenario requires a room cooling template");
        room.ifPresent(system -> system.configuration().units().forEach(unit ->
                system.setEnabled(unit.code(), scenario.roomCoolingEnabled())));
        var coupled = new CoupledRoomTemperatureSystemFactory().create(definition, dc, temperatures, room).orElseThrow();
        var engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(1)));
        new DatacenterInputPipelineFactory().registerInputSystems(engine, definition, dc, temperatures, options,
                new ConstantWorkloadSource(0.5), (server, tick) ->
                        (server.getConfig().idlePowerWatts() + server.getConfig().maxPowerWatts()) / 2.0, coupled);
        var health = new ServerHealthSystem(dc, temperatures, ServerHealthOptions.defaults());
        var energy = new EnergyConsumptionSystem(dc);
        engine.register(health);
        engine.register(energy);
        var history = new DatacenterSimulationHistory();
        var recorder = new DatacenterSimulationHistoryRecorder(new EnergyConsumptionSnapshotProvider(dc, energy),
                new TemperatureSnapshotProvider(dc, temperatures, options), new HealthSnapshotProvider(dc, health, temperatures),
                new DatacenterOperationalSnapshotProvider(dc), coupled::lastCoolingSnapshot,
                history, Optional.empty(), coupled::lastSnapshot);
        for (int minute = 0; minute < minutes; minute++) recorder.record(engine.step());
        return new Result(scenario, history.snapshots());
    }

    /** Optional JSON path, optional minutes (default 60); emits CSV to standard output. */
    public static void main(String[] args) {
        var base = new JsonDatacenterConfigLoader().load(Path.of(args.length > 0 ? args[0]
                : "data/config/rack-cooling-comparison.json"));
        int minutes = args.length > 1 ? Integer.parseInt(args[1]) : 60;
        System.out.println("scenario,minutes,room_air_C,rack_air_C,rack_server_avg_C,comparison_server_avg_C,it_W,cooling_W,facility_Wh,pue,energy_pue,rack_removed_Wh,rejected_to_room_Wh,rejected_external_Wh,max_balance_residual_J");
        var template = base.rackCooling().units().getFirst();
        var localRack = new RackLocation(template.column(), template.rackCode());
        var other = base.layout().racks().stream().map(rack -> new RackLocation(rack.column(), rack.code()))
                .filter(rack -> !rack.equals(localRack)).findFirst().orElseThrow();
        for (var scenario : Scenario.values()) {
            var result = run(base, scenario, minutes);
            var step = result.last();
            var op = step.operationalSnapshot();
            var energy = step.facilityEnergySnapshot().orElseThrow();
            double localAir = step.rackCoolingSnapshot().flatMap(value -> value.findRack(localRack))
                    .map(RackCoolingUnitThermalSnapshot::airTemperatureCelsius).orElse(Double.NaN);
            double removed = 0, toRoom = 0, external = 0;
            for (var capture : result.steps()) {
                var thermal = capture.coupledThermalSnapshot().orElseThrow();
                removed += thermal.rackRemovedHeatJoules();
                toRoom += thermal.rackHeatRejectedToRoomJoules();
                external += thermal.rackHeatRejectedExternalJoules();
            }
            System.out.printf(Locale.ROOT, "%s,%d,%.6f,%.6f,%.6f,%.6f,%.3f,%.3f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.9f%n",
                    scenario, minutes, op.roomAirTemperatureCelsius(), localAir,
                    op.getRack(localRack).averageOnlineTemperatureCelsius(), op.getRack(other).averageOnlineTemperatureCelsius(),
                    op.currentItPowerWatts(), op.coolingPowerWatts(), energy.consumedFacilityEnergyWh(), op.pue(),
                    energy.energyPue(), removed / 3600, toRoom / 3600, external / 3600, result.maximumEnergyResidualJoules());
        }
    }
}

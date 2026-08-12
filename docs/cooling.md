# Cooling System

The cooling system adds a simplified airflow and cooling-zone model to
`cpz-sim-datacenter`. It converts the current electrical power of installed
servers into zone heat loads, applies the available `SUPPLY` and `EXHAUST`
resources, and exposes immutable cooling results to external consumers and to
`TemperatureSystem`.

The implementation remains a pure Java backend concern. A console demo, a
future UI, or another adapter may control cooling units, but the operational
state belongs to `CoolingSystem`.

## Current Scope

The current milestone includes:

- logical cooling zones associated with exact server locations
- `SUPPLY` units that provide airflow, cooling capacity, and supplied-air
  temperature
- `EXHAUST` units that provide extraction airflow
- weighted unit influence over one or more zones
- mutable enabled state owned by `CoolingSystem`
- persistent per-zone air temperature owned by `CoolingSystem`
- per-tick unit and zone snapshots
- cooling-capacity and deficit calculations
- simplified inlet-air, exhaust-air, and recirculation calculations
- integration with `TemperatureSystem` through a temperature-reference provider
- an interactive backend demo with keyboard commands

The principal classes are:

- `CoolingConfiguration`
- `CoolingZoneDefinition`
- `CoolingUnitDefinition`
- `SupplyCoolingUnitDefinition`
- `ExhaustCoolingUnitDefinition`
- `CoolingZoneInfluence`
- `CoolingSystemOptions`
- `CoolingSystem`
- `DatacenterCoolingTickInputProvider`
- `CoolingSnapshotCoordinator`
- `CoolingSnapshotTemperatureReferenceProvider`
- `CoolingSnapshot`
- `CoolingUnitSnapshot`
- `CoolingZoneSnapshot`

## JSON Definitions, Runtime Configuration, and Execution

Cooling support now has three explicit layers:

- JSON definition: `DatacenterDefinition.cooling()` contains an optional
  `CoolingConfigDefinition` loaded by `JsonDatacenterConfigLoader`
- runtime configuration: `CoolingConfigurationFactory.create(DatacenterDefinition, Datacenter)`
  validates the datacenter definition, maps installed servers to zones, and
  returns `Optional<CoolingConfiguration>`
- executable system: `CoolingSystem` owns mutable unit state and produces one
  immutable `CoolingSnapshot` per tick

The end-to-end flow is:

```text
JSON
-> JsonDatacenterConfigLoader
-> DatacenterDefinition
-> DatacenterFactory
-> CoolingConfigurationFactory
-> CoolingConfiguration
-> ServerHeatLoadProvider
-> CoolingSystem
-> CoolingSnapshot
```

If the top-level JSON omits `cooling`, `CoolingConfigurationFactory.create(...)`
returns `Optional.empty()`. This preserves compatibility for configurations that
do not simulate cooling.

## Cooling Zones

`CoolingZoneDefinition` represents a logical thermal area, such as a section of
a rack row or hot aisle. A zone has a unique code and an immutable set of exact
`ServerLocation` values.

A server location can belong to at most one cooling zone. In JSON, a zone is
declared with:

- `code`
- `columns`
- `rackCodes`

The runtime zone membership is built from the intersection of `columns` and
`rackCodes` over the installed servers present in the constructed
`Datacenter`. `rackCodes` uses the rack `code`, not the rack `row`.

Each valid JSON zone must contain at least one installed server before runtime
construction. During execution, every server heat load processed by
`CoolingSystem` must reference a location assigned to a configured zone.

The complete server location is `column + rackCode + slot`. This prevents
servers with equivalent rack or slot labels in different columns from being
merged into the same thermal input accidentally.

## Cooling Units

`CoolingUnitDefinition` is a sealed interface with two current implementations.

### `SUPPLY`

`SupplyCoolingUnitDefinition` declares:

- unique unit code
- rated airflow in cubic metres per second
- rated cooling capacity in watts
- supplied-air temperature in degrees Celsius
- zone influences
- initial enabled state

An enabled supply unit contributes airflow and nominal cooling capacity to each
influenced zone. It also contributes its supplied-air temperature to the
airflow-weighted temperature used by that zone.

### `EXHAUST`

`ExhaustCoolingUnitDefinition` declares:

- unique unit code
- rated extraction airflow in cubic metres per second
- zone influences
- initial enabled state

An exhaust unit removes air but does not provide refrigeration capacity or a
configured supply-air temperature. Consequently, its
`CoolingUnitSnapshot.currentCoolingPowerWatts()` is always `0.0`.

### Zone Influence

`CoolingZoneInfluence` assigns a fraction of a unit's nominal resources to a
zone. Its `weight` is in the range `(0.0, 1.0]`:

- `1.0` means full influence
- `0.5` assigns half of the unit's airflow and, for a supply unit, half of its
  cooling capacity

Influence weights are applied independently per zone. For each unit, the
configured influence weights must sum to `1.0`.

## Configuration and Validation

`CoolingConfiguration` combines zones, units, and `CoolingSystemOptions` into
one immutable configuration. It validates that:

- zone and unit collections are not empty
- zone codes are unique
- unit codes are unique
- a server location does not belong to more than one zone
- every unit influence references a known zone
- a unit does not repeat the same zone in its influence list

The JSON contract is represented by:

- `CoolingConfigDefinition`
- `CoolingZoneConfigDefinition`
- `SupplyCoolingUnitConfigDefinition`
- `ExhaustCoolingUnitConfigDefinition`
- `CoolingZoneInfluenceConfigDefinition`
- `CoolingSystemOptionsDefinition`

`CoolingConfigurationFactory.create(DatacenterDefinition, Datacenter)` bridges
that JSON definition to the runtime model. It:

- returns `Optional.empty()` when `DatacenterDefinition.cooling()` is absent
- validates the full datacenter definition before creating cooling runtime data
- resolves runtime zones from installed server locations
- creates runtime units with `SUPPLY` units first and `EXHAUST` units after
- copies each unit's `initiallyEnabled` state into the runtime configuration
- transforms `CoolingSystemOptionsDefinition` into `CoolingSystemOptions`

At JSON-validation level, the cooling block currently requires:

- at least one zone
- at least one cooling unit across `supplyUnits` and `exhaustUnits`
- non-null `options`
- non-blank unique zone codes
- non-blank unique supply codes
- non-blank unique exhaust codes
- global uniqueness across supply and exhaust unit codes
- non-empty `columns` and `rackCodes` lists per zone
- non-empty influences per unit
- positive finite influence weights summing to `1.0`
- finite positive airflow values
- finite positive cooling capacity for `SUPPLY`
- finite supply temperature for `SUPPLY`
- finite positive air density and specific heat
- finite initial inlet-air temperature
- `maximumRecirculationFraction` within `[0.0, 1.0]`
- references only to known columns, racks, and zone codes
- no server location belonging to more than one zone
- at least one installed server in every valid zone

`CoolingSystemOptions.defaults()` uses:

- air density: `1.204 kg/m³`
- air specific heat: `1,005 J/(kg·K)`
- initial inlet-air temperature: `24.0 °C`
- maximum recirculation fraction: `0.95`

The volumetric heat capacity used by the model is:

```text
airVolumetricHeatCapacity = airDensity * airSpecificHeat
```

## Operational State

Cooling-unit definitions are immutable. `CoolingSystem` owns the current
enabled state of every configured unit and initializes it from
`CoolingUnitDefinition.initiallyEnabled()`.

The backend exposes:

```java
coolingSystem.enable("SUPPLY-01");
coolingSystem.disable("EXHAUST-01");
coolingSystem.setEnabled("SUPPLY-01", true);
boolean enabled = coolingSystem.toggle("EXHAUST-01");
```

These commands affect subsequent cooling ticks. Snapshots already produced are
immutable and are not changed retroactively.

`CoolingSystem.reset()` restores every unit to the initial state declared by
its definition and resets every zone-air temperature to
`CoolingSystemOptions.initialInletAirTemperatureCelsius()`.

A UI should invoke this backend API through its application or view-model
layer. It should not maintain a second authoritative copy of cooling-unit
state. The same API can be used by a console demo, keyboard shortcut, automated
test, or future remote-control adapter.

## Per-Tick Thermal Input

`ServerHeatLoadProvider` creates one `ServerHeatLoad` for every installed
server. In the current approximation:

```text
generatedHeatWatts = server.currentPowerWatts
```

The provider must run after `PowerConsumptionSystem`, so the cooling input uses
the electrical power calculated for the current tick. `OFFLINE` servers have
`0 W` power and therefore contribute no generated heat.

When a `SimulationTick` is already available, `DatacenterCoolingTickInputProvider`
offers the equivalent adapter that reads the current server power and produces a
`CoolingTickInput` directly from the `Datacenter`. The input also carries the
tick duration in seconds. `CoolingSystem` uses that duration when uncovered heat
must be accumulated into the zone-air state.

`CoolingSystem` aggregates the individual heat loads by cooling zone. Duplicate
server locations in the same tick input and locations not assigned to a zone
are rejected.

## Zone Calculations

For each zone, enabled units contribute their rated resources multiplied by
their influence weight.

### Available and Used Cooling Capacity

Only enabled `SUPPLY` units contribute cooling capacity:

```text
availableCoolingCapacityWatts =
    sum(supply.ratedCoolingCapacityWatts * influenceWeight)

usedCoolingCapacityWatts =
    min(generatedHeatWatts, availableCoolingCapacityWatts)

coolingDeficitWatts =
    max(0, generatedHeatWatts - availableCoolingCapacityWatts)
```

`availableCoolingCapacityWatts` is nominal active capacity assigned to the
zone. It is not reduced by recirculation in the current model. Recirculation
instead degrades the effective inlet-air temperature.

`CoolingZoneSnapshot.coolingCapacityUtilization()` returns used capacity divided
by available capacity. When available capacity is zero, it returns `0.0`; any
uncovered thermal load remains visible through `coolingDeficitWatts`.

### Supply-Air Temperature

When one or more supply units affect a zone, the nominal supply-air temperature
is weighted by their influenced airflow:

```text
weightedSupplyTemperature =
    sum(supplyTemperature * influencedSupplyAirflow)
    / totalSupplyAirflow
```

When no supply airflow exists, the model uses
the current zone-air temperature. Each zone starts at
`initialInletAirTemperatureCelsius`, but that value is kept between ticks and
changes when uncovered heat remains in the zone.

### Recirculation

Recirculation is derived from the imbalance between supply and extraction
airflow:

```text
if supplyAirflow == 0:
    recirculationFraction = maximumRecirculationFraction
otherwise:
    airflowImbalanceFraction =
        max(0, (supplyAirflow - exhaustAirflow) / supplyAirflow)

    recirculationFraction =
        min(airflowImbalanceFraction, maximumRecirculationFraction)
```

Equal or greater extraction airflow produces no recirculation in this simplified
calculation. Insufficient extraction increases recirculation up to the configured
maximum.

### Air Temperatures

The effective inlet temperature mixes the supplied-air temperature with the
previous zone-air temperature according to the recirculation fraction:

```text
inletAirTemperature =
    weightedSupplyTemperature * (1 - recirculationFraction)
    + previousZoneAirTemperature * recirculationFraction
```

When no supply airflow is available, `inletAirTemperature` is the current
zone-air temperature.

Heat that is covered by available cooling capacity does not increase the zone
air temperature. The temperature rise is based on `coolingDeficitWatts`, not on
the full generated heat.

When either supply or exhaust airflow is available, the temperature increase
across the active airflow path is:

```text
airTemperatureRise =
    coolingDeficitWatts
    / (airVolumetricHeatCapacity * max(supplyAirflow, exhaustAirflow))

exhaustAirTemperature =
    inletAirTemperature + airTemperatureRise
```

When both supply and exhaust airflow are zero, the zone has no active airflow
path. In that case, uncovered heat is accumulated into the persistent zone-air
state:

```text
temperatureRise =
    coolingDeficitWatts * deltaSeconds
    / effectiveZoneAirThermalCapacity

nextZoneAirTemperature =
    previousZoneAirTemperature + temperatureRise
```

The snapshot reports the previous zone-air temperature as inlet air and the next
zone-air temperature as exhaust air. The next temperature is then kept as the
zone state for the following tick.

## Snapshots

`CoolingSystem.tick(CoolingTickInput)` returns an immutable `CoolingSnapshot`
for the input tick.

`CoolingSnapshot` contains:

- `tickIndex`
- cooling-unit snapshots in configuration order
- cooling-zone snapshots in configuration order

It also provides lookups by code and aggregate methods for total generated heat
and total cooling deficit.

Each `CoolingUnitSnapshot` contains:

- `unitCode`
- `type`
- `enabled`
- `currentAirflowCubicMetersPerSecond`
- `currentCoolingPowerWatts`

Disabled units report zero airflow and zero cooling power. Exhaust units report
zero cooling power whether enabled or disabled.

Each `CoolingZoneSnapshot` contains:

- `zoneCode`
- `generatedHeatWatts`
- `availableCoolingCapacityWatts`
- `usedCoolingCapacityWatts`
- `coolingDeficitWatts`
- `supplyAirflowCubicMetersPerSecond`
- `exhaustAirflowCubicMetersPerSecond`
- `inletAirTemperatureCelsius`
- `exhaustAirTemperatureCelsius`
- `recirculationFraction`

It also exposes `hasCoolingDeficit()`, `coolingCapacityUtilization()`, and
`airTemperatureRiseCelsius()`.

## Integration with Temperature

`CoolingSnapshotTemperatureReferenceProvider` maps each installed server to its
configured cooling zone and returns the latest zone inlet-air temperature as the
server's thermal reference.

`CoolingSnapshotCoordinator` performs three operations for each tick:

```text
DatacenterCoolingTickInputProvider.inputFor(tick)
-> CoolingSystem.tick(input)
-> CoolingSnapshotTemperatureReferenceProvider.updateSnapshot(snapshot)
```

`TemperatureSystem` can then consume the provider through the
`ServerTemperatureReferenceProvider` abstraction. This keeps
`TemperatureSystem` independent from the mutable `CoolingSystem` and couples it
only to a temperature-reference contract.

The cooling snapshot must be installed before `TemperatureSystem` updates. If
the reference provider is queried before receiving a snapshot, for a server not
assigned to a zone, or with a snapshot that lacks the expected zone, it throws
`IllegalStateException`.

## Causal Order

With cooling enabled, the required order is:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> CoolingSnapshotCoordinator
-> TemperatureSystem
-> ServerHealthSystem
-> EnergyConsumptionSystem
```

`WorkloadSystem` determines utilization. `PowerConsumptionSystem` converts it
to current power. Cooling uses that same tick's power as generated heat and
publishes zone inlet temperatures. Temperature then uses those inlet
temperatures as its thermal references. Health and energy continue after the
thermal state has been updated.

`CoolingSnapshotCoordinator` is currently invoked explicitly between simulation
systems; it is not registered as a `SimulationSystem` in `SimulationEngine`.

## JSON and Runtime Example

```java
Path configPath = Path.of("data/config/demo-datacenter-medium.json");
DatacenterDefinition definition =
        new JsonDatacenterConfigLoader().load(configPath);
Datacenter datacenter = new DatacenterFactory().create(definition);

Optional<CoolingConfiguration> maybeCoolingConfiguration =
        new CoolingConfigurationFactory().create(definition, datacenter);

if (maybeCoolingConfiguration.isPresent()) {
    CoolingConfiguration coolingConfiguration =
            maybeCoolingConfiguration.orElseThrow();

    CoolingSystem coolingSystem =
            new CoolingSystem(coolingConfiguration);

    ServerHeatLoadProvider heatLoadProvider =
            new ServerHeatLoadProvider(datacenter);

    CoolingSnapshotTemperatureReferenceProvider temperatureReferenceProvider =
            new CoolingSnapshotTemperatureReferenceProvider(coolingConfiguration);

    SimulationTick tick = engine.step();
    CoolingTickInput input =
            new CoolingTickInput(
                    tick.index(),
                    tick.deltaSeconds(),
                    heatLoadProvider.createHeatLoads()
            );
    CoolingSnapshot coolingSnapshot = coolingSystem.tick(input);
    temperatureReferenceProvider.updateSnapshot(coolingSnapshot);
}
```

The coordinator form remains available when a simulation already uses
`SimulationTick` directly:

```java
CoolingSnapshotCoordinator coolingCoordinator =
        new CoolingSnapshotCoordinator(
                new DatacenterCoolingTickInputProvider(datacenter),
                coolingSystem,
                temperatureReferenceProvider
        );

CoolingSnapshot coolingSnapshot = coolingCoordinator.update(tick);
```

Production applications should build `CoolingConfiguration` from the validated
JSON definition rather than from ad hoc programmatic test fixtures.

## Demo

The interactive demo is:

```text
src/main/java/com/cpz/sim/datacenter/example/CoolingSimulationDemo.java
```

It demonstrates:

- a zone containing two installed servers
- one `SUPPLY` and one `EXHAUST` unit
- the causal order `workload -> power -> cooling -> temperature`
- backend state changes through `CoolingSystem.toggle(...)`
- keyboard control without a UI
- unit, zone, and server-temperature output after each tick

The controls are:

```text
Enter - execute the next simulation tick
S     - toggle SUPPLY-01
E     - toggle EXHAUST-01
Q     - quit
```

The demo confirms that a future UI toggle does not need to own or reproduce the
state-transition logic. It only needs to issue the corresponding backend
command and render a later snapshot.

## Tested Operating States

The example tests cover all four combinations of one supply and one exhaust
unit:

| SUPPLY | EXHAUST | Expected result |
| --- | --- | --- |
| enabled | enabled | cold supplied air without recirculation |
| disabled | enabled | no supply airflow or cooling capacity; zone temperature continues from its current state |
| enabled | disabled | cooling capacity remains available, but recirculation degrades inlet temperature |
| disabled | disabled | no supply airflow, extraction, or cooling capacity; uncovered heat accumulates gradually |

Separate integration tests verify that, from the same initial thermal state:

```text
normal inlet temperature
< exhaust-disabled inlet temperature
< supply-disabled inlet temperature
```

and that the server temperatures preserve the same ordering after one tick.

## Relationship to External Consumers

External consumers should:

- treat `CoolingSnapshot` as immutable output
- identify units and zones by their configured codes
- issue state-change commands through `CoolingSystem`
- generate cooling before temperature for each simulation tick
- avoid modifying `Server` or cooling definitions from the visual layer
- retain snapshots externally if historical cooling data is required

The current snapshot does not contain accumulated cooling energy, facility
power, PUE, alarms, or historical metrics.

## Current Limitations

- server electrical power is assumed to become heat at a one-to-one ratio
- zones are logical and use a simplified effective air volume, not measured
  physical geometry
- no pressure, humidity, leakage, containment, or fan-curve model
- no airflow propagation between zones
- no calibrated thermal mass for racks, walls, floor, ceiling, or equipment
- no cooling-unit ramp-up, partial-load behavior, failure mode, or energy use
- nominal cooling capacity is not reduced by recirculation
- extraction greater than supply does not create a separate negative-pressure
  effect
- no calibration against real datacenter cooling equipment

The model is suitable for simulation behavior, UI integration, and causal
experimentation. It should not be described as CFD or production-grade thermal
engineering software.

## Future Work

Possible future extensions include:

- integration of cooling results into higher-level operational snapshots
- cooling-unit electrical consumption and facility-energy metrics
- partial capacity, variable-speed fans, and equipment degradation
- configurable zone effective air volume and cross-zone thermal coupling
- rack inlet and hot-aisle aggregation
- cooling alarms and health reasons
- telemetry adapters for physical supply and exhaust equipment
- calibrated or interchangeable airflow and cooling models

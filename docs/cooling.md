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

## Cooling Zones

`CoolingZoneDefinition` represents a logical thermal area, such as a section of
a rack row or hot aisle. A zone has a unique code and an immutable set of exact
`ServerLocation` values.

A server location can belong to at most one cooling zone. A zone may be empty,
but every server heat load processed by `CoolingSystem` must reference a
location assigned to a configured zone.

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

Influence weights are applied independently per zone. The current model
validates each individual weight but does not require the weights of one unit
to sum to `1.0`.

## Configuration and Validation

`CoolingConfiguration` combines zones, units, and `CoolingSystemOptions` into
one immutable configuration. It validates that:

- zone and unit collections are not empty
- zone codes are unique
- unit codes are unique
- a server location does not belong to more than one zone
- every unit influence references a known zone
- a unit does not repeat the same zone in its influence list

The current cooling configuration is constructed programmatically. It is not
loaded from the datacenter JSON definition in this milestone.

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
its definition.

A UI should invoke this backend API through its application or view-model
layer. It should not maintain a second authoritative copy of cooling-unit
state. The same API can be used by a console demo, keyboard shortcut, automated
test, or future remote-control adapter.

## Per-Tick Thermal Input

`DatacenterCoolingTickInputProvider` creates one `ServerHeatLoad` for every
installed server. In the current approximation:

```text
generatedHeatWatts = server.currentPowerWatts
```

The provider must run after `PowerConsumptionSystem`, so the cooling input uses
the electrical power calculated for the current tick. `OFFLINE` servers have
`0 W` power and therefore contribute no generated heat.

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

When no supply airflow exists, the model falls back to
`initialInletAirTemperatureCelsius`.

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

When supply airflow is available, the temperature increase across the zone is:

```text
airTemperatureRise =
    generatedHeatWatts
    / (airVolumetricHeatCapacity * supplyAirflow)
```

The effective inlet and exhaust temperatures are then:

```text
inletAirTemperature =
    weightedSupplyTemperature
    + (recirculationFraction / (1 - recirculationFraction))
      * airTemperatureRise

exhaustAirTemperature =
    inletAirTemperature + airTemperatureRise
```

When supply airflow is zero, both temperatures fall back to the configured
initial inlet-air temperature. This is a deliberate fallback of the current
model: even if an exhaust unit remains enabled, the zone reports no calculated
air-temperature rise without supply airflow.

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

## Programmatic Example

```java
CoolingConfiguration coolingConfiguration =
        CoolingSimulationDemoScenario.createCoolingConfiguration(datacenter);

CoolingSystem coolingSystem =
        new CoolingSystem(coolingConfiguration);

CoolingSnapshotTemperatureReferenceProvider temperatureReferenceProvider =
        new CoolingSnapshotTemperatureReferenceProvider(coolingConfiguration);

CoolingSnapshotCoordinator coolingCoordinator =
        new CoolingSnapshotCoordinator(
                new DatacenterCoolingTickInputProvider(datacenter),
                coolingSystem,
                temperatureReferenceProvider
        );

TemperatureSystem temperatureSystem =
        new TemperatureSystem(
                datacenter,
                temperatureOptions,
                new SimpleServerTemperatureModel(),
                temperatureReferenceProvider
        );

SimulationTick tick = engine.step();
CoolingSnapshot coolingSnapshot = coolingCoordinator.update(tick);
temperatureSystem.update(tick);
```

The demo scenario helper is package-private and intended for the example and
its tests. Production applications should construct their own
`CoolingConfiguration`.

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
| disabled | enabled | no supply airflow or cooling capacity |
| enabled | disabled | cooling capacity remains available, but recirculation degrades inlet temperature |
| disabled | disabled | no supply airflow, extraction, or cooling capacity |

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

- configuration is programmatic; no cooling JSON block is implemented
- server electrical power is assumed to become heat at a one-to-one ratio
- zones are logical and do not model physical geometry or volume
- no pressure, humidity, leakage, containment, or fan-curve model
- no airflow propagation between zones
- no thermal mass or persistent air-temperature state for a zone
- no cooling-unit ramp-up, partial-load behavior, failure mode, or energy use
- nominal cooling capacity is not reduced by recirculation
- extraction greater than supply does not create a separate negative-pressure
  effect
- without supply airflow, inlet and exhaust temperatures use the configured
  fallback even when heat or extraction exists
- no calibration against real datacenter cooling equipment

The model is suitable for simulation behavior, UI integration, and causal
experimentation. It should not be described as CFD or production-grade thermal
engineering software.

## Future Work

Possible future extensions include:

- JSON configuration for cooling zones, units, influences, and options
- integration of cooling results into higher-level operational snapshots
- cooling-unit electrical consumption and facility-energy metrics
- partial capacity, variable-speed fans, and equipment degradation
- persistent zone air state and cross-zone thermal coupling
- rack inlet and hot-aisle aggregation
- cooling alarms and health reasons
- telemetry adapters for physical supply and exhaust equipment
- calibrated or interchangeable airflow and cooling models

# Temperature Model

The initial server temperature model adds a separate thermal simulation concern to
`cpz-sim-datacenter`. Its purpose is to expose a simplified per-server
temperature view that can be consumed by other applications without coupling the
simulation backend to any UI framework.

## Current Scope

The current milestone implements a server-level thermal model with these classes:

- `TemperatureSystem`
- `TemperatureSystemOptions`
- `ServerTemperatureModel`
- `SimpleServerTemperatureModel`
- `ServerThermalState`
- `TemperatureSnapshotProvider`
- `TemperatureSnapshot`
- `ServerTemperatureSnapshot`

The model uses `Server.currentPowerWatts` as thermal input and updates one
representative internal temperature per installed server.

An optional top-level JSON `temperature` block can be used to configure
`TemperatureSystemOptions`. If the block is omitted, `TemperatureSystemOptions.defaults()`
remains the fallback behavior. If the block is present, all current fields are
required.

## What `temperatureCelsius` Represents

`ServerTemperatureSnapshot.temperatureCelsius()` is a simplified representative
internal server temperature.

It should be interpreted as an internal device or server temperature proxy, not
as datacenter room temperature, cold aisle temperature, or rack inlet
temperature.

This distinction matters:

- room, ambient, and inlet temperatures are usually much lower
- values around `60-70 °C` can be plausible for internal server or device temperature under load
- those values must not be interpreted as room air temperature

## What It Does Not Represent

The current model does not represent:

- datacenter room temperature as a simulated output
- rack inlet temperature
- cold aisle or hot aisle behavior
- cooling zones
- CRAC or CRAH equipment
- airflow between servers or racks
- thermal coupling between racks

`ambientTemperatureCelsius` is only an input to the simplified thermal model. It
is not a full room or cooling simulation.

## Simplified Model

The current implementation is a first-order thermal approximation:

```text
heatLossWatts = heatDissipationWattsPerCelsius * (currentTemperatureCelsius - ambientTemperatureCelsius)
netThermalPowerWatts = currentPowerWatts - heatLossWatts
deltaTemperatureCelsius = (netThermalPowerWatts / thermalCapacityJoulesPerCelsius) * deltaSeconds
nextTemperatureCelsius = currentTemperatureCelsius + deltaTemperatureCelsius
```

Conceptually:

- server power adds heat
- heat dissipation removes heat in proportion to how far the current internal temperature is above or below the ambient input
- thermal capacity controls how quickly temperature changes

This is intentionally a simplified model. It should not be described as
production-grade thermal accuracy.

## Model Inputs

### `currentPowerWatts`

`TemperatureSystem` uses the current server power already computed by
`PowerConsumptionSystem`.

- more power means more thermal input
- `OFFLINE` servers have `0 W` power, so they stop receiving heat from power consumption

### `ambientTemperatureCelsius`

This is the ambient reference used by the simplified thermal model.

- it influences the heat loss term
- it acts as the temperature the server tends toward when it has no power input
- it is not a complete room or cooling model

### `thermalCapacityJoulesPerCelsius`

This controls thermal inertia.

- larger values make temperature change more slowly
- smaller values make temperature react more quickly to power changes

### `heatDissipationWattsPerCelsius`

This controls how strongly the server exchanges heat with the ambient reference.

- larger values pull temperature toward ambient more aggressively
- `0.0` disables that dissipation term

## OFFLINE Server Behavior

An `OFFLINE` server remains an installed server in the datacenter model, but
`Server.updatePowerConsumption()` forces its `currentPowerWatts` to `0.0f`.

As a result:

- the server does not add thermal power from electrical consumption
- if its internal temperature is above the ambient input, it cools toward ambient over time
- if it already equals the ambient input, it remains stable

The `TemperatureSimulationDemo` includes an `OFFLINE` server that tends toward
ambient temperature.

## Relationship to Other Systems

### `PowerConsumptionSystem`

The intended causal order is:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> TemperatureSystem
-> EnergyConsumptionSystem
```

`TemperatureSystem` must run after `PowerConsumptionSystem` so it reads the
current tick's `currentPowerWatts`.

### `EnergyConsumptionSystem`

`EnergyConsumptionSystem` and `TemperatureSystem` are separate concerns:

- temperature uses current power as thermal input
- energy integrates power over time into accumulated energy
- neither snapshot replaces the other

## Snapshot Independence

Temperature data is exposed through `TemperatureSnapshotProvider` using
`snapshot(SimulationTick tick)`.

This snapshot is independent from `EnergyConsumptionSnapshotProvider`:

- `TemperatureSnapshot` contains temperature-oriented data
- `EnergyConsumptionSnapshot` contains energy and IT power data
- there is no global combined `DatacenterSnapshot` in the current architecture

Snapshot providers read resulting state after systems update. They are not
simulation systems themselves.

## Demo

The current demo is:

```text
src/main/java/com/cpz/sim/datacenter/example/TemperatureSimulationDemo.java
```

It demonstrates:

- `WorkloadSystem`
- `PowerConsumptionSystem`
- `TemperatureSystem`
- `EnergyConsumptionSystem`
- `EnergyConsumptionSnapshotProvider`
- `TemperatureSnapshotProvider`
- separate energy and temperature snapshots
- an `OFFLINE` server with `0 W` power that trends toward ambient

The exact numeric output depends on workload evolution and tick duration.

## Current Limitations

- simplified internal server temperature only
- no room temperature simulation output
- no rack inlet temperature
- no cooling model
- no airflow model
- no rack-to-rack thermal coupling
- no calibration against specific server hardware

## Future Work

Possible future extensions, not implemented in this milestone:

- rack inlet temperature modeling
- room or zone temperature modeling
- cooling equipment integration
- airflow and thermal coupling between racks
- more advanced per-server thermal models

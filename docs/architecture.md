# Architecture

`cpz-sim-datacenter` is organized as a pure Java backend. The library models the
physical state of a datacenter, loads JSON configuration, runs simulation systems,
and exposes energy snapshots for external consumers.

It does not include Processing or a UI. A future graphical application should
consume this project as a Maven dependency and treat it as a domain/simulation
backend.

## Main Packages

- `com.cpz.sim.datacenter.model`: domain model (`Datacenter`, `Rack`, `Server`, `ServerLocation`, `HardwareStatus`, etc.).
- `com.cpz.sim.datacenter.config`: configuration loading contract.
- `com.cpz.sim.datacenter.config.definition`: records that represent JSON (`DatacenterDefinition`, `RackDefinition`, `ServerModelDefinition`, `ServerDefinition`).
- `com.cpz.sim.datacenter.config.json`: JSON loading with Jackson (`JsonDatacenterConfigLoader`).
- `com.cpz.sim.datacenter.config.validation`: validation of definitions before building the domain.
- `com.cpz.sim.datacenter.factory`: domain construction and helper providers (`DatacenterFactory`, `WorkloadFactorProviderFactory`).
- `com.cpz.sim.datacenter.workload`: workload strategies (`WorkloadSource` and its implementations).
- `com.cpz.sim.datacenter.system`: systems updated on each tick.
- `com.cpz.sim.datacenter.snapshot`: energy snapshot DTOs and provider.
- `com.cpz.sim.datacenter.example`: runnable demos.

## Domain Model

`Datacenter` contains a list of `Rack` and a list of `Server`. During construction
it validates that racks are unique, that each server points to an existing rack,
that the slot uses the `U<n>` format, and that the slot is inside the rack range.

`Rack` represents physical infrastructure:

- `RackCode code`
- `RackLocation location`, with `column` and `row`
- `slotCount`

A rack can exist without servers. This makes it possible to model unused physical
capacity.

`Server` represents an installed server:

- `ServerLocation location`, composed of `RackCode rackCode` and `slot`
- `ServerConfig config`
- `HardwareStatus status`
- `utilization`
- `currentPowerWatts`

`ServerLocation.code()` derives the server code as:

```text
<rackCode>-<slot>
```

Example: `RACK-A01-R01-U01`.

`HardwareStatus` supports:

- `OK`
- `ALERT`
- `OFFLINE`

## Systems

The systems implement `Simulatable` from `cpz-sim-foundation` and are registered in
`SimulationEngine`.

Registration order matters:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> EnergyConsumptionSystem
```

After running a tick, a consumer can create a snapshot with
`EnergyConsumptionSnapshotProvider`.

## Causal Order

1. `WorkloadSystem` computes `Server.utilization` for each operational server.
2. `PowerConsumptionSystem` recalculates `Server.currentPowerWatts`.
3. `EnergyConsumptionSystem` integrates accumulated energy using total IT power and `tick.deltaSeconds()`.
4. `EnergyConsumptionSnapshotProvider` reads the resulting state and builds an `EnergyConsumptionSnapshot`.

If this order is changed, energy or power values may reflect the previous tick.

## Current Energy Rules

The power of an operational server is calculated linearly between `idlePowerWatts`
and `maxPowerWatts`:

```text
idlePowerWatts + utilization * (maxPowerWatts - idlePowerWatts)
```

If the server is `OFFLINE`, `Server.updatePowerConsumption()` leaves
`currentPowerWatts` at `0.0f`.

`EnergyConsumptionSystem` accumulates energy in Wh:

```text
consumedEnergyWh += datacenter.getTotalItPowerWatts() * (tick.deltaSeconds() / 3600.0)
```

## Current Limits

- Preliminary API.
- No temperature model yet.
- No cooling model yet.
- No advanced electrical model.
- No UI.
- No result persistence.

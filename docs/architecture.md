# Architecture

`cpz-sim-datacenter` is organized as a pure Java backend. The library models the
physical state of a datacenter, loads JSON configuration, runs simulation systems,
and exposes independent snapshots for external consumers.

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
- `com.cpz.sim.datacenter.temperature`: server thermal state and temperature model contracts.
- `com.cpz.sim.datacenter.system`: systems updated on each tick.
- `com.cpz.sim.datacenter.snapshot`: snapshot DTOs and providers.
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
-> TemperatureSystem
-> EnergyConsumptionSystem
```

Snapshot providers are readers of state after the systems update. They are not
simulation systems and do not advance the simulation.

## Causal Order

1. `WorkloadSystem` computes `Server.utilization` for each operational server.
2. `PowerConsumptionSystem` recalculates `Server.currentPowerWatts`.
3. `TemperatureSystem` updates a representative internal server temperature from current server power.
4. `EnergyConsumptionSystem` integrates accumulated energy using total IT power and `tick.deltaSeconds()`.
5. Snapshot providers such as `EnergyConsumptionSnapshotProvider` and `TemperatureSnapshotProvider` read the resulting state and build immutable DTOs.

If this order is changed, power, temperature, or energy values may reflect the
previous tick.

## Snapshot Ownership

Each simulation concern owns its own state and snapshot model.

- `EnergyConsumptionSystem` exposes energy data through `EnergyConsumptionSnapshotProvider`.
- `TemperatureSystem` exposes temperature data through `TemperatureSnapshotProvider`.

There is intentionally no global `DatacenterSnapshot` at this stage.

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
- Temperature is currently a simplified server-level internal model.
- No cooling model yet.
- No rack inlet, room temperature, airflow, or rack-to-rack thermal coupling.
- No advanced electrical model.
- No UI.
- No result persistence.

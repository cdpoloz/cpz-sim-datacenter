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
- `com.cpz.sim.datacenter.config.definition`: objects that represent JSON (`DatacenterDefinition`, `RackDefinition`, `ServerModelDefinition`, `ServerDefinition`) plus helpers for effective rack slots.
- `com.cpz.sim.datacenter.config.json`: JSON loading with Jackson (`JsonDatacenterConfigLoader`).
- `com.cpz.sim.datacenter.config.validation`: validation of definitions before building the domain.
- `com.cpz.sim.datacenter.factory`: domain construction and option/provider factories (`DatacenterFactory`, `WorkloadFactorProviderFactory`, `TemperatureSystemOptionsFactory`, `ServerHealthOptionsFactory`).
- `com.cpz.sim.datacenter.workload`: workload strategies (`WorkloadSource` and its implementations).
- `com.cpz.sim.datacenter.temperature`: server thermal state and temperature model contracts.
- `com.cpz.sim.datacenter.health`: health thresholds, alert reasons, and per-server health state.
- `com.cpz.sim.datacenter.system`: systems updated on each tick.
- `com.cpz.sim.datacenter.snapshot`: snapshot DTOs and providers.
- `com.cpz.sim.datacenter.example`: runnable demos.

## Domain Model

`Datacenter` contains a list of `Rack` and a list of `Server`. During construction
it validates that racks are unique, that each server points to an existing rack,
and that the server slot exists in the referenced rack's effective slot list.

`Rack` represents physical infrastructure:

- `RackCode code`
- `RackLocation location`, composed of `column` and `RackCode`
- `row`, retained as layout metadata
- ordered slot codes

Rack identity is `column + rackCode`. `RackCode` is not globally unique; `C01/R01`
and `C02/R01` are valid distinct racks. Within a single column, a rack code must
remain unique.

`Rack.getSlotCount()` returns the number of effective slots. For legacy
configuration this is the declared `slotCount`; for explicit configuration this is
the size of the `slots` array. `Rack.getSlotCodes()` exposes an immutable ordered
list, and `Rack.hasSlot(String)` performs exact membership checks.

A rack can exist without servers. This makes it possible to model unused physical
capacity.

`Server` represents an installed server:

- `ServerLocation location`, composed of `column`, `RackCode rackCode`, and `slot`
- `ServerConfig config`
- `HardwareStatus status`
- `utilization`
- `currentPowerWatts`

`ServerLocation.code()` derives the server code as:

```text
<column>-<rackCode>-<slot>
```

Examples: `C01-R01-S01` and `C02-R01-S01`.

The `slot` part is opaque. It may be `U01`, `S01`, `GPU-A`, `NETWORK`, or any other
non-blank slot code declared by the rack. The backend does not derive physical
position by parsing the slot text.

`Datacenter` exposes location-aware lookup APIs:

```java
Optional<Rack> rack = datacenter.findRack("C01", "R01");
List<Server> rackServers = datacenter.getServers("C01", "R01");
Optional<Server> installed = datacenter.getServer("C01", "R01", "S03");
```

`getServers(...)` throws for an unknown rack and returns an empty immutable list
for a valid empty rack. `getServer(...)` throws for an unknown rack or undeclared
slot and returns `Optional.empty()` when the slot is valid but empty.

`HardwareStatus` supports:

- `OK`
- `ALERT`
- `OFFLINE`

The value loaded from each server's JSON `status` field is its initial state, not
an immutable classification. During simulation, `ServerHealthSystem`
automatically changes non-`OFFLINE` servers between `OK` and `ALERT` according to
their current utilization and internal temperature. `OFFLINE` always has priority
and the health system does not overwrite it.

## Systems

The systems implement `Simulatable` from `cpz-sim-foundation` and are registered in
`SimulationEngine`.

Registration order matters:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> TemperatureSystem
-> ServerHealthSystem
-> EnergyConsumptionSystem
```

Snapshot providers are readers of state after the systems update. They are not
simulation systems and do not advance the simulation.

## Causal Order

1. `WorkloadSystem` computes `Server.utilization` for each operational server.
2. `PowerConsumptionSystem` recalculates `Server.currentPowerWatts`.
3. `TemperatureSystem` updates a representative internal server temperature from current server power.
4. `ServerHealthSystem` evaluates current utilization and temperature, updates active alert reasons, and derives `HardwareStatus` for every non-`OFFLINE` server.
5. `EnergyConsumptionSystem` integrates accumulated energy using total IT power and `tick.deltaSeconds()`.
6. Snapshot providers such as `EnergyConsumptionSnapshotProvider`, `TemperatureSnapshotProvider`, and `HealthSnapshotProvider` read the resulting state and build immutable DTOs.

If this order is changed, power, temperature, health, or energy values may not
represent the same tick.

`ServerHealthSystem` produces `OK` when no monitored condition is active and
`ALERT` when utilization, temperature, or both have an active alert. Each
condition uses configured activation and clearing limits, with hysteresis between
them. The system also retains the active reasons in `ServerHealthState`.

## Snapshot Ownership

Each simulation concern owns its own state and snapshot model.

- `EnergyConsumptionSystem` exposes energy data through `EnergyConsumptionSnapshotProvider`.
- `TemperatureSystem` exposes temperature data through `TemperatureSnapshotProvider`.
- `ServerHealthSystem` exposes status, alert reasons, utilization, and temperature through `HealthSnapshotProvider`.

There is intentionally no global `DatacenterSnapshot` at this stage.

Providers that include a server status read the current `Server.status`. When
they are invoked after the complete pipeline, that value is the status calculated
by `ServerHealthSystem` for the tick, except that an existing `OFFLINE` status is
preserved.

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

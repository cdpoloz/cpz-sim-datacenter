# Temperature Model

`cpz-sim-datacenter` distinguishes representative internal server temperatures,
optional common room-air temperature and optional local rack-air temperatures.
These are independent thermal states. The coupled pipeline connects servers and
air through heat transfer rather than treating electrical power as room heat
directly. All are backend quantities independent of a UI framework.

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

That temperature is also an input to `ServerHealthSystem`, which can derive an
`ALERT` status when the configured temperature threshold is active.

`ServerTemperatureSnapshot` includes `column`, `rackCode`, and `slot`. The complete
server identity is `column + rackCode + slot`, so temperature snapshots can
distinguish `C01/R01/S01` from `C02/R01/S01`.

An optional top-level JSON `temperature` block can be used to configure
`TemperatureSystemOptions`. If the block is omitted, `TemperatureSystemOptions.defaults()`
remains the fallback behavior. If the block is present, all current fields are
required.

Each `serverModels` entry may additionally declare
`thermalCapacityJoulesPerCelsius` and
`heatDissipationWattsPerCelsius`. They are optional as a pair. Existing model
definitions that omit both fields continue to use the global values and preserve
the previous behavior.

## Room-air state preparation

An optional top-level `roomAirTemperature` configuration now prepares a separate
`RoomAirTemperatureOptions` and `RoomAirThermalState`. It specifies initial air
temperature, effective room thermal capacity, environment temperature and the
linear heat-transfer coefficient to that environment. The runtime factory
returns no options when this block is omitted; no room physics is guessed.

`RoomAirTemperatureSystem` can now advance this state from explicitly supplied
thermal transfers and return a separate `RoomAirTemperatureSnapshot`. It is not
automatically registered or connected to servers or cooling. The optional coupled
pipeline below also captures room air into operational/history snapshots. See
[JSON configuration](configuration.md#room-air-state-preparation) for the contract.

### Explicit room-air energy balance

The independent, well-mixed room node follows:

```text
C * dT/dt = Q_server_to_air - Q_removed - UA * (T - T_environment)
```

- `C`: configured effective room capacity, in J/°C.
- `Q_server_to_air`: signed heat actually transferred from servers to air, in W.
  A negative value means heat flows from air into colder servers.
- `Q_removed`: non-negative heat actually removed by cooling/ventilation, in W.
  It may exceed simultaneous server heat while removing stored room energy.
- `UA`: configured heat-transfer coefficient to the environment, in W/°C.

`SimpleRoomAirTemperatureModel` integrates this equation analytically while
thermal inputs and options are constant over a tick. For `UA = 0`,
`T_next = T + (Q_server_to_air - Q_removed) * deltaSeconds / C`.
For positive `UA`, the solution relaxes exponentially toward
`T_environment + (Q_server_to_air - Q_removed) / UA` with time constant `C / UA`.
The implementation uses `expm1` and a small-coefficient limit to avoid cancellation.
It applies no temperature clamp. Invalid/non-finite inputs or results are rejected.

For constant inputs, subdividing a tick produces the same solution up to rounding;
time-varying heat transfers still require appropriately sampled inputs. This exact
room integrator does not change the existing per-server Euler integrator.

`RoomAirTemperatureSystem.tick(tick, input)` advances once and returns an immutable
snapshot with tick index, elapsed seconds and room-air temperature. Call it once
per tick, in chronological order. `reset()` restores the configured initial air
temperature and clears the last snapshot. Rejected steps preserve the state.

The standalone system is not an automatic adapter from IT power or legacy
cooling snapshots. Use the coupled system below when server and room temperatures
must evolve together.

### Coupled server, room and cooling model

`CoupledRoomTemperatureSystem` implements `Simulatable` and owns both thermal
updates in `UTILIZATION_DRIVEN` and `POWER_DRIVEN`. It writes per-server results
into the existing `TemperatureSystem` state store, preserving health and server
snapshot consumers. Its equations are:

```text
C_i * dT_i/dt = P_i - H_i * (T_i - T_air)
C_air * dT_air/dt = sum(H_i * (T_i - T_air))
                    - Q_cooling(T_air) - UA * (T_air - T_environment)
```

All installed servers participate, including offline servers releasing stored
heat at zero electrical power. Per-model capacity and conductance take precedence
over global server options. Signed heat exchange is used on both sides of each
server/air connection; electrical IT input is never added directly to the air.

The solver uses simultaneous backward Euler substeps of at most one second,
rather than combining two independent explicit updates. This is a conservative
numerical approximation, not the exact standalone constant-input solution.
IT power and enabled unit states are constant within each external tick. Tick
duration must be in `(0, 86400]` seconds to bound work. Repeated or backward
tick indices/times are rejected; reset before replaying a run.

For a substep of length `dt`, elimination of server states gives:

```text
gamma_i = H_i / (1 + dt * H_i / C_i)
base_i  = T_i_old + dt * P_i / C_i
A       = C_air / dt + UA + sum(gamma_i)
B       = C_air / dt * T_air_old + UA * T_environment + sum(gamma_i * base_i)
A * T_air_new + Q_cooling(T_air_new) = B
q_i = gamma_i * (base_i - T_air_new)
T_i_new = T_i_old + dt * (P_i - q_i) / C_i
```

The scalar room equation is monotone and solved by bracketed bisection. The same
transfers update server energy, room energy and the joule ledger. Failed steps
publish no partial server/air state. `reset()` restores initial server/air
temperatures, clears the ledger and resets cooling units; power/workload reset
remains with those systems.

`CoolingSystem.roomAirCoolingPaths()` reads unit controls without advancing legacy
zone air. Each enabled SUPPLY contributes paths weighted by its zone influences:

```text
K = air_density * air_specific_heat * supply_flow * influence * (1 - recirculation)
Q_path(T_air) = max(0, min(rated_capacity * influence, K * (T_air - T_supply)))
Q_cooling(T_air) = sum(Q_path(T_air))
```

Recirculation uses the existing zone flow-balance target and configured residual
and maximum fractions, instantaneously in this mixed-room approximation. Balanced
EXHAUST improves return flow; it is not outdoor ventilation or a second cooling
source. Exhaust alone provides no cooling here. A SUPPLY hotter than room air
does not provide heating in this cooling-only approximation. Influence weights
must sum to one. Zone air volume and recirculation response time remain legacy
parameters and do not add another air capacity or lag in coupled mode.

Removal can exceed IT input when cooling stored energy, but is limited by supply
capacity and vanishes below supply temperature. HVAC electrical power is separate
through `CoolingSystem.currentUnitSnapshots()`. This first thermal boundary
excludes HVAC/fan motor heat; it is not added to room air.

`CoupledRoomThermalSnapshot` exposes a room-air snapshot and integrated energies:
IT input, server storage change, room storage change, signed server-to-air heat,
removed heat and signed environment loss. Its `energyBalanceResidualJoules()`
should be zero within rounding tolerance. Operational/history snapshots retain
the server aggregate and independently expose optional room-air data and the ledger.
An absent air model produces no air snapshot and a NaN numeric air accessor; it
never falls back to server or configured ambient temperature.

`lastCoolingSnapshot()` returns a read-only instantaneous projection captured at
the end of the coupled tick. Electrical unit activity is real enabled-unit power;
zone inlet/exhaust values both represent the same mixed room air, with no spatial
temperature rise. Used cooling capacity is instantaneous removal at that final
temperature, whereas `removedHeatJoules` integrates the full tick. Zone generated
heat/deficit remain electrical IT load diagnostics and must not replace the
conservative ledger or be treated as stored room energy.

Do not run legacy `CoolingSystem.tick`, `CoolingSnapshotCoordinator`,
`CoolingSimulationSystem`, `TemperatureSystem.update` or the standalone
`RoomAirTemperatureSystem` alongside this coupled pipeline. They would evolve
competing states. `CoupledRoomTemperatureSystemFactory` returns empty without
`roomAirTemperature` and rejects `TEMPERATURE_DRIVEN` coupling: observed
temperatures need a separate inference model and must not be overwritten.

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

The legacy per-server temperature value does not represent:

- physical datacenter room-air temperature (the separate room-air system handles that approximation)
- rack inlet temperature
- cold aisle or hot aisle behavior
- cooling zones
- CRAC or CRAH equipment
- airflow between servers or racks
- thermal coupling between racks

`ambientTemperatureCelsius` is only an input to the simplified thermal model. It
is not a full room or cooling simulation.

`DatacenterOperationalSnapshot.averageOnlineServerTemperatureCelsius()` is the
online-server-weighted mean of representative internal server temperatures. It
is not room air and is not the configured ambient reference. If no servers are
online, the aggregate uses the configured ambient reference as a compatibility
fallback.

`roomTemperatureCelsius()` is a deprecated compatibility alias for that same
server aggregate; despite its name, it has never returned the simulated room-air
state. Read the latter with `roomAirTemperatureCelsius()`, which is `NaN` when
the optional room-air model is absent. The optional snapshot is available as
`roomAirSnapshot()`.

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
- in global `TemperatureSystemOptions`, `0.0` disables that dissipation term
- a model-specific value must be greater than zero

## Effective Properties per Server Model

`TemperatureSystem` is the layer that knows both the installed `Server` and the
global options. For every server it resolves:

```text
thermal capacity and heat dissipation:
    ServerConfig.thermalProperties, when present
    otherwise TemperatureSystemOptions

ambient and default initial temperature:
    TemperatureSystemOptions
```

The resolved values are then passed to `ServerTemperatureModel` for the
mathematical update. `SimpleServerTemperatureModel` does not inspect
`ServerRole`, and roles such as `AI` or `GPU` never activate thermal behavior.

In JSON, both model-specific fields must be present together or both omitted.
Explicit `null`, non-numeric values, non-finite values, zero, and negative values
are rejected. The example `SRV-DEMO-002` model in
`data/config/datacenter-ui-test.json` declares a differentiated pair; the other
model in that file demonstrates fallback to the global values.

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

In `UTILIZATION_DRIVEN` and `POWER_DRIVEN`, `TemperatureSystem` derives
temperature from current server power. The utilization-driven causal order is:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> TemperatureSystem
-> ServerHealthSystem
-> EnergyConsumptionSystem
```

`TemperatureSystem` must run after `PowerConsumptionSystem` so it reads the
current tick's `currentPowerWatts`. `ServerHealthSystem` must run after
`TemperatureSystem` so it evaluates the temperature produced for that tick.

In `TEMPERATURE_DRIVEN`, temperature is the authoritative input. Use
`DatacenterInputPipelineFactory` so `RackTemperatureInputSystem` writes the
observed rack or hot-aisle-derived temperature into `TemperatureSystem` state.
Do not run `TemperatureSystem.update(...)` afterward in that mode, because it
would recalculate temperature from power and overwrite the observed input.

### `ServerHealthSystem`

`ServerHealthSystem` reads the representative internal temperature together with
current utilization. It applies the configured `temperatureCelsius` activation
and clearing limits and records `HIGH_TEMPERATURE` while that condition is
active. It then derives `HardwareStatus.ALERT` or `HardwareStatus.OK` for
non-`OFFLINE` servers; `OFFLINE` is preserved.

See [Server Health](server-health.md) for threshold behavior and configuration.

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

`ServerTemperatureSnapshot.status()` reads the current server status. If the
snapshot is taken after `ServerHealthSystem`, it contains the health-calculated
status for the tick, or the preserved `OFFLINE` status.

Temperature snapshots contain installed servers only. Empty physical slots are
obtained from `Rack.getSlotCodes()` and correlated with
`Datacenter.getServer(column, rack, slot)`.

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

The demo does not register `ServerHealthSystem`; consequently, the status printed
by that demo remains the status assigned when each server is constructed. Add
the health system after `TemperatureSystem`, as shown above, when the output must
show automatically calculated status.

The exact numeric output depends on workload evolution and tick duration.

## Current Limitations

- internal server temperature remains a representative device proxy
- room-air output requires the opt-in standalone or coupled system
- local rack air is well mixed; no separate physical rack inlet/outlet sensors
- coupled cooling uses a well-mixed room and simplified effective airflow
- no direct rack-to-rack airflow; local rack nodes interact indirectly through common room air
- no claim of calibration against real server hardware

## Future Work

The staged rack-contained cooling extension is specified in the
[stage 1 design contract](#rack-contained-cooling-implementation-contract-stage-1)
below. Configuration, equipment controls and coupled rack thermal evolution
are implemented. Broader validation and operational/history/energy integration
remain subsequent stages.

Possible future extensions, not implemented in this milestone:

- rack inlet temperature modeling
- calibrating room parameters and separating spatial air zones/inlets
- temperature-driven air inference and calibrated cooling equipment behavior
- airflow and thermal coupling between racks
- more advanced per-server thermal models


## Rack-contained cooling: implementation contract (stage 1)

**Status: stage 4 implements the coupled rack-air solver and removes the
temporary execution guards.** JSON validation and equipment controls are also
available. Rack-specific operational/history integration and facility electrical
aggregates remain subsequent stages. The existing behavior is preserved when
`rackCooling` is absent.
The parameters remain uncalibrated until independent measurements are available.

### Scope and identity

A rack-contained cooling unit serves exactly one physical rack, identified by
the pair `column + rackCode`. A rack code alone is insufficient because rack
codes may repeat across columns. Its unit code must be globally unique across
room SUPPLY, EXHAUST and rack-contained units.

For the first version, allow at most one rack-contained cooling unit per rack.
Each rack served by such a unit has one explicit, well-mixed local air node.
All its installed servers, including offline servers, exchange heat with that
node instead of exchanging heat directly with room air. Other racks retain the
current direct server-to-room connection. An empty configured rack can retain
its local air node and cooling unit; installing servers is not a thermal
prerequisite.

A dedicated rack connection is not a legacy cooling-zone influence weight.
The unit must never also contribute a room-air cooling path. Existing logical
zone membership can remain for diagnostics, but must not create a second
server-to-air heat transfer or a second refrigeration sink.

This is a simplified recirculating local-air model, not a detailed reproduction
of every rack-mounted air conditioner. It does not model separate hot/cold rack
compartments, condenser air mass flow, stratification, humidity, pressure or
ducted ventilation. Mounting at the bottom does not by itself change the
equations. Physical slot reservation and rendering belong to later equipment
placement/UI work; this contract does not treat the unit as a server.

### Required physical quantities

The configuration stage must provide explicit values for these quantities.
Names below describe the physical contract. The implemented JSON structure is
documented in [rack cooling configuration](configuration.md#rack-cooling-configuration-stage-2).

| Quantity | Unit | Constraint / meaning |
| --- | --- | --- |
| Rack identity | — | Existing column and rack code pair |
| Initial local air temperature | °C | Finite; independent of server internal temperature |
| Effective local air thermal capacity `C_r` | J/°C | Finite and strictly positive |
| Rack-to-room conductance `G_r` | W/°C | Finite and non-negative; zero represents ideal thermal isolation |
| Unit code | — | Non-blank and globally unique |
| Rated cooling capacity `Q_max` | W | Finite and strictly positive |
| Internal recirculating airflow `F` | m³/s | Finite and strictly positive |
| Supply-air temperature `T_supply` | °C | Finite; lower reference for this cooling-only approximation |
| Rated electrical power `P_rated` | W | Finite and strictly positive |
| Heat rejection destination | — | Explicitly `ROOM` or `EXTERNAL`; no implicit default |
| Initially enabled | boolean | Explicit initial operational state |

Effective local air capacity represents the local node only. It must not include
server thermal capacities already modeled separately. Room effective capacity
must likewise exclude any capacity assigned to local rack nodes. The backend
cannot infer this partition from rack dimensions; example configurations must
state their assumptions.

The conductance is an effective approximation of enclosure heat exchange and
air mixing. It is not inferred from the cooling unit's internal airflow.
Use the configured cooling air density and specific heat to convert internal
airflow to thermal conductance. No external EXHAUST unit is required for this
local recirculation path, and the legacy supply/exhaust imbalance penalty does
not apply to it.

### Cooling and operational behavior

For enabled units, use the initial fixed-power approximation:

```text
K_r = air_density * air_specific_heat * F
Q_r(T_r) = max(0, min(Q_max, K_r * (T_r - T_supply)))
P_r = P_rated
Q_rejected_r = Q_r + P_r
```

For disabled units, `Q_r = P_r = Q_rejected_r = 0`. Disabling a unit does not
remove its local air node or disable passive rack-to-room heat exchange.
Cooling can remove stored heat even when server electrical load is zero.
It vanishes when local air is at or below supply temperature; it does not
actively heat that air. This is not a global temperature clamp.

Enabled units consume rated electrical power even when actual cooling is zero.
Their electrical consumption is not inferred from rated cooling capacity or
from IT load. Thermostat cycling, COP curves and partial-load control are
outside the first version.

Reset restores configured initial local-air temperature and unit enabled state,
together with the existing server/room reset behavior.

### Heat rejection boundary

| Destination | Thermal meaning |
| --- | --- |
| `ROOM` | Deliver `Q_r + P_r` to the common room air node |
| `EXTERNAL` | Export `Q_r + P_r` across the modeled rack–room boundary; do not add it to room air |

For this first approximation, **all electrical dissipation of the new unit is
assigned to its rejection destination**. Internal fan motor heat is not assigned
separately to rack air. This assumption must remain visible in documentation;
it can later be refined into separate electrical heat fractions without
changing the meaning of the two destinations.

`EXTERNAL` specifies a boundary condition, not an assertion that the unit has
zero electrical consumption. It can approximate an external condenser or an
external water cooling service. It does not simulate those services or add
unconfigured chiller/pump electricity to facility totals.

A return-air duct is not automatically `EXTERNAL`: use that boundary only when
the selected scenario explicitly treats the captured heat as leaving the
modeled room. A separate duct/return node is deferred.

### Conservative thermal equations

For a server `i` inside a local-air rack `r`:

```text
q_i = H_i * (T_i - T_r)
C_i * dT_i/dt = P_i - q_i

q_r_room = G_r * (T_r - T_room)
C_r * dT_r/dt = sum(q_i in rack r) - q_r_room - Q_r(T_r)
```

For servers without a local-air rack, keep the existing
`q_i = H_i * (T_i - T_room)`. The room equation becomes:

```text
C_room * dT_room/dt =
    sum(q_i from servers without local rack air)
  + sum(q_r_room from all local rack nodes)
  + sum(Q_r + P_r for units rejecting to ROOM)
  - Q_existing_room_cooling(T_room)
  - UA * (T_room - T_environment)
```

All server/air and rack/room exchanges are signed: colder servers can absorb
heat, and colder rack air can absorb heat from the room. Each internal transfer
appears once with opposite signs at its two endpoints.

Electrical IT load must not also be injected directly into rack or room air.
The heat rejected by a `ROOM` unit is an internal transfer plus its electrical
input; it is not heat removed from the combined system. A `ROOM` unit can cool
its rack while increasing the thermal load handled by room cooling.

The implementation advances servers, local rack nodes and room air together.
Stage 4 uses analytical elimination of piecewise-linear rack air equations and
a monotone scalar room solve, preserving the bounded backward-Euler substep
policy, atomic publication, chronological tick validation and reset semantics.
See the numerical details in the stage 4 section below.

### Energy ledger and facility power

Over each tick, the extended thermal balance must satisfy:

```text
E_IT + E_new_rack_cooling_electric
  = delta_E_servers + delta_E_local_rack_air + delta_E_room_air
  + E_existing_room_cooling_removed
  + E_new_rack_rejected_external
  + E_environment_loss
```

Here `E_new_rack_rejected_external` integrates `Q_r + P_r` only for
`EXTERNAL` units. Electrical energy for all new units is included on the input
side; for external rejection its electrical part also leaves on the output
side. Internal server-to-rack, rack-to-room and rack-rejection-to-room transfers
must be separately inspectable but cancel from the global balance.
Storage changes and environment loss are signed.

The existing room HVAC electrical heat exclusion remains unchanged. Its
electrical consumption continues to be reported separately, so the extended
thermal ledger must state that boundary explicitly rather than silently adding
legacy fan/compressor heat. Without local rack nodes or new units, the extended
balance must reduce to the existing balance.

For facility electrical metrics, count each new unit's electrical consumption
once as cooling power/energy, regardless of heat destination. It is not IT power.
Preserve existing PUE behavior for a zero IT denominator. Cooling capacity,
actual heat removal and electrical power remain distinct quantities.

Stage 4 extends `CoupledRoomThermalSnapshot` with explicit rack energies and
preserves its seven-argument compatibility constructor. `removedHeatJoules`
retains existing room cooling removal only. Local refrigeration is recorded
separately and is not itself removal from the combined rack-room system when
rejected back to the room.

### Integration and compatibility requirements

- The extension is opt-in. Existing configurations retain their thermal results
  and do not acquire guessed local air nodes or physical parameters.
- Initial support is limited to the coupled `UTILIZATION_DRIVEN` and
  `POWER_DRIVEN` pipeline, with explicit `roomAirTemperature` configuration.
- Reject a configuration declaring this feature with the legacy thermal
  pipeline or `TEMPERATURE_DRIVEN`; do not silently ignore it or overwrite
  observed temperatures.
- A dedicated rack unit has one authoritative control state. Unit electricity
  must participate in existing cooling/facility metrics without duplicate
  registration as a room SUPPLY or EXHAUST.
- Expose local rack air separately from representative internal server
  temperature and common room air. Existing rack server averages keep their
  current meaning.
- Later snapshots must identify rack and unit, publish local air temperature,
  actual cooling, electrical power, destination, signed rack-to-room exchange
  and integrated energies needed to audit the balance.
- The backend owns all numerical calculations. The UI issues commands and
  displays backend output.

### Acceptance scenarios for implementation

1. With the feature absent, existing configurations and thermal regressions
   retain their current behavior.
2. Two otherwise equal local-air racks with different cooling states show a
   local thermal difference; any effect on other racks occurs through room air.
3. With no existing room cooling or environment exchange, a `ROOM` unit
   conserves combined energy with input `E_IT + E_new_rack_cooling_electric`.
4. Under the same boundary, an `EXTERNAL` unit exports `Q_r + P_r` without
   directly adding rejection heat to room air.
5. Disabled units draw no electricity and remove no heat, while local air,
   offline-server heat release and passive room exchange continue evolving.
6. Cooling respects capacity/airflow limits and becomes zero below supply
   temperature; enabled zero-cooling electricity still reaches its destination.
7. Opposite rack/room temperature gradients produce correctly signed exchange.
   Empty racks and mixed local-air/direct-to-room layouts remain valid.
8. Integrated energy residuals remain within stated numerical tolerances across
   supported tick sizes. Rejected updates leave all thermal states unchanged;
   reset and replay are deterministic.

These checks establish implementation consistency, not physical calibration.


## Rack air thermal integration (stage 4)

`CoupledRoomTemperatureSystem` now optionally owns a local air temperature for
each configured rack. Only the servers installed in that exact column/rack pair
exchange heat with that local node. All remaining servers keep the original
connection directly to room air. Offline servers still exchange stored heat at
zero electrical input; empty racks retain their air capacity and cooling path.

The factory's original call installs configured rack controls automatically:

```java
CoupledRoomTemperatureSystem coupled = new CoupledRoomTemperatureSystemFactory()
        .create(definition, datacenter, temperatureSystem, roomCoolingSystem)
        .orElseThrow();

Optional<RackCoolingSystem> rackControls = coupled.rackCoolingSystem();
Map<RackLocation, Double> localAir = coupled.rackAirTemperaturesCelsius();
OptionalDouble rackAir = coupled.rackAirTemperatureCelsius(new RackLocation("C01", "R01"));
```

`roomCoolingSystem` is `Optional<CoolingSystem>` and may be empty. To share
controls with another application layer, use the factory overload accepting
`Optional<RackCoolingSystem>` as its final argument. The configuration must match
the definition. The input pipeline also checks rack configuration identity and
requires the coupled overload. `TEMPERATURE_DRIVEN` remains unsupported for
this feature.

The returned local temperature map is immutable and replaced only after a
successful tick. A missing rack returns an empty `OptionalDouble`, never a
server or common-room fallback. These are well-mixed local air temperatures,
not separate measured inlet/outlet temperatures. `reset()` restores initial
server, rack and room temperatures, both room and rack enabled states, and
clears last thermal/cooling captures. It does not reset IT workload/power systems.

### Numerical method

Keep the existing backward-Euler substeps of at most one second. For each
substep, eliminate server states with the existing `gamma_i` and `base_i`.
For a rack with conductance `G_r`, its reduced equation is:

```text
a_r = C_r / dt + G_r + sum(gamma_i for servers in rack r)
b_r = C_r / dt * T_r_old + sum(gamma_i * base_i for those servers)
a_r * T_r + Q_r(T_r) = b_r + G_r * T_room
```

`CoupledRackAirSolver` solves that capped piecewise-linear equation analytically
for each candidate room temperature. It substitutes the result into the room
energy balance, including `Q_r + P_r` for ROOM rejection, and solves the strictly
increasing residual by bracket expansion and bisection. Elimination is an
algebraic reduction of the simultaneous equations, not a sequential thermal
update. Local cooling and room cooling are evaluated at the new substep
temperatures and used consistently in both state changes and energy transfers.

Without rack nodes the original room solver path is retained. Rack enabled
states, IT power and airflow parameters are held constant during an external
tick. Extreme inputs that overflow or cannot satisfy the energy balance within
`1e-6 J + 1e-9 * energyScale` are rejected without publishing partial thermal
state. `energyScale` is the sum of absolute global input, storage-change and
boundary-output energies. This is an implementation tolerance, not calibration.

### Per-tick thermal ledger

`lastSnapshot()` retains the existing server/room ledger and adds:

| Accessor | Meaning, in joules over the tick |
| --- | --- |
| `rackAirEnergyChangeJoules()` | Signed aggregate local-air storage change |
| `rackCoolingElectricalEnergyJoules()` | Electricity consumed by enabled rack units |
| `rackRemovedHeatJoules()` | Heat extracted from local rack air, before selecting a rejection destination |
| `rackHeatRejectedToRoomJoules()` | Heat delivered to common room air: local cooling plus corresponding unit electricity |
| `rackHeatRejectedExternalJoules()` | Heat exported across the modeled boundary: local cooling plus corresponding unit electricity |
| `rackHeatTransferredToRoomJoules()` | Signed passive local-air-to-room exchange |

`inputElectricalEnergyJoules()` remains IT electricity only.
`heatTransferredToAirJoules()` remains signed server-to-air exchange, now summed
over both local rack air and direct room air. `removedHeatJoules()` remains
existing room cooling removal. The extended `energyBalanceResidualJoules()`
includes rack electricity, local air storage and external rack rejection; room
rejection and passive exchange are internal transfers and cancel globally.

Room SUPPLY/EXHAUST projections from `lastCoolingSnapshot()` retain their
existing room-only semantics. They do not include rack units. In particular,
room-only power/PUE aggregates must not be described as complete facility
metrics for new rack-cooling scenarios until the later electrical aggregation
stage. The thermal ledger already includes rack electricity independently.

Stage 4 integration tests cover both destinations, isolated analytical cases,
capacity- and airflow-limited cooling, disabled units, below-supply operation,
offline servers, signed passive exchange, mixed rack/direct-room servers,
combined room cooling and environment exchange, reset/replay, tick subdivision
and atomic rejection. This verifies implementation behavior, not measured
physical calibration.

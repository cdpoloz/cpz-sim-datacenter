# JSON Configuration

JSON configuration is loaded with `JsonDatacenterConfigLoader` and represented as
`DatacenterDefinition`. Before building the domain, `DatacenterFactory` runs
`DatacenterConfigValidator`.

Loading example:

```java
Path configPath = Path.of("data/config/demo-datacenter-medium.json");
DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(configPath);
Datacenter datacenter = new DatacenterFactory().create(definition);
```

## General Structure

```json
{
  "name": "Demo Datacenter",
  "layout": {
    "room": {
      "code": "ROOM-01",
      "name": "Sala Principal"
    },
    "racks": []
  },
  "serverModels": [],
  "servers": []
}
```

Main fields:

- `name`: non-blank datacenter name.
- `layout.room`: optional metadata for the room associated with the active layout.
- `layout.hotAisles`: optional hot-aisle to rack-column mapping for
  `TEMPERATURE_DRIVEN/AISLE`.
- `layout.racks`: available physical infrastructure.
- `serverModels`: server model catalog.
- `servers`: servers installed in specific racks and slots.
- `temperature`: optional thermal model configuration.
- `roomAirTemperature`: optional independent room-air thermal configuration;
  see [Room-air state preparation](#room-air-state-preparation).
- `health`: optional server health threshold configuration.
- `cooling`: optional cooling-system configuration.
- `dataInputMode`: optional authoritative input mode. Defaults to
  `UTILIZATION_DRIVEN`.
- `powerInputGranularity`: optional power input granularity for
  `POWER_DRIVEN`. Defaults to `SERVER`.
- `temperatureInputGranularity`: optional temperature input granularity for
  `TEMPERATURE_DRIVEN`. Defaults to `RACK`.

## Room-air state preparation

The optional top-level `roomAirTemperature` block prepares parameters and an
independent room-air state. It is separate from `layout.room` metadata and the
per-server `temperature` options.

```json
"roomAirTemperature": {
  "initialTemperatureCelsius": 24.0,
  "effectiveThermalCapacityJoulesPerCelsius": 2000000.0,
  "environmentTemperatureCelsius": 20.0,
  "environmentHeatTransferWattsPerCelsius": 0.0
}
```

These numbers illustrate the contract only; they are not calibrated defaults
or a recommendation for a real room. All four numeric fields are required when
the block is present. Temperatures must be finite, effective thermal capacity
must be finite and strictly positive, and environment heat transfer must be
finite and non-negative. Zero heat transfer represents an insulated boundary.
Unknown fields, explicit nulls, numeric strings and incomplete blocks are rejected.
The loader validates structure and finite numbers; `DatacenterConfigValidator`
validates physical ranges before runtime options are created.

Omitting the block preserves existing behavior and produces no room-air options.
`RoomAirTemperatureOptionsFactory.create(definition)` returns an `Optional`.
Consumers can initialize the separate state explicitly:

```java
Optional<RoomAirThermalState> roomAirState =
        new RoomAirTemperatureOptionsFactory()
                .create(definition)
                .map(RoomAirThermalState::new);
```

This standalone setup only constructs the room-air state; it does not register or
advance it. The standalone `RoomAirTemperatureSystem` can advance it explicitly:

```java
RoomAirTemperatureSystem roomAirSystem = new RoomAirTemperatureSystem(
        new RoomAirTemperatureOptionsFactory().create(definition).orElseThrow());
RoomAirTemperatureSnapshot airSnapshot = roomAirSystem.tick(
        tick,
        new RoomAirThermalInput(averageServerHeatTransferWatts, averageRemovedHeatWatts)
);
```

The two thermal powers are step-average transfers in watts, supplied explicitly
by the caller. Server-to-air transfer is signed; removed heat is non-negative.
Do not substitute server electrical consumption, HVAC electrical power or rated
cooling capacity without a consistent thermal coupling model. The system uses
the tick duration in seconds and the exact constant-input room energy balance.
It exposes its latest immutable snapshot separately and supports `reset()`.
See [Temperature model](temperature.md#explicit-room-air-energy-balance).

### Coupled input pipeline

For utilization- or power-driven simulations, construct the coupled system using
the configured room parameters and the same cooling system that receives unit
enable/disable commands:

```java
Optional<CoolingSystem> coolingSystem = new CoolingConfigurationFactory()
        .create(definition, datacenter).map(CoolingSystem::new);
Optional<CoupledRoomTemperatureSystem> coupled = new CoupledRoomTemperatureSystemFactory()
        .create(definition, datacenter, temperatureSystem, coolingSystem);

new DatacenterInputPipelineFactory().registerInputSystems(
        engine, definition, datacenter, temperatureSystem, temperatureOptions,
        workloadSource, serverPowerInputSource, coupled.orElse(null));
```

The new overload replaces the legacy temperature update when configured.
Omitting `roomAirTemperature` returns an empty optional and preserves the original
pipeline. The older pipeline overload rejects an enabled room configuration
rather than silently ignoring it. Configured cooling must match the supplied
runtime cooling system. `TEMPERATURE_DRIVEN` coupling is unsupported for now;
its existing unconfigured pipeline is unchanged.

With coupling enabled, register health and energy consumers afterward, but do not
also register a legacy cooling thermal adapter, `TemperatureSystem.update`, or
the standalone room-air system. The coupled solver owns thermal evolution;
`TemperatureSystem` is its server-state store. Cooling controls remain usable.
`coupled.lastSnapshot()` exposes room air and the energy ledger;
`coolingSystem.currentUnitSnapshots()` exposes electrical unit activity.
`coupled.lastCoolingSnapshot()` provides the end-of-tick mixed-room projection
without running the legacy cooling thermal update. Its zone inlet/exhaust fields
both contain the same global air temperature; they do not describe a spatial rise.

The history recorder overload accepts `coupled::lastSnapshot` as its final
supplier and the coupled cooling snapshot supplier. It stores the ledger on
`DatacenterSimulationStepSnapshot.coupledThermalSnapshot()` and the independent
air snapshot on `operationalSnapshot().roomAirSnapshot()`. Tick indices and elapsed
times must match. Numeric `roomAirTemperatureCelsius()` is NaN when air is absent;
the legacy server-temperature accessors retain their meaning. See the
[coupled model](temperature.md#coupled-server-room-and-cooling-model) for equations,
integration and boundary assumptions.

## dataInputMode

`dataInputMode` declares which external or simulated variable drives the
datacenter simulation. If omitted, the backend uses `UTILIZATION_DRIVEN`.

```json
{
  "name": "Demo Datacenter",
  "dataInputMode": "UTILIZATION_DRIVEN",
  "layout": {
    "racks": []
  },
  "serverModels": [],
  "servers": []
}
```

Allowed values:

- `UTILIZATION_DRIVEN`: utilization is supplied through `WorkloadSource`; the
  backend derives IT power and server temperature.
- `POWER_DRIVEN`: server power is supplied directly, for example from telemetry;
  utilization is inferred from power, and the backend derives temperature from
  that power.
- `TEMPERATURE_DRIVEN`: observed rack or hot-aisle temperature is supplied
  directly; rack/server temperature, power, and utilization are inferred from
  that observed temperature.

Rules:

- If `dataInputMode` is absent, `DatacenterDefinition.dataInputMode()` returns
  `UTILIZATION_DRIVEN`.
- If present, `dataInputMode` must be a non-null string matching one of the
  allowed enum names exactly.
- Existing JSON configurations remain compatible because the default mode
  preserves the historical utilization-driven behavior.
- Consumers should use `DatacenterInputPipelineFactory` to register input
  systems for the configured mode. It avoids duplicating mode-specific
  registration rules in UI or telemetry code.
- `TemperatureInputSourceFactory` is the backend factory that chooses
  `SimulatedRackTemperatureInputSource` for `RACK` or the hot-aisle adapter
  stack for `AISLE`.

## powerInputGranularity

`powerInputGranularity` selects the simulated power source granularity used by
`POWER_DRIVEN`.

Allowed values:

- `SERVER`: power is simulated directly per server with
  `NoiseServerPowerInputSource`.
- `RACK`: power is simulated as aggregate rack power with
  `NoiseRackPowerInputSource`, then adapted through
  `RackPowerToServerPowerInputSource`.

Rules:

- If `powerInputGranularity` is absent, `DatacenterDefinition.powerInputGranularity()`
  returns `SERVER`.
- If present, it must be a non-null string matching one of the allowed enum names
  exactly.
- The field only affects `POWER_DRIVEN`. `UTILIZATION_DRIVEN` keeps its normal
  workload-to-power behavior even if `powerInputGranularity` is present.
- `PowerInputSystem` always receives a `ServerPowerInputSource`, so it does not
  distinguish server-level input from rack-level input.

Server-level power-driven example:

```json
{
  "name": "Power Driven Server Example",
  "dataInputMode": "POWER_DRIVEN",
  "powerInputGranularity": "SERVER",
  "layout": {
    "racks": []
  },
  "serverModels": [],
  "servers": []
}
```

Rack-level power-driven example:

```json
{
  "name": "Power Driven Rack Example",
  "dataInputMode": "POWER_DRIVEN",
  "powerInputGranularity": "RACK",
  "layout": {
    "racks": []
  },
  "serverModels": [],
  "servers": []
}
```

Rack-level policy:

- `NoiseRackPowerInputSource` considers installed online servers only.
- Rack power is calculated between aggregate idle and aggregate maximum power.
- The activity range depends on the dominant online server role in the rack.
- `RackPowerToServerPowerInputSource` distributes rack power to online servers.
- If rack power exceeds online server physical capacity, per-server maximum
  clamps apply and the distributed sum may not conserve the original rack power.

## temperatureInputGranularity

`temperatureInputGranularity` selects the observed temperature granularity used
by `TEMPERATURE_DRIVEN`.

Allowed values:

- `RACK`: temperature is supplied per rack through `RackTemperatureInputSource`
  and applied by `RackTemperatureInputSystem`.
- `AISLE`: temperature is supplied per hot aisle through
  `AisleTemperatureInputSource`, adapted to rack observations through
  `AisleTemperatureToRackTemperatureInputSource`, and then applied by
  `RackTemperatureInputSystem`.
- Backend rack-temperature sources include fixed/manual values via
  `MapRackTemperatureInputSource` and smooth deterministic simulated values via
  `SimulatedRackTemperatureInputSource`.
- Backend aisle-temperature sources include fixed/manual values via
  `MapAisleTemperatureInputSource` and smooth deterministic simulated values via
  `SimulatedAisleTemperatureInputSource`.

Rules:

- If `temperatureInputGranularity` is absent,
  `DatacenterDefinition.temperatureInputGranularity()` returns `RACK`.
- If present, it must be a non-null string matching an allowed enum name exactly.
- The field only affects `TEMPERATURE_DRIVEN`; consumers should not interpret it
  without also checking `dataInputMode`.
- `TEMPERATURE_DRIVEN/SERVER` and `TEMPERATURE_DRIVEN/ROOM` are not implemented.
- `TEMPERATURE_DRIVEN/AISLE` means hot aisle, not cold aisle.
- When `layout.hotAisles` is present, `TemperatureInputSourceFactory` uses that
  configuration to resolve each rack column to a hot-aisle code.
- When `layout.hotAisles` is absent, the factory falls back to
  `StandardHotAisleCodeResolver` for compatibility with the standard/demo
  layout only.

Standard/demo fallback hot-aisle mapping:

```text
C01       -> HA01
C02 / C03 -> HA02
C04 / C05 -> HA03
C06 / C07 -> HA04
C08       -> HA05
```

Columns sharing a hot aisle receive the same base observed temperature for the
same tick. Per-rack differences within one hot aisle are future work requiring
gradients, multiple sensors, localized recirculation, or another more detailed
thermal model. Arbitrary layouts should declare `layout.hotAisles`.

Rack-level temperature-driven example:

```json
{
  "name": "Temperature Driven Rack Example",
  "dataInputMode": "TEMPERATURE_DRIVEN",
  "temperatureInputGranularity": "RACK",
  "layout": {
    "racks": []
  },
  "serverModels": [],
  "servers": []
}
```

Hot-aisle-level temperature-driven example:

```json
{
  "name": "Temperature Driven Hot Aisle Example",
  "dataInputMode": "TEMPERATURE_DRIVEN",
  "temperatureInputGranularity": "AISLE",
  "layout": {
    "racks": []
  },
  "serverModels": [],
  "servers": []
}
```

Rack-level inference policy:

- `RackTemperatureInputSystem` considers installed online servers only.
- The observed rack temperature is written as the current temperature of online
  servers in that rack.
- In aisle-level mode, the observed hot-aisle temperature is first resolved to a
  rack temperature using `layout.hotAisles` when configured, or the
  standard/demo fallback resolver when that configuration is absent.
- Power is inferred from the clamped ratio between ambient temperature and a
  maximum reference temperature.
- The default maximum reference temperature is `85.0 C`. It is an inference
  reference for normalizing that ratio, not a universal health threshold and not
  a replacement for configured health hysteresis thresholds.
- Inferred power is clamped to each server's `idlePowerWatts..maxPowerWatts`
  range.
- Utilization is inferred from power through the same server helper used by
  `POWER_DRIVEN`.
- Offline servers keep zero power and zero utilization.
- In `TEMPERATURE_DRIVEN`, `RackTemperatureInputSystem` writes observed or
  adapted temperature into `TemperatureSystem`. Do not register
  `TemperatureSystem` afterward as a simulatable in the same tick, or it will
  recalculate temperature from power and overwrite the observed input.

## layout.room

`layout.room` is optional metadata that identifies the room associated with the
single active `layout` loaded by `DatacenterDefinition`. It does not change
layout selection, server placement, or simulation behavior.

Minimal example:

```json
{
  "name": "Complete 8x12 UI Test Datacenter",
  "layout": {
    "room": {
      "code": "ROOM-01",
      "name": "Sala Principal"
    },
    "racks": []
  },
  "serverModels": [],
  "servers": []
}
```

Rules:

- If `layout.room` is absent, `DatacenterDefinition.layout().room()` is `null`.
- If `layout.room` is present, it cannot be `null`.
- `layout.room.code` and `layout.room.name` are required when the block is present.

## layout.hotAisles

`layout.hotAisles` is optional for compatibility. When present, each entry
declares one hot aisle code and the rack columns that share that hot aisle. In
`TEMPERATURE_DRIVEN/AISLE`, aisle temperature input is interpreted as observed
hot-aisle temperature; the backend uses this mapping to adapt each rack to the
hot-aisle observation for its column.

Example:

```json
{
  "layout": {
    "hotAisles": [
      { "code": "HA01", "columns": ["C01"] },
      { "code": "HA02", "columns": ["C02", "C03"] },
      { "code": "HA03", "columns": ["C04", "C05"] },
      { "code": "HA04", "columns": ["C06", "C07"] },
      { "code": "HA05", "columns": ["C08"] }
    ],
    "racks": []
  }
}
```

Rules:

- If `layout.hotAisles` is absent, `DatacenterDefinition.layout().hotAisles()`
  is `null` and `TEMPERATURE_DRIVEN/AISLE` uses the standard/demo fallback.
- If present, the list cannot contain `null` entries.
- Each hot aisle `code` must be non-null and non-blank.
- Each `columns` list must be non-null and non-empty.
- Column codes cannot be null or blank.
- Hot aisle codes cannot be duplicated.
- A column cannot belong to more than one hot aisle.
- Declared columns must exist in `layout.racks`.

## layout.racks

Each rack declares its physical slots using exactly one of two mutually exclusive
formats.

Legacy `slotCount` format:

```json
{
  "code": "RACK-A01-R01",
  "column": "A01",
  "row": "R01",
  "slotCount": 42
}
```

This generates ordered slot codes `U01`, `U02`, ..., `U42`. The format is kept for
compatibility and convenience.

Explicit `slots` format:

```json
{
  "code": "RACK-A01-R01",
  "column": "A01",
  "row": "R01",
  "slots": [
    "S01",
    "S02",
    "S03"
  ]
}
```

The `slots` array order is the physical or visual order inside the rack. Slot codes
are opaque strings. The backend does not require `U`, does not parse the numeric
part, and does not infer position from the text. Valid examples include `U01`,
`S01`, `GPU-A`, `NETWORK`, `LOWER-B`, and `SPARE`.

Rules:

- `code`, `column`, and `row` cannot be null or blank.
- `code` must be unique within a column.
- Each rack must define exactly one of `slotCount` or `slots`.
- If `slotCount` is used, it must be greater than `0` and generates legacy `Uxx` codes.
- If `slots` is used, the list cannot be null or empty.
- Explicit slot codes cannot be null or blank.
- Explicit slot codes must be unique inside the same rack.
- Slot comparisons are exact and case-sensitive.
- Different racks may reuse the same slot code because full server identity is `column + rackCode + slot`.
- A `rackCode` may be reused in different columns.
- Within one column, `rackCode` must be unique.
- A rack may have no associated servers; that represents empty physical infrastructure.

## serverModels

Each model is defined as:

```json
{
  "modelCode": "SRV-DEMO-001",
  "manufacturer": "CPZ",
  "model": "Demo Server",
  "idlePowerWatts": 100.0,
  "maxPowerWatts": 300.0
}
```

Thermal properties may be declared for a model that needs behavior different
from the global temperature configuration:

```json
{
  "modelCode": "SRV-AI-001",
  "manufacturer": "CPZ",
  "model": "AI Accelerator Server",
  "idlePowerWatts": 150.0,
  "maxPowerWatts": 500.0,
  "thermalCapacityJoulesPerCelsius": 9000.0,
  "heatDissipationWattsPerCelsius": 14.0
}
```

Rules:

- `modelCode`, `manufacturer`, and `model` cannot be null or blank.
- `modelCode` must be unique.
- `idlePowerWatts` must be finite and `>= 0`.
- `maxPowerWatts` must be finite and greater than `idlePowerWatts`.
- `thermalCapacityJoulesPerCelsius` and
  `heatDissipationWattsPerCelsius` are optional only as a pair: both must be
  present or both must be absent.
- When present, both thermal values must be JSON numbers, finite, and `> 0`.
- Explicit `null` is invalid for either thermal field.
- When both fields are absent, `ServerConfig.thermalProperties()` is `null` and
  `TemperatureSystem` uses the global values from `TemperatureSystemOptions`.
- Existing JSON files without these fields remain valid and retain their
  previous global thermal behavior.

These values belong to the reusable server model and are independent of
`ServerRole`. Assigning role `AI`, `GPU`, or any other role does not select
thermal physics.

## servers

Each installed server is defined as:

```json
{
  "column": "C01",
  "rackCode": "RACK-A01-R01",
  "slot": "S01",
  "modelCode": "SRV-DEMO-001",
  "status": "OK",
  "role": "AI",
  "workloadFactor": 1.5
}
```

Fields:

- `column`: column containing the rack. Recommended for all new configurations.
- `rackCode`: code of an existing rack in `layout.racks`.
- `slot`: exact slot code declared by the referenced rack.
- `modelCode`: code of an existing model in `serverModels`.
- `status`: initial `HardwareStatus` value: `OK`, `ALERT`, or `OFFLINE`.
- `role`: optional primary functional `ServerRole`.
- `workloadFactor`: non-negative factor used to scale workload per server.

Rules:

- `column`, if present, cannot be blank.
- `rackCode`, `slot`, `modelCode`, and `status` cannot be null or blank.
- If `column` is present, `column + rackCode` must identify an existing rack.
- If `column` is omitted, `rackCode` must identify exactly one rack in the datacenter.
- `slot` must exist in the referenced rack's effective slot list.
- Two servers cannot share the same location `column + "/" + rackCode + "/" + slot`.
- `status` must exactly match the enum name.
- If present, `role` must be a non-null JSON string that exactly matches a
  `ServerRole` name. Matching is case-sensitive.
- `workloadFactor` must be finite and `>= 0`.
- If `ServerDefinition` is built from Java using the constructor without `workloadFactor`, the default value is `1.0f`.

Allowed `role` values are:

- `GENERAL_PURPOSE`
- `AI`
- `STORAGE`
- `DATABASE`
- `EDGE`
- `GPU`
- `MANAGEMENT`

When `role` is absent, `ServerDefinition.role()` remains `null` because the
definition represents exactly what was declared. `DatacenterFactory` normalizes
that absence to `ServerRole.GENERAL_PURPOSE`, so `Server.getRole()` never returns
`null` for a successfully built server. An explicit `role: null`, a non-textual
value, or an unknown value prevents the JSON configuration from loading.

`AI` means that training or inference of artificial intelligence is the server's
primary function. `GPU` means GPU-accelerated compute whose primary function is
not specifically classified as AI. Each server has one primary role in this
version.

The JSON status is used when constructing the server. Once
`ServerHealthSystem` runs, a non-`OFFLINE` status can evolve between `OK` and
`ALERT` based on current utilization and temperature. An initial `ALERT` is
therefore reevaluated like any other operational status. `OFFLINE` always has
priority and is never overwritten by the health system.

## workloadFactor

`DatacenterFactory` creates the datacenter, but it does not apply `workloadFactor`
by itself. To use factors from JSON, create a `ServerWorkloadFactorProvider` with
`WorkloadFactorProviderFactory` and wrap the base source with `ScaledWorkloadSource`.

```java
FractalNoise fractalNoise = new FractalNoise(
        new PerlinNoise(1234L),
        5,
        1.0f,
        2.0f,
        0.5f
);
WorkloadSource base = new NoiseWorkloadSource(fractalNoise, 0.001, 0.2f, 0.9f);
ServerWorkloadFactorProvider factors =
        new WorkloadFactorProviderFactory().create(definition);
WorkloadSource workload = new ScaledWorkloadSource(base, factors);
```

`workloadFactor` can be greater than `1.0`. `ScaledWorkloadSource` multiplies the
base utilization by the factor and clamps the result to `[0, 1]`.

## Small Complete Example

```json
{
  "name": "Small Demo Datacenter",
  "layout": {
    "racks": [
      {
        "code": "RACK-A01-R01",
        "column": "A01",
        "row": "R01",
        "slotCount": 42
      },
      {
        "code": "RACK-A01-R02",
        "column": "A01",
        "row": "R02",
        "slots": [
          "S01",
          "S02",
          "SPARE"
        ]
      }
    ]
  },
  "serverModels": [
    {
      "modelCode": "SRV-DEMO-001",
      "manufacturer": "CPZ",
      "model": "Demo Compute Server",
      "idlePowerWatts": 100.0,
      "maxPowerWatts": 300.0
    }
  ],
  "servers": [
      {
        "column": "A01",
        "rackCode": "RACK-A01-R01",
      "slot": "U01",
      "modelCode": "SRV-DEMO-001",
      "status": "OK",
      "workloadFactor": 1.5
    },
      {
        "column": "A01",
        "rackCode": "RACK-A01-R01",
      "slot": "U02",
      "modelCode": "SRV-DEMO-001",
      "status": "OFFLINE",
      "workloadFactor": 1.0
    }
  ]
}
```

In this example `RACK-A01-R02` is an empty rack. The server in `U02` is installed
but `OFFLINE`; after running `WorkloadSystem` and `PowerConsumptionSystem`, it must
end up with `0.0f` utilization and `0.0f` power.

An empty slot is different from an `OFFLINE` server:

- A declared slot with no server entry is empty physical capacity.
- A server with `HardwareStatus.OFFLINE` is installed but powered off or not operational.
- `EMPTY` is not a `HardwareStatus` value.

## Location Lookup from Java

Use backend APIs rather than assembling visual state manually:

```java
List<Server> servers = datacenter.getServers("C01", "R01");

Optional<Server> server =
        datacenter.getServer("C01", "R01", "S03");

Rack rack = datacenter.findRack("C01", "R01").orElseThrow();

for (String slot : rack.getSlotCodes()) {
    Optional<Server> installed =
            datacenter.getServer("C01", "R01", slot);

    if (installed.isEmpty()) {
        // Empty physical slot
    }
}
```

## Temperature Configuration

An optional `temperature` block can be added to the top-level JSON definition.
If it is omitted, consumers can use `TemperatureSystemOptions.defaults()`.

Current shape:

```json
{
  "temperature": {
    "ambientTemperatureCelsius": 24.0,
    "defaultInitialTemperatureCelsius": 30.0,
    "thermalCapacityJoulesPerCelsius": 5000.0,
    "heatDissipationWattsPerCelsius": 8.0
  }
}
```

Rules:

- the whole `temperature` block is optional
- if the block is present, all four fields are currently required
- `ambientTemperatureCelsius` must be finite
- `defaultInitialTemperatureCelsius` must be finite
- `thermalCapacityJoulesPerCelsius` must be finite and `> 0`
- `heatDissipationWattsPerCelsius` must be finite and `>= 0`

This configuration feeds the simplified internal server temperature model only.
By itself it does not add room temperature, rack inlet, or airflow behavior,
and it does not replace the separate top-level `cooling` block.
For each server, `TemperatureSystem` resolves the thermal capacity and heat
dissipation with this precedence:

1. the pair declared by the server's referenced `serverModels` entry
2. these global `temperature` values

`ambientTemperatureCelsius` and `defaultInitialTemperatureCelsius` are always
global. A missing top-level `temperature` block uses
`TemperatureSystemOptions.defaults()`, including when a server model overrides
the two model-specific properties.

## Cooling Configuration

An optional top-level `cooling` block configures cooling zones, cooling units,
and physical options. If it is omitted, JSON loading and datacenter
construction remain compatible, and
`CoolingConfigurationFactory.create(definition, datacenter)` returns
`Optional.empty()`.

Current shape:

```json
{
  "cooling": {
    "zones": [
      {
        "code": "ZONE-C01",
        "columns": ["C01"],
        "rackCodes": ["R01", "R02"]
      },
      {
        "code": "ZONE-C02",
        "columns": ["C02"],
        "rackCodes": ["R01"]
      }
    ],
    "supplyUnits": [
      {
        "code": "SUPPLY-01",
        "ratedAirflowCubicMetersPerSecond": 8.0,
        "ratedCoolingCapacityWatts": 100000.0,
        "supplyAirTemperatureCelsius": 18.0,
        "influences": [
          { "zoneCode": "ZONE-C01", "weight": 0.75 },
          { "zoneCode": "ZONE-C02", "weight": 0.25 }
        ],
        "initiallyEnabled": true
      }
    ],
    "exhaustUnits": [
      {
        "code": "EXHAUST-01",
        "ratedAirflowCubicMetersPerSecond": 6.0,
        "influences": [
          { "zoneCode": "ZONE-C01", "weight": 0.50 },
          { "zoneCode": "ZONE-C02", "weight": 0.50 }
        ],
        "initiallyEnabled": false
      }
    ],
    "options": {
      "airDensityKilogramsPerCubicMeter": 1.204,
      "airSpecificHeatJoulesPerKilogramKelvin": 1005.0,
      "initialInletAirTemperatureCelsius": 24.0,
      "maximumRecirculationFraction": 0.95,
      "residualRecirculationFraction": 0.1,
      "effectiveZoneAirVolumeCubicMeters": 1000.0,
      "recirculationResponseTimeSeconds": 300.0
    }
  }
}
```

### cooling.zones

Each zone is defined as:

```json
{
  "code": "ZONE-C01",
  "columns": ["C01"],
  "rackCodes": ["R01", "R02"]
}
```

Fields:

- `code`: unique cooling-zone code.
- `columns`: datacenter columns included in the zone.
- `rackCodes`: rack codes included in the zone.

Rules:

- the whole `zones` list is required when `cooling` is present
- `zones` cannot be null or empty
- zone entries cannot be null
- `code` cannot be null, blank, or duplicated
- `columns` cannot be null or empty
- `rackCodes` cannot be null or empty
- `columns` values must be non-blank, unique, and reference existing layout columns
- `rackCodes` values must be non-blank and unique
- every `column + rackCode` combination declared by the zone must reference an existing rack
- `rackCodes` uses the rack `code`, not the rack `row`
- runtime membership is built from installed servers whose location matches one
  configured column and one configured rack code
- a valid zone must contain at least one installed server
- one installed server location cannot belong to more than one cooling zone

### cooling.supplyUnits

Each supply unit is defined as:

```json
{
  "code": "SUPPLY-01",
  "ratedAirflowCubicMetersPerSecond": 8.0,
  "ratedCoolingCapacityWatts": 100000.0,
  "supplyAirTemperatureCelsius": 18.0,
  "influences": [
    { "zoneCode": "ZONE-C01", "weight": 0.75 },
    { "zoneCode": "ZONE-C02", "weight": 0.25 }
  ],
  "initiallyEnabled": true
}
```

Rules:

- the `supplyUnits` list is required when `cooling` is present
- the list may be empty only if `exhaustUnits` is not empty
- unit entries cannot be null
- `code` cannot be null, blank, or duplicated
- `ratedAirflowCubicMetersPerSecond` must be finite and `> 0`
- `ratedCoolingCapacityWatts` must be finite and `> 0`
- `supplyAirTemperatureCelsius` must be finite
- `influences` cannot be null or empty
- `initiallyEnabled` is required and is a JSON boolean

### cooling.exhaustUnits

Each exhaust unit is defined as:

```json
{
  "code": "EXHAUST-01",
  "ratedAirflowCubicMetersPerSecond": 6.0,
  "influences": [
    { "zoneCode": "ZONE-C01", "weight": 0.50 },
    { "zoneCode": "ZONE-C02", "weight": 0.50 }
  ],
  "initiallyEnabled": false
}
```

Rules:

- the `exhaustUnits` list is required when `cooling` is present
- the list may be empty only if `supplyUnits` is not empty
- unit entries cannot be null
- `code` cannot be null, blank, or duplicated
- `ratedAirflowCubicMetersPerSecond` must be finite and `> 0`
- `influences` cannot be null or empty
- `initiallyEnabled` is required and is a JSON boolean
- exhaust units do not declare cooling capacity or supply-air temperature

### cooling unit influences

Each influence is defined as:

```json
{
  "zoneCode": "ZONE-C01",
  "weight": 0.75
}
```

Rules:

- influence entries cannot be null
- `zoneCode` must be non-blank
- `zoneCode` must reference a known zone inside the same `cooling.zones` list
- one unit cannot repeat the same `zoneCode`
- `weight` must be finite and `> 0`
- for each unit, the sum of all influence weights must be exactly `1.0`

### cooling.options

`options` is required when `cooling` is present:

```json
{
  "airDensityKilogramsPerCubicMeter": 1.204,
  "airSpecificHeatJoulesPerKilogramKelvin": 1005.0,
  "initialInletAirTemperatureCelsius": 24.0,
  "maximumRecirculationFraction": 0.95,
  "residualRecirculationFraction": 0.1,
  "effectiveZoneAirVolumeCubicMeters": 1000.0,
  "recirculationResponseTimeSeconds": 300.0
}
```

Rules:

- `options` cannot be null
- all seven fields are currently required when the block is present
- `airDensityKilogramsPerCubicMeter` must be finite and `> 0`
- `airSpecificHeatJoulesPerKilogramKelvin` must be finite and `> 0`
- `initialInletAirTemperatureCelsius` must be finite
- `maximumRecirculationFraction` must be finite and within `[0.0, 1.0]`
- `residualRecirculationFraction` must be finite and within
  `[0.0, maximumRecirculationFraction]`
- `effectiveZoneAirVolumeCubicMeters` must be finite and `> 0`
- `recirculationResponseTimeSeconds` must be finite and `> 0`

This block becomes `CoolingSystemOptions` at runtime. The backend does not add
JSON defaults during loading; values are taken from the configuration exactly as
declared. The separate Java helper `CoolingSystemOptions.defaults()` is useful
for programmatic construction, but it is not applied automatically to a partial
or missing JSON `cooling.options` block.

## Server Health Configuration

An optional top-level `health` block configures the utilization and temperature
limits used by `ServerHealthSystem`:

```json
{
  "health": {
    "utilization": {
      "alertAtOrAbove": 0.90,
      "clearAtOrBelow": 0.85
    },
    "temperatureCelsius": {
      "alertAtOrAbove": 80.0,
      "clearAtOrBelow": 75.0
    }
  }
}
```

For both monitored values:

- `alertAtOrAbove` activates the corresponding alert when the current value is
  greater than or equal to this limit.
- `clearAtOrBelow` clears an active alert when the current value is less than or
  equal to this limit.
- While the value is strictly between both limits, the previous condition state
  is retained. This hysteresis prevents status oscillation near a single
  threshold.

`utilization` controls `HIGH_UTILIZATION` and uses the server's current
utilization. Both limits must be finite and within `[0, 1]`.

`temperatureCelsius` controls `HIGH_TEMPERATURE` and uses the representative
internal server temperature from `TemperatureSystem`. Both limits must be
finite.

For each threshold, `clearAtOrBelow` must be strictly less than
`alertAtOrAbove`. The whole `health` block is optional. If it is absent,
`ServerHealthOptionsFactory` returns `ServerHealthOptions.defaults()`, whose
limits are:

- utilization: alert at `0.90`, clear at `0.85`
- temperature: alert at `80.0 °C`, clear at `75.0 °C`

If `health` is present, both `utilization` and `temperatureCelsius` are required,
as are both fields inside each threshold. A JSON `null` health block is rejected.
These values configure runtime behavior; the health system does not use separate
hard-coded evaluation limits.


## Rack cooling configuration (stage 2)

An optional top-level `rackCooling` block defines dedicated rack units and their
local air nodes. It is independent of the legacy `cooling` block: a scenario may
have rack units without room cooling zones or room SUPPLY/EXHAUST units.

**Current availability:** the loader and validator accept the contract below.
`DatacenterFactory.create` and `CoupledRoomTemperatureSystemFactory.create`
deliberately throw `UnsupportedOperationException`
when `rackCooling` is declared, including when units are initially disabled.
Stage 3 provides standalone equipment definitions, controls and electrical power
through `RackCoolingConfigurationFactory` and `RackCoolingSystem`. Coupled thermal
evolution and thermal/history snapshots arrive in later stages; running a full
scenario now must not silently ignore the new cooling configuration.
Existing scenarios without `rackCooling` continue to build normally.

The following is a fragment to add to an otherwise complete datacenter definition.
The referenced `C01/R01` rack must already exist in `layout.racks`. All numeric
values are illustrative, not physically calibrated.

```json
{
  "roomAirTemperature": {
    "initialTemperatureCelsius": 24.0,
    "effectiveThermalCapacityJoulesPerCelsius": 2000000.0,
    "environmentTemperatureCelsius": 20.0,
    "environmentHeatTransferWattsPerCelsius": 0.0
  },
  "rackCooling": {
    "airDensityKilogramsPerCubicMeter": 1.204,
    "airSpecificHeatJoulesPerKilogramKelvin": 1005.0,
    "units": [
      {
        "code": "RACK-COOLING-01",
        "column": "C01",
        "rackCode": "R01",
        "initialAirTemperatureCelsius": 24.0,
        "effectiveAirThermalCapacityJoulesPerCelsius": 10000.0,
        "roomHeatTransferWattsPerCelsius": 50.0,
        "ratedAirflowCubicMetersPerSecond": 0.1,
        "ratedCoolingCapacityWatts": 2000.0,
        "supplyAirTemperatureCelsius": 18.0,
        "ratedElectricalPowerWatts": 500.0,
        "heatRejectionDestination": "ROOM",
        "initiallyEnabled": true
      }
    ]
  }
}
```

Both air constants are required, finite and strictly positive. They apply to
internal rack unit airflow, independently of room `cooling.options`; no defaults
are inferred from the room configuration. `units` must be a non-empty array.
To omit the feature, omit `rackCooling` entirely. An explicit null block is invalid.

Every unit requires all fields shown above:

| Fields | Validation / meaning |
| --- | --- |
| `code` | Non-blank, unique across all rack, SUPPLY and EXHAUST units |
| `column`, `rackCode` | Non-blank; must identify an existing rack as a pair |
| `initialAirTemperatureCelsius`, `supplyAirTemperatureCelsius` | Finite numbers |
| `effectiveAirThermalCapacityJoulesPerCelsius` | Finite and strictly positive; excludes separately modeled server capacity |
| `roomHeatTransferWattsPerCelsius` | Finite and non-negative; zero is ideal thermal isolation |
| `ratedAirflowCubicMetersPerSecond` | Finite and strictly positive; internal recirculating airflow |
| `ratedCoolingCapacityWatts` | Finite and strictly positive; thermal capacity, not electrical power |
| `ratedElectricalPowerWatts` | Finite and strictly positive; future enabled-state electrical consumption |
| `heatRejectionDestination` | Exactly `ROOM` or `EXTERNAL`, explicitly supplied |
| `initiallyEnabled` | Explicit JSON boolean |

Only one unit may serve each rack in the first version. Repeated rack codes in
different columns remain distinct. Empty racks may declare a unit; unlike a legacy
cooling zone, no installed server is required. Slot occupancy is not reserved by
this thermal configuration.

`ROOM` assigns rejected heat (actual cooling plus unit electrical power) to the
common room air. `EXTERNAL` exports that heat across the modeled boundary. Neither
option removes electrical consumption from facility metrics. The first thermal
approximation assigns all unit electrical dissipation to the selected destination;
internal fan heat is not split out. See the
[thermal design contract](temperature.md#rack-contained-cooling-implementation-contract-stage-1)
for equations and energy accounting.

A declared block requires `roomAirTemperature` and `UTILIZATION_DRIVEN` (the
existing default) or `POWER_DRIVEN`. `TEMPERATURE_DRIVEN` is rejected even if the
units are initially disabled. Existing constructors of `DatacenterDefinition`
remain available and leave `rackCooling` absent. Programmatic definitions are
checked by `DatacenterConfigValidator`, including null entries and non-finite
magnitudes.

The loader rejects missing fields, explicit null fields, unknown properties
inside this block or its units, wrong JSON types, numeric/boolean coercion and
non-finite numeric values. Semantic checks (positive magnitudes, rack references,
uniqueness and mode dependencies) remain the validator's responsibility.

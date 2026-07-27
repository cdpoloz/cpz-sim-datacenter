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
    "racks": []
  },
  "serverModels": [],
  "servers": []
}
```

Main fields:

- `name`: non-blank datacenter name.
- `layout.racks`: available physical infrastructure.
- `serverModels`: server model catalog.
- `servers`: servers installed in specific racks and slots.
- `temperature`: optional thermal model configuration.
- `health`: optional server health threshold configuration.

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
It does not enable room temperature, rack inlet, cooling, or airflow modeling.
For each server, `TemperatureSystem` resolves the thermal capacity and heat
dissipation with this precedence:

1. the pair declared by the server's referenced `serverModels` entry
2. these global `temperature` values

`ambientTemperatureCelsius` and `defaultInitialTemperatureCelsius` are always
global. A missing top-level `temperature` block uses
`TemperatureSystemOptions.defaults()`, including when a server model overrides
the two model-specific properties.

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

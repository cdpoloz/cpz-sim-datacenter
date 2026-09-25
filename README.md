# CPZ SIM Datacenter

![Java](https://img.shields.io/badge/Java-26+-orange)
![Status](https://img.shields.io/badge/status-active-brightgreen)
![License](https://img.shields.io/badge/license-Apache--2.0-lightgrey)
[![GitHub](https://img.shields.io/badge/GitHub-cdpoloz-181717?logo=github)](https://github.com/cdpoloz)

`cpz-sim-datacenter` is a pure Java backend for simulating server workload, IT
power, simplified server temperature, server health, accumulated energy
consumption, and a simplified cooling model for a datacenter. It is an
independent Maven library intended to be consumed by other applications, for
example a UI such as `sim-datacenter-ui`.

It does not include Processing, a graphical UI, client-specific logic, or a
full room-level HVAC model. The API is preliminary and may change before the
final `0.1.0` release.

---

## Current Status

Current version:

```xml
<groupId>com.cpz.sim</groupId>
<artifactId>cpz-sim-datacenter</artifactId>
<version>0.1.0-alpha.1</version>
```

Available in `0.1.0-alpha.1`:

- JSON-configurable datacenter definitions with `layout.racks`, `serverModels` and `servers`.
- Optional top-level JSON blocks for `temperature`, `health`, `cooling`, and `dataInputMode`.
- Physical layout with existing racks, ordered slot codes, and empty racks.
- Servers installed by `column`, `rackCode`, and `slot`.
- Static per-server functional roles exposed through `Server#getRole()`.
- Hardware states: `OK`, `ALERT`, `OFFLINE`, with automatic health evaluation from utilization and temperature.
- Simulation systems: `WorkloadSystem`, `PowerConsumptionSystem`, `TemperatureSystem`, `ServerHealthSystem`, and `EnergyConsumptionSystem`.
- Cooling runtime built from JSON through `CoolingConfigurationFactory`, executed by `CoolingSystem`, and exposed through `CoolingSnapshot`.
- Cooling zones resolved from installed server locations using `columns` plus `rackCodes`.
- `SUPPLY` and `EXHAUST` cooling units with weighted influences, initial enabled state, and mutable runtime control.
- Workload strategy through `WorkloadSource`, with noise-based and scaled workloads.
- Data input modes through `DatacenterDataInputMode`: `UTILIZATION_DRIVEN`
  uses utilization as the authoritative input, `POWER_DRIVEN` uses electrical
  power as the authoritative input with server or rack granularity, and
  `TEMPERATURE_DRIVEN` uses observed rack or hot-aisle temperature as the
  authoritative input.
- Integration with `FractalNoise` from `cpz-utils` for variable workloads.
- Per-server `workloadFactor` read from JSON and applied through `ScaledWorkloadSource`.
- Energy snapshots through `EnergyConsumptionSnapshotProvider`, `EnergyConsumptionSnapshot` and `ServerEnergySnapshot`.
- Temperature snapshots through `TemperatureSnapshotProvider`, `TemperatureSnapshot`, and `ServerTemperatureSnapshot`.
- Optional model-specific thermal capacity and heat dissipation in `serverModels`, with global fallback.
- Health snapshots through `HealthSnapshotProvider`, `HealthSnapshot`, and `ServerHealthSnapshot`.

Important rules:

- An empty rack represents physical infrastructure with no installed server.
- An `OFFLINE` server represents an installed server that is powered off or not operational.
- Rack identity is `column + rackCode`; server identity is `column + rackCode + slot`.
- Cooling zone membership is resolved from installed servers whose locations match one configured `column` and one configured `rackCode`.
- A missing JSON `role` is normalized to `ServerRole.GENERAL_PURPOSE` when the domain is built.
- Model-specific thermal capacity and heat dissipation must be declared together.
  When absent, the global `TemperatureSystemOptions` values are used. These
  properties are independent of `ServerRole`.
- Slot codes are opaque identifiers declared by each rack. A UI should read rack slots from the backend and match servers by exact `column + rackCode + slot`.
- `WorkloadSystem` forces `utilization = 0.0f` for `OFFLINE` servers and does not query the `WorkloadSource` for them.
- `Server.updatePowerConsumption()` forces `currentPowerWatts = 0.0f` for `OFFLINE` servers.
- The JSON `status` is the initial hardware status. On every tick,
  `ServerHealthSystem` recalculates non-`OFFLINE` status as `OK` or `ALERT` from
  current utilization and temperature using configured thresholds.
- `OFFLINE` has priority and is never overwritten by `ServerHealthSystem`.
- `workloadFactor` can be greater than `1.0`; the final utilization produced by `ScaledWorkloadSource` is clamped to `[0, 1]`.
- If the top-level JSON omits `cooling`, `CoolingConfigurationFactory.create(...)` returns `Optional.empty()`.
- If the top-level JSON omits `dataInputMode`, the definition defaults to
  `UTILIZATION_DRIVEN`.

---

## Requirements

- Java 26.
- Maven.
- `cpz-sim-foundation` version `0.1.0-alpha.1`, installed locally or available from a configured Maven repository.
- `cpz-utils` version `0.2.3`, resolved by Maven.
- Jackson Databind, resolved by Maven.

---

## Local Build

Run tests:

```bash
mvn clean test
```

Install the library in the local Maven repository:

```bash
mvn clean install
```

If `cpz-sim-foundation` is not published in a reachable repository, install it in
the local Maven repository before building this project.

---

## Quick Usage

Maven dependency for a consumer such as `sim-datacenter-ui`:

```xml
<dependency>
    <groupId>com.cpz.sim</groupId>
    <artifactId>cpz-sim-datacenter</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

Recommended local order when all projects are under development:

1. Install `cpz-sim-foundation`.
2. Install `cpz-sim-datacenter`.
3. Build or run `sim-datacenter-ui`.

---

## Minimal JSON Configuration

```json
{
  "name": "Demo Datacenter",
  "layout": {
    "racks": [
      {
        "code": "RACK-C01-R01",
        "column": "C01",
        "row": "R01",
        "slotCount": 42
      },
      {
        "code": "RACK-C01-R02",
        "column": "C01",
        "row": "R02",
        "slots": [
          "S01",
          "S02",
          "S03"
        ]
      }
    ]
  },
  "serverModels": [
    {
      "modelCode": "SRV-DEMO-001",
      "manufacturer": "CPZ",
      "model": "Demo Server",
      "idlePowerWatts": 100.0,
      "maxPowerWatts": 300.0
    }
  ],
  "servers": [
    {
      "column": "C01",
      "rackCode": "RACK-C01-R01",
      "slot": "U01",
      "modelCode": "SRV-DEMO-001",
      "status": "OK",
      "role": "AI",
      "workloadFactor": 1.5
    },
    {
      "column": "C01",
      "rackCode": "RACK-C01-R01",
      "slot": "U02",
      "modelCode": "SRV-DEMO-001",
      "status": "OFFLINE",
      "workloadFactor": 1.0
    }
  ]
}
```

`RACK-C01-R01` uses the legacy `slotCount` format, which generates `U01` through
`U42`. `RACK-C01-R02` uses explicit opaque slot codes and exists even though it has
no installed servers. The first server explicitly has role `AI`; the second omits
`role` and is built as `GENERAL_PURPOSE`.

The same `rackCode` may appear in different columns:

```json
{
  "layout": {
    "racks": [
      { "code": "R01", "column": "C01", "row": "R01", "slots": ["S01"] },
      { "code": "R01", "column": "C02", "row": "R01", "slots": ["S01"] }
    ]
  }
}
```

Servers should include `column`. Legacy server entries without `column` remain
valid only when their `rackCode` identifies exactly one rack in the datacenter.

Each rack must define exactly one of:

- `slotCount`: legacy/convenience format that generates `U01`, `U02`, ...
- `slots`: ordered list of non-blank, unique slot identifiers such as `S01`, `GPU-A`, `NETWORK`, or `SPARE`

`slotCount` and `slots` are mutually exclusive.

Optional top-level `temperature`, `health`, and `cooling` blocks configure the
thermal model, health thresholds, and cooling model. If `health` is omitted,
the health options factory uses documented defaults. If `cooling` is omitted,
cooling runtime configuration is absent and the rest of the backend remains
compatible. Server models may optionally override thermal capacity and heat
dissipation as a pair; old JSON without that pair keeps using the global
temperature values. See [JSON Configuration](docs/configuration.md) and
[Cooling System](docs/cooling.md).

Optional top-level `dataInputMode` declares which variable is authoritative for
the simulation:

```json
{
  "dataInputMode": "UTILIZATION_DRIVEN"
}
```

Supported values are `UTILIZATION_DRIVEN`, `POWER_DRIVEN`, and
`TEMPERATURE_DRIVEN`. `UTILIZATION_DRIVEN` supplies utilization through a
`WorkloadSource` and derives server power and temperature from it.
`POWER_DRIVEN` supplies server power through a `ServerPowerInputSource`; server
utilization remains available but is estimated from power so snapshots, health
checks, and UI panels remain compatible. Temperature is still derived from
power, cooling, and the thermal model. `TEMPERATURE_DRIVEN` supplies observed
temperature through rack-level input, or hot-aisle-level input adapted to rack
observations; rack/server temperature, server power, and utilization are
inferred conservatively from that observed temperature.

For `POWER_DRIVEN`, optional top-level `powerInputGranularity` selects the
simulated power-input granularity:

```json
{
  "dataInputMode": "POWER_DRIVEN",
  "powerInputGranularity": "RACK"
}
```

Supported values are `SERVER` and `RACK`; the default is `SERVER` when the field
is absent. `SERVER` simulates power directly per server with
`NoiseServerPowerInputSource`. `RACK` simulates aggregate rack power with
`NoiseRackPowerInputSource`, adapts it through
`RackPowerToServerPowerInputSource`, and still feeds `PowerInputSystem` as a
`ServerPowerInputSource`. `UTILIZATION_DRIVEN` behavior is unchanged even if the
granularity field is present.

For `TEMPERATURE_DRIVEN`, optional top-level `temperatureInputGranularity`
selects the temperature-input granularity. Supported values are `RACK` and
`AISLE`; the default is `RACK` when the field is absent:

```json
{
  "dataInputMode": "TEMPERATURE_DRIVEN",
  "temperatureInputGranularity": "RACK"
}
```

In `TEMPERATURE_DRIVEN/RACK`, `RackTemperatureInputSystem` applies the observed
rack temperature to online servers in the rack, infers server power from the
configured ambient-to-reference temperature range, and then reuses the existing
power-to-utilization inference on each server.
Rack temperatures can be supplied manually with `MapRackTemperatureInputSource`
or generated deterministically with `SimulatedRackTemperatureInputSource` for
backend scenarios that need smooth per-tick variation without UI-side data
generation.
In `TEMPERATURE_DRIVEN/AISLE`, the observed input is hot-aisle temperature, not
cold-aisle temperature. `AisleTemperatureInputSource` supplies that value and
`AisleTemperatureToRackTemperatureInputSource` adapts it to rack observations.
For the current standard/demo layout, `StandardHotAisleCodeResolver` maps
columns to hot aisles as follows:

```text
C01       -> HA01
C02 / C03 -> HA02
C04 / C05 -> HA03
C06 / C07 -> HA04
C08       -> HA05
```

Columns that share the same hot aisle receive the same base observed
temperature for the same tick. Differences inside one hot aisle would require a
future model for gradients, multiple sensors, distance to extraction, localized
recirculation, or similar effects; they are not part of the current
implementation. Aisle temperatures can be supplied manually with
`MapAisleTemperatureInputSource` or generated deterministically with
`SimulatedAisleTemperatureInputSource`. The current resolver is intentionally a
minimum solution for the standard/demo layout; supporting arbitrary layouts
should move hot-aisle mapping into configuration.
The default maximum reference temperature is `85.0 C`; it is an inference
reference used to normalize the thermal ratio, not a universal health threshold
and not a replacement for configured health hysteresis thresholds.

---

## Simulation Pipeline and Snapshots

The historical utilization-driven causal simulation order without cooling is:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> TemperatureSystem
-> ServerHealthSystem
-> EnergyConsumptionSystem
```

Snapshot providers read resulting state after systems update:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> TemperatureSystem
-> ServerHealthSystem
-> EnergyConsumptionSystem
-> EnergyConsumptionSnapshotProvider / TemperatureSnapshotProvider / HealthSnapshotProvider
```

For consumer applications, prefer `DatacenterInputPipelineFactory` to register
the input-side systems from `dataInputMode` and the configured granularities.
This avoids duplicating mode-specific rules in a UI or adapter, especially the
rule that `TEMPERATURE_DRIVEN` writes observed temperature through
`RackTemperatureInputSystem` and must not run `TemperatureSystem.update(...)`
afterward.

Recommended flow using JSON `workloadFactor`, `FractalNoise`,
`NoiseWorkloadSource`, and `ScaledWorkloadSource`:

```java
DatacenterDefinition definition =
        new JsonDatacenterConfigLoader().load(Path.of("data/config/demo-datacenter-medium.json"));
Datacenter datacenter = new DatacenterFactory().create(definition);

FractalNoise fractalNoise = new FractalNoise(
        new PerlinNoise(1234L),
        5,
        1.0f,
        2.0f,
        0.5f
);

WorkloadSource baseWorkload = new NoiseWorkloadSource(fractalNoise, 0.001, 0.2f, 0.9f);
ServerWorkloadFactorProvider factors =
        new WorkloadFactorProviderFactory().create(definition);
WorkloadSource workload = new ScaledWorkloadSource(baseWorkload, factors);

TemperatureSystemOptions temperatureOptions =
        new TemperatureSystemOptionsFactory().create(definition);
TemperatureSystem temperatureSystem = new TemperatureSystem(
        datacenter,
        temperatureOptions,
        new SimpleServerTemperatureModel()
);
ServerHealthOptions healthOptions =
        new ServerHealthOptionsFactory().create(definition);
ServerHealthSystem healthSystem =
        new ServerHealthSystem(datacenter, temperatureSystem, healthOptions);
EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);

SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
new DatacenterInputPipelineFactory().registerInputSystems(
        engine,
        definition,
        datacenter,
        temperatureSystem,
        temperatureOptions,
        workload,
        null
);
engine.register(healthSystem);
engine.register(energySystem);

SimulationTick tick = engine.step();
EnergyConsumptionSnapshotProvider energyProvider =
        new EnergyConsumptionSnapshotProvider(datacenter, energySystem);
TemperatureSnapshotProvider temperatureProvider =
        new TemperatureSnapshotProvider(datacenter, temperatureSystem, temperatureOptions);
HealthSnapshotProvider healthProvider =
        new HealthSnapshotProvider(datacenter, healthSystem, temperatureSystem);
EnergyConsumptionSnapshot energySnapshot = energyProvider.snapshot(tick);
TemperatureSnapshot temperatureSnapshot = temperatureProvider.snapshot(tick);
HealthSnapshot healthSnapshot = healthProvider.snapshot(tick);
```

The energy snapshot captures tick index, elapsed seconds, total IT power,
accumulated energy and one entry per server with rack, slot, `HardwareStatus`,
utilization and current power. Per-server snapshots include column, rack code and slot. The
slot value is the exact code from `ServerLocation`; it is not normalized or
interpreted by the backend.

When providers run after the pipeline, every snapshot status is the
`HardwareStatus` resulting from `ServerHealthSystem` for that tick (or the
preserved `OFFLINE` status). Temperature and health are exposed through separate
snapshot models. See [Temperature Model](docs/temperature.md) and
[Server Health](docs/server-health.md).

When cooling is configured from JSON for a power-derived temperature pipeline,
the causal order becomes:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> CoolingSystem.tick(...)
-> CoolingSnapshotTemperatureReferenceProvider.updateSnapshot(...)
-> TemperatureSystem
-> ServerHealthSystem
-> EnergyConsumptionSystem
```

The backend supports the flow:

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

For consumer applications that need to inspect the plan without registering
systems, `DatacenterDataInputModePlanner.planFor(definition.dataInputMode())`
describes which parts of the state are backend-derived and which parts are
externally supplied. `DatacenterInputPipelineFactory` is the productive
assembly point. In `UTILIZATION_DRIVEN`, utilization is supplied through
`WorkloadSource`, power is derived by `PowerConsumptionSystem`, and temperature
is derived by `TemperatureSystem`. In `POWER_DRIVEN`, server power is supplied
through `PowerInputSystem` and `ServerPowerInputSource`; utilization is
estimated from that power, and temperature is still derived from power/cooling
through `TemperatureSystem`. The source can be server-level or rack-level;
`PowerInputSystem` does not distinguish the origin because both paths expose
`ServerPowerInputSource`.
In `TEMPERATURE_DRIVEN/RACK`, `TemperatureInputSourceFactory` creates a rack
temperature source and `RackTemperatureInputSystem` writes observed rack
temperature into `TemperatureSystem` for online servers. In
`TEMPERATURE_DRIVEN/AISLE`, `TemperatureInputSourceFactory` creates an
`AisleTemperatureToRackTemperatureInputSource` backed by
`SimulatedAisleTemperatureInputSource` and `StandardHotAisleCodeResolver`; the
adapted rack observations are then consumed by `RackTemperatureInputSystem`.
In both temperature-driven granularities, server power and utilization are
inferred before downstream energy and snapshot providers read state, and
`TemperatureSystem` must not be registered later as a simulatable that
recalculates temperature from power.
Consumers should interpret `temperatureInputGranularity` only together with
`dataInputMode = TEMPERATURE_DRIVEN`; other modes ignore that field.

---

## Existing Demos

The demos are located in `src/main/java/com/cpz/sim/datacenter/example`:

- `DatacenterSimulationDemo`: in-code datacenter simulation.
- `NoiseWorkloadSimulationDemo`: in-code datacenter using `FractalNoise`.
- `JsonDatacenterSimulationDemo`: loads `data/config/demo-datacenter-medium.json`, uses `FractalNoise` and `ScaledWorkloadSource`.
- `JsonTemperatureSimulationDemo`: loads JSON and delegates input-system
  registration to `DatacenterInputPipelineFactory`.
- `EnergySnapshotSimulationDemo`: loads JSON, simulates `FractalNoise + workloadFactor` with the energy-only pipeline, and emits energy snapshots.
- `TemperatureSimulationDemo`: in-code simulation with workload, power, temperature, and energy systems plus separate energy and temperature snapshots; it does not register `ServerHealthSystem`.
- `CoolingSimulationDemo`: in-code simulation with cooling units, cooling snapshots, and temperature integration.

The runnable demos above that omit `ServerHealthSystem` retain the configured
server status. Use the complete pipeline shown earlier when snapshots must expose
automatically calculated health status.

From an IDE, run the `main` method of each class directly. With Maven, because no
exec plugin is configured in `pom.xml`, use the Maven Exec Plugin explicitly:

```bash
mvn -Dexec.mainClass=com.cpz.sim.datacenter.example.EnergySnapshotSimulationDemo \
    org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

`EnergySnapshotSimulationDemo` optionally accepts the JSON path as its first
argument; by default it uses `data/config/demo-datacenter-medium.json`.

---

## Additional Documentation

- [Architecture](docs/architecture.md)
- [JSON Configuration](docs/configuration.md)
- [Workloads](docs/workloads.md)
- [Energy Snapshot](docs/energy-snapshot.md)
- [Cooling System](docs/cooling.md)
- [Temperature Model](docs/temperature.md)
- [Server Health](docs/server-health.md)
- [Using the Library from a Maven UI](docs/getting-started-ui.md)

---

## Roadmap

This milestone closes the first functional base for energy simulation, the
initial server temperature model, automatic server health evaluation, and the
first cooling integration from JSON to runtime snapshots. Future work outside
the current scope:

- Stable final `0.1.0` API.
- Additional production telemetry adapters for externally provided power and
  temperature feeds.
- Server-level and room-level temperature input.
- Configurable hot-aisle mapping for arbitrary layouts.
- Hot-aisle gradients, multiple sensors per aisle, and localized recirculation
  effects.
- More detailed rack inlet, room, and cooling-zone thermal modeling.
- UI or visualization.
- More complete public contracts for consumer applications.

---

## License

`cpz-sim-datacenter` is released under the Apache License, Version 2.0. See [LICENSE](LICENSE).

---

## Author

**Carlos Polo Zamora**  
GitHub: https://github.com/cdpoloz  
Alias: CPZ / cepezeta / cdpoloz

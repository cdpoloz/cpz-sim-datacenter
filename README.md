# CPZ SIM Datacenter

![Java](https://img.shields.io/badge/Java-26+-orange)
![Status](https://img.shields.io/badge/status-active-brightgreen)
![License](https://img.shields.io/badge/license-Apache--2.0-lightgrey)
[![GitHub](https://img.shields.io/badge/GitHub-cdpoloz-181717?logo=github)](https://github.com/cdpoloz)

`cpz-sim-datacenter` is a pure Java backend for simulating server workload, IT
power, simplified server temperature, server health, and accumulated energy
consumption in a datacenter. It is an independent Maven library intended to be
consumed by other applications, for example a future UI such as
`sim-datacenter-ui`.

It does not include Processing, a graphical UI, client-specific logic, cooling
modeling, airflow modeling, or room-level thermal modeling. The API is
preliminary and may change before the final `0.1.0` release.

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
- Physical layout with existing racks, ordered slot codes, and empty racks.
- Servers installed by `column`, `rackCode`, and `slot`.
- Hardware states: `OK`, `ALERT`, `OFFLINE`, with automatic health evaluation from utilization and temperature.
- Simulation systems: `WorkloadSystem`, `PowerConsumptionSystem`, `TemperatureSystem`, `ServerHealthSystem`, and `EnergyConsumptionSystem`.
- Workload strategy through `WorkloadSource`, with noise-based and scaled workloads.
- Integration with `FractalNoise` from `cpz-utils` for variable workloads.
- Per-server `workloadFactor` read from JSON and applied through `ScaledWorkloadSource`.
- Energy snapshots through `EnergyConsumptionSnapshotProvider`, `EnergyConsumptionSnapshot` and `ServerEnergySnapshot`.
- Temperature snapshots through `TemperatureSnapshotProvider`, `TemperatureSnapshot`, and `ServerTemperatureSnapshot`.
- Health snapshots through `HealthSnapshotProvider`, `HealthSnapshot`, and `ServerHealthSnapshot`.

Important rules:

- An empty rack represents physical infrastructure with no installed server.
- An `OFFLINE` server represents an installed server that is powered off or not operational.
- Rack identity is `column + rackCode`; server identity is `column + rackCode + slot`.
- Slot codes are opaque identifiers declared by each rack. A UI should read rack slots from the backend and match servers by exact `column + rackCode + slot`.
- `WorkloadSystem` forces `utilization = 0.0f` for `OFFLINE` servers and does not query the `WorkloadSource` for them.
- `Server.updatePowerConsumption()` forces `currentPowerWatts = 0.0f` for `OFFLINE` servers.
- The JSON `status` is the initial hardware status. On every tick,
  `ServerHealthSystem` recalculates non-`OFFLINE` status as `OK` or `ALERT` from
  current utilization and temperature using configured thresholds.
- `OFFLINE` has priority and is never overwritten by `ServerHealthSystem`.
- `workloadFactor` can be greater than `1.0`; the final utilization produced by `ScaledWorkloadSource` is clamped to `[0, 1]`.

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
no installed servers.

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

Optional top-level `temperature` and `health` blocks configure the thermal model
and health thresholds. If `health` is omitted, the health options factory uses
documented defaults. See [JSON Configuration](docs/configuration.md).

---

## Simulation Pipeline and Snapshots

The expected causal simulation order is:

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

Recommended flow using JSON `workloadFactor`, `FractalNoise`, `NoiseWorkloadSource`
and `ScaledWorkloadSource`:

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
engine.register(new WorkloadSystem(datacenter, workload));
engine.register(new PowerConsumptionSystem(datacenter));
engine.register(temperatureSystem);
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
accumulated energy and one entry per server with rack, slot, status, utilization
and current power. Per-server snapshots include column, rack code and slot. The
slot value is the exact code from `ServerLocation`; it is not normalized or
interpreted by the backend.

When providers run after the pipeline, every snapshot status is the
`HardwareStatus` resulting from `ServerHealthSystem` for that tick (or the
preserved `OFFLINE` status). Temperature and health are exposed through separate
snapshot models. See [Temperature Model](docs/temperature.md) and
[Server Health](docs/server-health.md).

---

## Existing Demos

The demos are located in `src/main/java/com/cpz/sim/datacenter/example`:

- `DatacenterSimulationDemo`: in-code datacenter simulation.
- `NoiseWorkloadSimulationDemo`: in-code datacenter using `FractalNoise`.
- `JsonDatacenterSimulationDemo`: loads `data/config/demo-datacenter-medium.json`, uses `FractalNoise` and `ScaledWorkloadSource`.
- `EnergySnapshotSimulationDemo`: loads JSON, simulates `FractalNoise + workloadFactor` with the energy-only pipeline, and emits energy snapshots.
- `TemperatureSimulationDemo`: in-code simulation with workload, power, temperature, and energy systems plus separate energy and temperature snapshots; it does not register `ServerHealthSystem`.

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
- [Temperature Model](docs/temperature.md)
- [Server Health](docs/server-health.md)
- [Using the Library from a Maven UI](docs/getting-started-ui.md)

---

## Roadmap

This milestone closes the first functional base for energy simulation, the
initial server temperature model, and automatic server health evaluation. Future
work outside the current scope:

- Stable final `0.1.0` API.
- Cooling model.
- Rack inlet, room, and cooling-zone thermal modeling.
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

# Using the Library from a Maven UI

A UI such as `sim-datacenter-ui` should consume `cpz-sim-datacenter` as a library.
The UI should not reimplement the datacenter model or calculate power or energy on
its own.

## Maven Dependency

```xml
<dependency>
    <groupId>com.cpz.sim</groupId>
    <artifactId>cpz-sim-datacenter</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

`cpz-sim-datacenter` depends on `cpz-sim-foundation`, `cpz-utils`, and Jackson. If
`cpz-sim-foundation` is not published in a Maven-accessible repository, it must be
installed in the local repository.

## Local Installation Order

When the projects are under local development:

1. `cpz-sim-foundation`
2. `cpz-sim-datacenter`
3. `sim-datacenter-ui`

Command to install this project:

```bash
mvn clean install
```

## Recommended Flow for a UI

1. Load a JSON file with `JsonDatacenterConfigLoader`.
2. Build the domain with `DatacenterFactory`.
3. Create a `NoiseWorkloadSource` with `FractalNoise`.
4. Register systems in `SimulationEngine` in the correct order.
5. Run ticks.
6. Convert each resulting tick to `EnergyConsumptionSnapshot`.
7. Render the UI from snapshots, not from direct domain mutations.

Example:

```java
Path configPath = Path.of("data/config/demo-datacenter-medium.json");
DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(configPath);
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

EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);

SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
engine.register(new WorkloadSystem(datacenter, workload));
engine.register(new PowerConsumptionSystem(datacenter));
engine.register(energySystem);

EnergyConsumptionSnapshotProvider snapshots =
        new EnergyConsumptionSnapshotProvider(datacenter, energySystem);

SimulationTick tick = engine.step();
EnergyConsumptionSnapshot snapshot = snapshots.snapshot(tick);
```

## Useful Contract for a UI

High-level data:

- `snapshot.tickIndex()`
- `snapshot.elapsedSeconds()`
- `snapshot.totalItPowerWatts()`
- `snapshot.consumedEnergyWh()`
- `snapshot.consumedEnergyKWh()`
- `snapshot.serverCount()`

Per-server data:

- `server.serverCode()`
- `server.rackCode()`
- `server.slot()`
- `server.status()`
- `server.utilization()`
- `server.currentPowerWatts()`

## Current Integration Limits

- Preliminary API; it may change before the final `0.1.0`.
- No UI-specific events.
- No internal snapshot history.
- No temperature or cooling yet.
- No dedicated snapshot serialization layer; the current records can be serialized by the consuming application if needed.

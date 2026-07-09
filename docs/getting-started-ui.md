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
6. Convert each resulting tick to one or more snapshots such as `EnergyConsumptionSnapshot` and `TemperatureSnapshot`.
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
TemperatureSystemOptions temperatureOptions = TemperatureSystemOptions.defaults();
TemperatureSystem temperatureSystem = new TemperatureSystem(
        datacenter,
        temperatureOptions,
        new SimpleServerTemperatureModel()
);

SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
engine.register(new WorkloadSystem(datacenter, workload));
engine.register(new PowerConsumptionSystem(datacenter));
engine.register(temperatureSystem);
engine.register(energySystem);

EnergyConsumptionSnapshotProvider energySnapshots =
        new EnergyConsumptionSnapshotProvider(datacenter, energySystem);
TemperatureSnapshotProvider temperatureSnapshots =
        new TemperatureSnapshotProvider(datacenter, temperatureSystem, temperatureOptions);

SimulationTick tick = engine.step();
EnergyConsumptionSnapshot energySnapshot = energySnapshots.snapshot(tick);
TemperatureSnapshot temperatureSnapshot = temperatureSnapshots.snapshot(tick);
```

## Useful Contract for a UI

High-level data:

- `energySnapshot.tickIndex()`
- `energySnapshot.elapsedSeconds()`
- `energySnapshot.totalItPowerWatts()`
- `energySnapshot.consumedEnergyWh()`
- `energySnapshot.consumedEnergyKWh()`
- `energySnapshot.serverCount()`

Per-server data:

- `server.serverCode()`
- `server.rackCode()`
- `server.slot()`
- `server.status()`
- `server.utilization()`
- `server.currentPowerWatts()`

Independent temperature data:

- `temperatureSnapshot.ambientTemperatureCelsius()`
- `temperatureSnapshot.averageTemperatureCelsius()`
- `temperatureSnapshot.maxTemperatureCelsius()`
- `server.temperatureCelsius()`

The temperature snapshot is separate from the energy snapshot. This lets a UI
consume temperature and energy independently without introducing a global
combined snapshot.

## Current Integration Limits

- Preliminary API; it may change before the final `0.1.0`.
- No UI-specific events.
- No internal snapshot history.
- Temperature is a simplified internal server model only.
- No cooling, airflow, or room-level thermal modeling.
- No dedicated snapshot serialization layer; the current records can be serialized by the consuming application if needed.

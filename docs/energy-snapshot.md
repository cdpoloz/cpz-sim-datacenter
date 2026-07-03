# Energy Snapshot

The energy snapshot is an immutable view of the energy state after a simulation
tick. It is intended to expose data to external consumers, such as a future UI,
without coupling the UI to the internal mutable state of the domain.

## Classes

`EnergyConsumptionSnapshotProvider` builds snapshots from:

- `Datacenter`
- `EnergyConsumptionSystem`
- `SimulationTick`

`EnergyConsumptionSnapshot` contains:

- `tickIndex`
- `elapsedSeconds`
- `totalItPowerWatts`
- `consumedEnergyWh`
- `servers`

It also exposes:

- `consumedEnergyKWh()`
- `serverCount()`

`ServerEnergySnapshot` contains, per server:

- `serverCode`
- `rackCode`
- `slot`
- `status`
- `utilization`
- `currentPowerWatts`

## When to Generate It

Generate the snapshot after running the tick and after the systems have been
updated in this order:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> EnergyConsumptionSystem
-> EnergyConsumptionSnapshotProvider
```

The provider does not advance the simulation. It only reads the current state of
the datacenter and the energy system.

## Recommended Example with JSON, FractalNoise, and workloadFactor

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

EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);

SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
engine.register(new WorkloadSystem(datacenter, workload));
engine.register(new PowerConsumptionSystem(datacenter));
engine.register(energySystem);

SimulationTick tick = engine.step();

EnergyConsumptionSnapshotProvider provider =
        new EnergyConsumptionSnapshotProvider(datacenter, energySystem);
EnergyConsumptionSnapshot snapshot = provider.snapshot(tick);

double powerWatts = snapshot.totalItPowerWatts();
double energyKWh = snapshot.consumedEnergyKWh();
int serverCount = snapshot.serverCount();
```

The complete example is in:

```text
src/main/java/com/cpz/sim/datacenter/example/EnergySnapshotSimulationDemo.java
```

## Relationship to a Future UI

A UI should treat `EnergyConsumptionSnapshot` as a read DTO:

- Render total power and accumulated energy from the snapshot.
- Render servers using `ServerEnergySnapshot`.
- Do not modify `Server` directly from the visual layer.
- Generate one snapshot per tick, or at whatever cadence the UI needs.

The current snapshot does not include temperature, cooling, PUE, or historical
metrics. If the UI needs time series, it should accumulate snapshots or use a
higher-level layer that stores them.

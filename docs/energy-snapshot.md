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
- `column`
- `rackCode`
- `slot`
- `status` (`HardwareStatus`)
- `utilization`
- `currentPowerWatts`

`slot` is the exact server location slot code. It may be legacy `U01` or an
explicit opaque rack slot such as `S01`, `GPU-A`, or `NETWORK`.
`column + rackCode + slot` distinguishes servers such as `C01/R01/S01` and
`C02/R01/S01`.

`status` is read from the current server state. When the provider runs after
`ServerHealthSystem`, it is the `HardwareStatus` calculated for the tick from
utilization and temperature, except that `OFFLINE` is preserved.

## When to Generate It

Generate the snapshot after running the tick and after the systems have been
updated in this order:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> TemperatureSystem
-> ServerHealthSystem
-> EnergyConsumptionSystem
```

The provider does not advance the simulation. It only reads the current state of
the datacenter and the energy system.

Temperature and health data are exposed separately through
`TemperatureSnapshotProvider` and `HealthSnapshotProvider`. They are intentionally
not merged into `EnergyConsumptionSnapshot`.

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

EnergyConsumptionSnapshotProvider provider =
        new EnergyConsumptionSnapshotProvider(datacenter, energySystem);
EnergyConsumptionSnapshot snapshot = provider.snapshot(tick);

double powerWatts = snapshot.totalItPowerWatts();
double energyKWh = snapshot.consumedEnergyKWh();
int serverCount = snapshot.serverCount();
```

The existing energy-only runnable demo is:

```text
src/main/java/com/cpz/sim/datacenter/example/EnergySnapshotSimulationDemo.java
```

That demo does not construct `TemperatureSystem` or `ServerHealthSystem`, so its
snapshot status remains the initial configured value. Use the complete pipeline
shown above when energy snapshots must expose automatically calculated health
status.

## Relationship to a Future UI

A UI should treat `EnergyConsumptionSnapshot` as a read DTO:

- Render total power and accumulated energy from the snapshot.
- Render servers using `ServerEnergySnapshot`.
- Treat each server status as calculated simulation output, not as a static copy
  of the JSON configuration.
- Do not modify `Server` directly from the visual layer.
- Generate one snapshot per tick, or at whatever cadence the UI needs.

The current snapshot does not include temperature, alert reasons, cooling, PUE,
or historical metrics. Use the independent temperature and health snapshots for
the first two. If the UI needs time series, it should accumulate snapshots or
use a higher-level layer that stores them.

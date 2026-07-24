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
6. Convert each resulting tick to one or more snapshots such as `EnergyConsumptionSnapshot`, `TemperatureSnapshot`, and `HealthSnapshot`.
7. Render the UI from rack slot definitions and snapshots, not from direct domain mutations.

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

SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
engine.register(new WorkloadSystem(datacenter, workload));
engine.register(new PowerConsumptionSystem(datacenter));
engine.register(temperatureSystem);
engine.register(healthSystem);
engine.register(energySystem);

EnergyConsumptionSnapshotProvider energySnapshots =
        new EnergyConsumptionSnapshotProvider(datacenter, energySystem);
TemperatureSnapshotProvider temperatureSnapshots =
        new TemperatureSnapshotProvider(datacenter, temperatureSystem, temperatureOptions);
HealthSnapshotProvider healthSnapshots =
        new HealthSnapshotProvider(datacenter, healthSystem, temperatureSystem);

SimulationTick tick = engine.step();
EnergyConsumptionSnapshot energySnapshot = energySnapshots.snapshot(tick);
TemperatureSnapshot temperatureSnapshot = temperatureSnapshots.snapshot(tick);
HealthSnapshot healthSnapshot = healthSnapshots.snapshot(tick);
```

The required system registration order is:

```text
WorkloadSystem
-> PowerConsumptionSystem
-> TemperatureSystem
-> ServerHealthSystem
-> EnergyConsumptionSystem
```

## Useful Contract for a UI

Physical rack layout:

- `datacenter.getRacks()`
- `rack.getCode()`
- `rack.getLocation()`
- `rack.getColumn()`
- `rack.getRow()`
- `rack.getSlotCount()`
- `rack.getSlotCodes()`
- `rack.hasSlot(slotCode)`
- `datacenter.findRack("C01", "R01")`
- `datacenter.getServers("C01", "R01")`
- `datacenter.getServer("C01", "R01", "S03")`

The UI must get physical slots from each rack. It should compare those exact slot
codes with server locations from snapshots. Rack identity is `column + rackCode`;
server identity is `column + rackCode + slot`. It must not generate `U01`, `U02`,
... on its own when a rack uses explicit `slots`.

Example slot rendering:

```java
Rack rack = datacenter.findRack("C01", "R01").orElseThrow();

for (String slot : rack.getSlotCodes()) {
    Optional<Server> installed =
            datacenter.getServer("C01", "R01", slot);

    if (installed.isEmpty()) {
        // Empty physical slot
    }
}
```

High-level data:

- `energySnapshot.tickIndex()`
- `energySnapshot.elapsedSeconds()`
- `energySnapshot.totalItPowerWatts()`
- `energySnapshot.consumedEnergyWh()`
- `energySnapshot.consumedEnergyKWh()`
- `energySnapshot.serverCount()`

Per-server data:

- `server.serverCode()`
- `server.column()`
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

Independent health data:

- `healthSnapshot.alertServerCount()`
- `healthSnapshot.countByReason(reason)`
- `server.status()`
- `server.alertReasons()`
- `server.utilization()`
- `server.temperatureCelsius()`

The temperature and health snapshots are separate from the energy snapshot. This
lets a UI consume each concern independently without introducing a global
combined snapshot.

All per-server snapshot statuses read the server's current state. After the
pipeline above, this is the status calculated by `ServerHealthSystem` from
utilization and temperature for that tick. The UI should not treat the JSON
status as permanent or reproduce the threshold calculation itself.

Slot state should be represented as:

- declared slot without an installed server: empty slot
- installed server with `OFFLINE` status: server present but powered off or not operational

`OFFLINE` has priority over health evaluation and is never changed automatically
to `OK` or `ALERT`.

Snapshots contain installed servers only. Empty slots come from
`Rack.getSlotCodes()` combined with `Datacenter.getServer(...)`.

Do not model empty slots by adding `EMPTY` to `HardwareStatus`.

## Current Integration Limits

- Preliminary API; it may change before the final `0.1.0`.
- No UI-specific events.
- No internal snapshot history.
- Temperature is a simplified internal server model only.
- No cooling, airflow, or room-level thermal modeling.
- No dedicated snapshot serialization layer; the current records can be serialized by the consuming application if needed.

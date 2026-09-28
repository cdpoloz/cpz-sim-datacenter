# Operational snapshots

`DatacenterOperationalSnapshotProvider` combines energy, temperature and
health snapshots captured for the same completed tick. It exposes immutable
aggregates for racks, columns, the complete datacenter and optional
application-defined server groups.

## Application-defined server groups

The backend intentionally does not assign physical meaning to server groups.
A consumer can use them to represent hot aisles, zones, clusters or other
stable selections without adding UI layout concepts to the simulation model.

Define groups once, after the datacenter topology has been loaded:

```java
ServerGroupDefinition hotAisle = new ServerGroupDefinition(
        "HA01",
        Set.of(
                new ServerLocation("C01", "R01", "S01"),
                new ServerLocation("C01", "R01", "S02")
        )
);

DatacenterOperationalSnapshotProvider provider =
        new DatacenterOperationalSnapshotProvider(
                datacenter,
                List.of(hotAisle)
        );
```

Every location must identify an installed server in the datacenter. Group
codes must be unique. Groups may overlap and may be empty.

For every completed tick, use the same provider as usual:

```java
DatacenterOperationalSnapshot snapshot = provider.snapshot(
        energySnapshot,
        temperatureSnapshot,
        healthSnapshot
);

ServerGroupOperationalSnapshot aisle =
        snapshot.getServerGroup("HA01");
```

Temperature and utilization averages include online servers only. Maximum
temperature includes every installed server in the group and exposes its exact
location. When a group has no online servers, its online averages are `NaN`.
When it has no installed servers, its maximum is `NaN` and its maximum location
is empty.

Rack snapshots expose two temperature metrics with different contracts:
`averageOnlineTemperatureCelsius` is the real average temperature of online
servers only and remains `NaN` when a rack has no online servers.
`representativeTemperatureCelsius` is a finite rack temperature intended for
spatial or aggregate visualization. It matches the online average when the
rack has online servers, and falls back to the snapshot
`ambientTemperatureCelsius` when the rack is empty or all installed servers are
offline. Rack thermal gradients in the UI should use
`representativeTemperatureCelsius` to avoid gaps for racks without online
servers.

The one-argument provider constructor remains available and produces no group
aggregates.

## Hot-aisle temperature history

`HotAisleTemperatureHistoryRecorder` records an in-memory series after
each operational snapshot has been captured. It groups racks through the same
`Function<RackLocation, String>` used to resolve hot-aisle codes; applications
can therefore use `StandardHotAisleCodeResolver` or
`ConfiguredHotAisleCodeResolver` for `layout.hotAisles`.

Each `HotAisleTemperatureSample` contains the hot-aisle code, tick index, and
average temperature. The average is weighted by the online-server count of each
rack. Racks without online servers do not contribute, and an aisle without valid
temperature data receives no sample. Every valid sample is retained for the
lifetime of the history instance; there is no maximum window or FIFO eviction.

Pass the recorder to the extended `DatacenterSimulationHistoryRecorder`
constructor. Its `record(tick)` call captures the operational snapshot and then
updates the hot-aisle series automatically:

```java
HotAisleTemperatureHistoryRecorder hotAisleHistory =
        new HotAisleTemperatureHistoryRecorder(
                new ConfiguredHotAisleCodeResolver(definition.layout().hotAisles())
        );

DatacenterSimulationHistoryRecorder recorder =
        new DatacenterSimulationHistoryRecorder(
                energySnapshots,
                temperatureSnapshots,
                healthSnapshots,
                operationalSnapshots,
                Optional::empty,
                new DatacenterSimulationHistory(),
                hotAisleHistory
        );

List<HotAisleTemperatureSample> series =
        hotAisleHistory.history().samples("HA02");
```

`samples(code)` returns an immutable list in ascending tick order and returns an
empty list for an unknown aisle or one with no samples. The history remains in
memory for the lifetime of the simulation instance and is not long-term
persistence. Creating a new recorder creates an empty history, and
`DatacenterSimulationHistoryRecorder.clear()` clears its attached hot-aisle
history as well. Consumers such as the UI should choose how many of the retained
samples to present based on available display space.

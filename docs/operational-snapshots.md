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

The one-argument provider constructor remains available and produces no group
aggregates.

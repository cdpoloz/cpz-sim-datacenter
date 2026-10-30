# Customer datasets: normalized workbook pilot

The backend can now inspect the English `0.1-proposed` customer dataset workbook,
preserve its original observations and expose approved instantaneous server
series through the existing utilization/power source interfaces. This is the
first dataset import/replay stage; it does not change thermal calculations or
the UI and does not interpret arbitrary client spreadsheet layouts.

## Two separate contracts

- [Installation configuration](templates/README.md): inventory, physical layout,
  ratings, cooling definitions and model parameters; converted separately to the
  existing configuration JSON.
- [Customer dataset workbook](templates/datacenter-customer-dataset-template.xlsx):
  timestamped observations linked by `configurationId`, `configurationVersion`
  and `roomCode`. The blank workbook includes the dictionary and completion rules.

The configuration identity/version belongs to the exchange layer. It is supplied
explicitly through `DatasetBinding`, not inserted as unsupported JSON properties.

## Read, inspect, then bind

```java
CustomerDataset dataset = new XlsxCustomerDatasetLoader().load(workbookPath);
List<DatasetIssue> inspection =
        new CustomerDatasetValidator().validate(dataset, null);
```

The loader expects row 6 headers and row 7 onward data. It supports the `Dataset`,
`EntityMapping`, `ServerSamples`, `RackSamples`, `RoomSamples`, and `CoolingSamples`
tables. `Guide` and `Dictionary` may accompany them. Headers can be reordered but
must be present exactly once; unknown sheets/headers/data outside headers are
rejected. Blank styled rows are ignored. Formulas and error cells are rejected
rather than evaluated or replaced by cached values.

Values retain their native number, text or boolean types. Timestamps must be UTC
ISO 8601 **text ending in Z**; timezone-free Excel serial dates are not guessed.
Numeric strings are not silently converted. Blank is different from zero/false.
The immutable imported observations retain utilization and electrical power
independently, plus source references, quality, provenance and optional metrics.

Validation returns source-located errors/warnings without repairing the data.
It checks required metadata/identities, units/types/ranges, duplicate keys,
interval durations and overlaps, quality/provenance, missing observations,
power boundaries and incompatible EXHAUST fields. With a binding it additionally
checks configuration identity/version, room, rack/server references, server
maximum power and declared cooling-unit identities.

```java
DatasetBinding binding = new DatasetBinding(
        "customer-room", "revision-1", "ROOM-01", datacenter,
        Set.of("SUPPLY:SUPPLY-01", "EXHAUST:EXHAUST-01", "RACK:RACK-COOLING-01"));
List<DatasetIssue> diagnostics =
        new CustomerDatasetValidator().validate(dataset, binding);
```

Cooling keys must come from the matching configuration: SUPPLY from
`cooling.supplyUnits`, EXHAUST from `cooling.exhaustUnits`, and RACK from
`rackCooling.units`. `RACK` here identifies the exchange collection, not a new
backend enum. The console demo builds this set from the supplied JSON.
EntityMapping is retained and checked as a crosswalk; it does not silently fill
missing sample identities or create hardware.

## Bounded server replay

```java
ServerDatasetReplay replay = new ServerDatasetReplay(
        dataset, binding, Duration.ofMinutes(15));
WorkloadSource utilization = replay.workloadSource();
ServerPowerInputSource power = replay.powerSource();
```

Use the source matching the existing `DatacenterInputPipelineFactory` mode:

| Mode | Authoritative input | Calculated runtime value |
| --- | --- | --- |
| `UTILIZATION_DRIVEN` | `replay.workloadSource()` | Server electrical power |
| `POWER_DRIVEN` | `replay.powerSource()` | Estimated utilization |

Do not register both authoritative input systems together. Do not wrap recorded
utilization in simulated workload factors. The original imported pair remains
available for inspection/comparison and is never replaced by runtime estimates.
Temperature, observed status, rack/room/cooling telemetry are retained as
observations; this pilot does not apply them as simultaneous control inputs.
Existing configured OFFLINE behavior and health/thermal logic still apply.

Replay requires a non-empty server series, coverage for every installed server,
matching installation identities and **no unresolved validation diagnostics**.
All server rows must be `VALID`, have specified provenance and use
`INSTANTANEOUS` semantics. Interval means can be inspected but are deliberately
not replayed in this stage. Source acquisition provenance need not be MEASURED;
CALCULATED/ESTIMATED/ASSUMED values remain explicitly labeled.

Elapsed simulation time is added to the earliest server timestamp. At each query,
the latest sample at or before that timestamp is selected. A sample is usable
only while its age is strictly less than the explicitly supplied maximum hold.
An exact next-sample timestamp selects the next sample. Before a server's first
sample, across an expired gap, or after the final hold expires, the query throws;
there is no wrapping, interpolation, zero fill or indefinite last-value hold.
An explicitly declared `coverageEndExclusiveUtc` also bounds replay, even when
the last sample's maximum hold would otherwise extend beyond it.
Resetting simulation time reproduces the same source values without a cursor.

## Console inspection

Run `com.cpz.sim.datacenter.example.CustomerDatasetInspectionDemo` from an IDE,
or with the compiled runtime classpath:

```bash
mvn test
mvn dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
java -cp "target/classes:$(cat target/runtime-classpath.txt)" \
  com.cpz.sim.datacenter.example.CustomerDatasetInspectionDemo dataset.xlsx
```

Optional binding arguments:

```text
dataset.xlsx data/config/datacenter.json configurationId configurationVersion
```

The demo prints per-sheet counts, diagnostic totals, the first 20 diagnostics and
five source observations. It performs inspection only, does not advance the
simulation and never claims that unbound data is ready for replay.

## The supplied 20-rack pilot

The separately prepared preview contains 19,200 records for the first source day,
20 racks and 200 servers. It intentionally lacks the physical room/columns and
configuration identity/version; source temporal meaning/provenance remains
unconfirmed. The importer can display these observations and report unresolved
fields. Do not substitute the UI demo's different layout, invent missing metadata
or mark the data VALID merely to bypass validation.

## Limits and next steps

- XLSX loading uses Apache POI's in-memory API. Partition large exports; full-month
  production ingestion will need bounded/streaming IO and volume measurements.
- Duplicate/interval checks cover one imported part. Cross-part validation and
  dataset manifests are future work; `partId` does not deduplicate multiple files.
- Binding identifiers and cooling references are supplied by the caller. They
  must refer to the same configuration instance/version used by the simulation.
- No direct client-specific converter, configuration-workbook converter, measured
  multi-input runtime mode, energy-counter import or UI wiring is introduced here.
- The library has no automatic disk writes or remote communication on import.

When updating the dataset schema, update `DatasetSchema`, validation tests,
the workbook/dictionary and this guide together. The XLSX reader uses Apache POI
6.0.0, the release listed by the [official download page](https://poi.apache.org/download.cgi)
when this pilot was prepared.

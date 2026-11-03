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

The loader opens the XLSX package with explicit read-only access and closes both
the workbook and package, including on failure. Loading or rejecting a dataset
leaves the input file byte-for-byte unchanged, including ZIP entry timestamps.

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

## Reproducible synthetic console pilot

This scenario is **synthetic**, separate from the customer preview. Nothing here
reconstructs the customer's installation. The committed configuration is
[`data/config/synthetic-dataset-pilot.json`](../data/config/synthetic-dataset-pilot.json).
Its exchange identity is `synthetic-dataset-pilot`, version `1`, room `SYN-ROOM`.

Fictional assumptions:

- Two columns (`C01`, `C02`), ten rack rows each, twenty unique rack codes
  (`SYN-R01` through `SYN-R20`), ten explicit slots (`U01` through `U10`) per rack.
  This is a logical layout, with no claimed physical dimensions or cooling plant.
- Two hundred identical, online, general-purpose servers, rated 100 W idle and
  300 W maximum, using the existing linear electrical model. Workload factors
  are 1 and recorded utilization is passed directly without scaling.
- Ambient and initial server temperature 22 degrees Celsius, thermal capacity
  10,000 J/degree Celsius and heat dissipation 10 W/degree Celsius. These are demo
  parameters for the pipeline's existing temperature system. No health, cooling,
  energy integration or observed temperature/status control is added by this runner.
- UTC instantaneous observations at 00:00, 00:15 and 00:30 on January 1, 2026.
  Coverage is `[00:00,00:45)`. There are 600 server samples and 200 explicit
  `EntityMapping` rows. Other observation tables are empty.
- For instant index `i=0..2` and slot index `s=1..10`, original utilization is
  `(20 + 20*i + s - 1)/100`; original power is `100 + 200*utilization + 10` W.
  Every pair fits the ratings. The deliberate 10 W difference demonstrates that
  original power is preserved independently of modeled power.
- All samples are `INSTANTANEOUS`, `VALID`, `ASSUMED`, with synthetic source
  references and notes; none is labeled `MEASURED`. Blank optional metrics stay
  blank. Normalization time is fixed for reproducibility.

From the repository root, with **JDK 26** and the project's Maven dependencies
available, run these actual entry points (no extra plugins or dependencies):

```bash
mvn --batch-mode --no-transfer-progress test
mvn --batch-mode --no-transfer-progress dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
java -cp "target/classes:$(cat target/runtime-classpath.txt)" \
  com.cpz.sim.datacenter.example.SyntheticDatasetGenerator \
  docs/templates/datacenter-customer-dataset-template.xlsx target/replay-session-pilot.xlsx
java -cp "target/classes:$(cat target/runtime-classpath.txt)" \
  com.cpz.sim.datacenter.example.CustomerDatasetReplayDemo \
  target/replay-session-pilot.xlsx data/config/synthetic-dataset-pilot.json \
  synthetic-dataset-pilot 1 UTILIZATION_DRIVEN PT15M 2026-01-01T00:00:00Z 3 PT15M
java -cp "target/classes:$(cat target/runtime-classpath.txt)" \
  com.cpz.sim.datacenter.example.CustomerDatasetReplayDemo \
  target/replay-session-pilot.xlsx data/config/synthetic-dataset-pilot.json \
  synthetic-dataset-pilot 1 POWER_DRIVEN PT15M 2026-01-01T00:00:00Z 3 PT15M
```

The generator reads the official template with Apache POI, preserves its headers,
guide and dictionary, and writes to a **new** output file. It refuses to overwrite
any existing path, including the template. For another run choose a new output
name. Workbook data are deterministic; XLSX ZIP timestamps need not be identical.
The existing POI dependency may emit `Log4j API could not find a logging provider`
on stderr; this is separate from dataset diagnostics and does not affect results.

Runner arguments, in order:

```text
workbook.xlsx configuration.json configurationId configurationVersion
UTILIZATION_DRIVEN|POWER_DRIVEN maximumHold startUtc steps step [serverCode[,serverCode...]]
```

Durations use ISO 8601, e.g. `PT15M`. Start must be UTC text ending in `Z`.
The range consists of exactly `steps` timestamps, from `startUtc` through
`startUtc + (steps-1)*step`, with positive step and 1..1000 steps. The default
selection is the first installed server by sorted code. An optional selection of
1..10 installed server codes limits detail; all installed servers are simulated
and checked, including those absent from console detail. For example use
`C01-SYN-R01-U01,C02-SYN-R20-U10` as the last argument.

The runner delegates preparation and all steps to `DatasetReplaySession`, described
below. The explicit mode overrides only the JSON input-mode selection in memory.
Installation inventory, ratings and model parameters are preserved. The session
uses `DatacenterFactory`, `SimulationClock`, `SimulationEngine` and
`DatacenterInputPipelineFactory`, registering only the selected authoritative
source and its production electrical/temperature systems. No second simulation
engine is created. Configurations declaring cooling, rack cooling, coupled room
temperature or health are rejected explicitly; power-driven input requires SERVER
granularity. These components are never silently omitted from a configured plant.

`ServerDatasetReplay.origin()` remains the earliest server timestamp. Because
`SimulationEngine.step()` advances before updating systems, console clock origin
is `startUtc - step`; the first engine tick therefore reads exactly `startUtc`.
A time adapter translates each clock tick to elapsed time relative to dataset
origin before calling the existing replay source. Starting later begins a fresh
simulation at that point; it does not warm up thermal history before the range.
The first tick advances the existing thermal model by one step, so these rows
are electrical comparisons, not calibrated historical thermal reconstruction.

The runner validates and binds the workbook, then checks **every requested time
for every installed server** before executing. It buffers console results until
the whole bounded run succeeds. Any unresolved diagnostic (including warnings),
missing required source metric, expired gap, out-of-coverage time, incompatible
configuration or invalid argument produces `REPLAY_FAILED: <cause>` on stderr,
exit status **2**, and no successful or partial result rows on stdout. Success
returns **0** and ends with `REPLAY_OK`.

The summary reports inventory, sample count, diagnostics, declared coverage,
dataset/clock origins, hold and requested range. Detail columns are:

| Column | Meaning |
| --- | --- |
| `mode` | Selected authoritative input mode |
| `timeUtc` | Absolute simulation query time, UTC |
| `serverCode` | Installed identity: column, rack and slot |
| `sourceTimestampUtc` | Timestamp of original sample (may be held) |
| `originalUtilizationFraction` | Unchanged imported fraction, 0..1 |
| `originalPowerWatts` | Unchanged imported electrical power, W |
| `resultUtilizationFraction` | Runtime fraction: authoritative or estimated |
| `resultPowerWatts` | Runtime electrical power, W: calculated or authoritative |

A blank original metric is printed as `null`, never as zero; the chosen mode's
required metric must exist in every server sample. Runtime power uses the existing
float storage; output rounds runtime utilization to six decimals and power to
three. Imported numbers are printed independently.

For `C01-SYN-R01-U01`, expected real output pairs are:

| UTC | Original fraction / W | Utilization-driven result | Power-driven result |
| --- | --- | --- | --- |
| 00:00 | 0.20 / 150 | 0.20 / 140 W | 0.25 / 150 W |
| 00:15 | 0.40 / 190 | 0.40 / 180 W | 0.45 / 190 W |
| 00:30 | 0.60 / 230 | 0.60 / 220 W | 0.65 / 230 W |

No interpolation, wrap or filling is performed. A held sample expires at exactly
`sampleTimestamp + maximumHold`. Exact next-sample times select the new sample.
Coverage end is always exclusive even with a longer hold. To reproduce explicit
failures, rerun the console command above with these argument changes:

| Change | Expected outcome (exit 2) |
| --- | --- |
| `steps=4` and `maximumHold=PT1H` | 00:45 is outside declared coverage |
| `maximumHold=PT10M`, `steps=3`, `step=PT5M` | Sample expires at 00:10 |
| `startUtc=2025-12-31T23:59:59Z`, `steps=1` | Before the first sample |
| `configurationVersion=wrong-version` | Binding mismatch |
| `configurationId=wrong-installation` | Binding mismatch |
| `mode=DUAL` or `TEMPERATURE_DRIVEN` | Unsupported server replay mode |

`CustomerDatasetReplayDemoIntegrationTest` additionally exercises a removed middle
sample in an unselected server, changed installation rack identities, unresolved
quality, missing authoritative metric, invalid arguments, unchanged workbook
bytes after both modes, deterministic generation and refusal to overwrite.

Before replacing this scenario with customer data, obtain the actual installation
configuration (physical layout, server models/ratings, slot identities and any
required thermal/cooling parameters), confirmed configuration identity/version
and room code, and an explicit source-to-installation crosswalk. Confirm timezone,
instantaneous versus interval semantics, quality approval, provenance, units,
power boundaries and coverage. The external 19,200-row preview is not an input to
this scenario. The existing UI inventory cannot substitute for that installation.
The next UI step should consume a validated binding and explicit replay range,
mode and hold, and display original and runtime values separately; it should
surface diagnostics before starting. No UI, HTTP, persistence or publication is
implemented here.


## Reusable electrical replay session

`DatasetReplaySession` is independent of the console and Processing. It owns one
mutable installation and one production simulation engine, without threads,
wall-clock timers, automatic startup steps, health systems or energy integration.
The existing per-server temperature system remains in the input pipeline; its
internal state is not exposed as a historical thermal reconstruction. Configured
ambient/initial temperatures and thermal model parameters are used as usual.

### Prepare and handle failures

Create a `DatasetReplayPlan(mode, startUtc, points, step, maximumHold)`. The mode
must be `UTILIZATION_DRIVEN` or `POWER_DRIVEN`, the start an `Instant`, points
1..`DatasetReplayPlan.MAX_POINTS` (1000), and both durations positive. Unrepresentable
time ranges are rejected as arguments. Cadence in workbook metadata does not
implicitly select the step or hold.

There are two preparation entry points, with the same binding and plan arguments:

```java
DatasetReplaySession.load(configurationPath, workbookPath,
        configurationId, configurationVersion, plan);
DatasetReplaySession.prepare(configurationDefinition, immutableDataset,
        configurationId, configurationVersion, plan);
```

`load` reads each input once, using the read-only XLSX loader. `prepare` reuses a
`CustomerDataset` with its immutable native values and snapshots configuration
collections, so later mutations of caller-owned configuration lists cannot alter
restart behavior. Neither returns a mutable `Datacenter`, `Server` or engine.
The normalizer's native immutable cell values (text, numbers and booleans) remain
unchanged; optional metrics remain absent rather than becoming zero.

Preparation validates the selected configuration, binding, every dataset row,
the chosen authoritative metric, replay semantics and **every installed server at
every requested point**. All errors and warnings block preparation. Every server
sample must provide the authoritative metric, including samples outside the
requested subrange. No step runs and no result frame is created during preparation.

Failures use `DatasetReplayException` with structured accessors:

| Accessor / kind | Meaning |
| --- | --- |
| `kind()` | Stable category; consumers need not parse messages |
| `INVALID_ARGUMENT` | Missing/invalid plan, binding arguments or paths |
| `CONFIGURATION_READ` | Configuration file cannot be read/decoded |
| `DATASET_READ` | XLSX cannot be read or its worksheet structure/cells are rejected |
| `CONFIGURATION` | Installation definition fails backend validation or lacks room metadata |
| `UNSUPPORTED_CONFIGURATION` | Definition requires components outside this session's scope |
| `DATASET_DIAGNOSTICS` | Unresolved validation issues or missing authoritative metric |
| `DATASET_CONTENT` | Missing server series, empty replay or unsupported sample semantics |
| `AVAILABILITY` | A requested server/time has no usable observation |
| `SESSION_STATE` | Advance attempted after an unexpected execution failure |
| `EXECUTION` | Unexpected exception while executing/publishing a step |
| `diagnostics()` | Immutable full `List<DatasetIssue>`; severity, sheet, row, field and message preserved |
| `serverLocation()`, `replayUtc()` | Optional structured context for availability failures |
| `getCause()` | Underlying read, configuration or execution exception when present |

Validation issues from `CustomerDatasetValidator` are retained intact. The
session reports missing authoritative metrics as additional source-located ERROR
issues, without changing validator rules or repairing cells. Loader rejections
are `DATASET_READ` failures, not invented validator diagnostics. Console formatting
limits displayed diagnostics to twenty, but the exception retains the full list.

Supported configurations have room metadata, validated rack/server identities,
server models and optional global/model-specific thermal parameters. Explicit
mode selection overrides the configuration's default input mode. Configurations
with `cooling`, `rackCooling`, `roomAirTemperature` or `health` are rejected,
including present-but-empty blocks. `POWER_DRIVEN` requires `SERVER` power input
granularity. Layout hot-aisle metadata can be retained but no aisle inputs or
thermal outputs are produced. Existing installed OFFLINE behavior still applies;
observed status and temperature remain source values rather than extra controls.
There are no cooling placeholders or inferred equipment.

### Explicit steps and lifecycle

The session states are `PREPARED`, `ACTIVE`, `COMPLETED`, and `FAILED`. Play,
pause and presentation speed belong to the consumer; not calling `advance()`
is sufficient to pause.

- Successful preparation: `PREPARED`, `completedPoints() == 0`,
  `engineElapsed() == Duration.ZERO`, empty `lastFrame()` and `failure()`.
- `advance()` prechecks all installed servers at the next UTC before touching
  the engine. It then executes exactly one `engine.step()` and returns
  `Optional.of(frame)`. State becomes ACTIVE or COMPLETED after the final point.
- After normal completion, `advance()` returns `Optional.empty()` repeatedly,
  without running systems or changing time, progress or the last frame.
- A precheck failure throws `AVAILABILITY` without changing the runtime, state,
  progress or last frame. This protection is separate from preparation preflight.
- An unexpected runtime exception during execution/publication sets FAILED,
  records `failure()`, and publishes no successful frame for that step. Mutable
  systems and the clock may already have advanced: **there is no rollback**.
  Further advances throw SESSION_STATE until restart.
- `restart()` reconstructs runtime state from the captured configuration and
  immutable observations, without rereading files or executing systems. It
  resets progress, last frame and failure and returns to PREPARED. It creates
  no frames; frames retained by the consumer remain unchanged.

Calls must be serialized by the consumer; the session is not thread-safe.

For start `S`, positive step `delta`, and point number `k` starting at 1:

```text
replayUtc     = S + (k - 1) * delta
engineElapsed = k * delta
replayOffset  = (k - 1) * delta
clockOrigin   = S - delta
```

`SimulationEngine.step()` advances its real clock first. Only the tick passed to
the dataset source is adapted to the dataset origin. For a later start such as
00:15, the first engine elapsed time is still one step and replay offset is zero;
00:15's observation is selected, with no earlier history warmed up. With a
15-minute step the three pilot frames have engine elapsed times 15, 30 and 45
minutes and replay offsets 0, 15 and 30 minutes. No frame is produced at 00:45.

Sample-and-hold remains strictly bounded by `ServerDatasetReplay`: the maximum
age is exclusive, coverage end is exclusive, and exact next-sample times select
the new observation. There is no wrapping, interpolation or filling. Dataset
cadence, integration/query step and the consumer's wall-clock playback frequency
are separate quantities. This API exposes no measured/reconstructed temperatures,
energy, health or facility results.

### Inventory, frames and history ownership

`inventory()` returns immutable room metadata, rack locations/rows/slot codes and
server locations/model configurations/initial statuses/roles. Identities are
canonical records, never control-name strings or mutable model instances.

`DatasetReplayFrame` contains `pointNumber`, `replayUtc`, `engineElapsed`,
`replayOffset` and all servers in deterministic code order. Each `ServerResult`
contains `roomCode`, canonical `location`, full immutable `original` `DatasetRow`,
`resultUtilizationFraction` and `resultPowerWatts`. The original row retains its
`timestampUtc`, `sourceEntityId`, `quality`, `provenance`, references, notes and
optional metrics. Utilization is a fraction in [0,1]; electrical power is watts.
Observed values and results are independent, including when a sample is held.

The session stores **only the last completed frame**, not the whole sequence.
It owns no history. A consumer may retain frames, up to the plan's finite point
count (at most 1000 per run), or use a smaller ring buffer. On restart the consumer
must clear its own history if it wants a fresh display; retained frames themselves
are immutable and remain stable across later steps and restarts. Inventory and
source observations can also be retained independently.

Example without Processing (imports omitted):

```java
var plan = new DatasetReplayPlan(DatacenterDataInputMode.POWER_DRIVEN,
        Instant.parse("2026-01-01T00:00:00Z"), 3,
        Duration.ofMinutes(15), Duration.ofMinutes(15));
try {
    var session = DatasetReplaySession.load(
            Path.of("data/config/synthetic-dataset-pilot.json"),
            Path.of("target/replay-session-pilot.xlsx"),
            "synthetic-dataset-pilot", "1", plan);
    // Preparation leaves time at zero. A UI could now enable Play.
    while (session.state() != DatasetReplaySession.State.COMPLETED) {
        var frame = session.advance().orElseThrow();
        var server = frame.servers().getFirst();
        System.out.println(frame.replayUtc() + " " + server.location().code()
                + " original=" + server.original().number("electricalPowerWatts")
                + " W result=" + server.resultPowerWatts() + " W");
    }
    session.restart(); // No file access and no automatic first step.
} catch (DatasetReplayException failure) {
    System.err.println(failure.kind() + ": " + failure.getMessage());
    for (DatasetIssue issue : failure.diagnostics()) {
        System.err.println(issue.severity() + " " + issue.sheet() + "!"
                + issue.excelRow() + " " + issue.field() + ": " + issue.message());
    }
}
```

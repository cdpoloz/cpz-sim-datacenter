# Integrated rack cooling: examples and final verification

The backend implements rack-local air, configurable heat rejection, equipment
controls, completed-step snapshots, and electrical power/energy accounting.
The companion [Processing UI](https://github.com/cdpoloz/cpz-sim-datacenter-ui)
implements the selected-rack control and readouts. Physical calibration remains
pending independent measurements.

## Reproduce the controlled comparison

Use the Java release in `pom.xml` (Java 26 for this revision). Install the matching
`cpz-sim-foundation` first. From the backend repository root:

```bash
mvn clean install
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" \
  com.cpz.sim.datacenter.example.RackCoolingComparisonDemo \
  data/config/rack-cooling-comparison.json 60
```

The command opens no window and emits CSV. The optional arguments are a template
JSON path and positive simulated minutes (default 60). Redirect stdout to save a
new comparison. The checked-in [CSV](rack-cooling-comparison.csv) was generated
with this command on 2026-10-27.

`data/config/rack-cooling-comparison.json` contains two identical racks, `C01-R01`
and `C01-R02`, each with two identical 100–300 W servers. Initial server temperature
is 30 °C; local and common room air start at 24 °C. The runner applies fixed 50%
utilization, equivalent to 200 W per server and 800 W IT overall. It intentionally
does not apply workload factors or noise. Equivalent power-driven input is tested.

Only `C01-R01` has an optional local unit. The local air capacity is 10 kJ/°C,
room exchange is 20 W/°C, airflow is 0.1 m³/s, supply is 18 °C, nominal cooling
is 1,000 W, and enabled electrical power is 250 W. The room capacity is 200 kJ/°C;
environmental heat transfer is zero. Optional room supply/exhaust electricity is
500 W + 100 W, with a 1,000 W nominal room cooling path. All values are synthetic.

The runner creates six fresh simulations from the template:

| Scenario | Rack unit / local air node | Heat rejection | Room SUPPLY/EXHAUST |
| --- | --- | --- | --- |
| `NO_UNIT` | Absent / absent | None | Off |
| `DISABLED` | Off / present | None while off | Off |
| `ROOM` | On / present | Common room | Off |
| `EXTERNAL` | On / present | Outside modeled room | Off |
| `ROOM_WITH_HVAC` | On / present | Common room | Both on |
| `EXTERNAL_WITH_HVAC` | On / present | Outside modeled room | Both on |

`DISABLED` retains the configured enclosure air capacity and conductance. It is
not thermally equivalent to `NO_UNIT`, whose servers exchange directly with room
air. Removing an installation and switching off its cooling are different cases.

## Results after 60 minutes

Temperatures rounded to three decimals; cumulative energy is in Wh. `R01 servers`
and `R02 servers` are internal server proxies, distinct from air temperatures.

| Scenario | Room air °C | R01 local air °C | R01 servers °C | R02 servers °C | Cooling electrical W | Facility Wh | PUE |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| NO_UNIT | 35.573 | — | 58.270 | 58.270 | 0 | 800 | 1.0000 |
| DISABLED | 33.747 | 49.224 | 71.188 | 56.645 | 0 | 800 | 1.0000 |
| ROOM | 40.372 | 23.901 | 48.385 | 62.267 | 250 | 1050 | 1.3125 |
| EXTERNAL | 28.495 | 22.288 | 47.081 | 52.592 | 250 | 1050 | 1.3125 |
| ROOM_WITH_HVAC | 23.292 | 21.573 | 46.471 | 48.117 | 850 | 1650 | 2.0625 |
| EXTERNAL_WITH_HVAC | 19.981 | 21.110 | 46.040 | 45.016 | 850 | 1650 | 2.0625 |

The ROOM case cools its rack while warming the common room and the comparison
rack. It returns removed rack heat plus its electrical dissipation to the room.
EXTERNAL exports that sum instead. Identical electrical power gives the same PUE
in both cases even though room temperatures differ; PUE alone cannot describe
where heat goes or how well a rack is cooled.

With strong room cooling in EXTERNAL_WITH_HVAC, the directly room-cooled R02
servers are slightly cooler than R01. The local rack has its own air node and
limited passive exchange, so installing a unit does not guarantee a lower server
temperature under every room condition. The DISABLED case also demonstrates the
extra resistance of the configured local enclosure. These outcomes depend on the
chosen conductances, capacities and loads; they are not predictions for hardware.

Instantaneous PUE and cumulative energy PUE match here because all electrical
loads stay constant. With changing schedules they need not match. The CSV also
includes cumulative rack heat removal and heat rejected to each destination.
Thermal removal is not electrical consumption.

## Energy boundary

The check independently reconstructs stored energy from initial/final server,
local-air and room temperatures and their configured capacities. For these cases:

```text
IT input + rack electrical input
  - room cooling heat removal - rack heat rejected externally
  = change in server + rack-air + room-air stored energy
```

ROOM rejection is an internal transfer. Room HVAC motor electricity is counted
in facility energy but is outside the existing room thermal boundary; it is not
added as room motor heat. Environmental loss is zero only in this fixture. The
largest absolute per-tick residual in the recorded run is below 1e-6 J. Tests
allow 2e-5 J per tick and 1e-3 J for independently reconstructed hour-long storage.
These are numerical checks, not physical accuracy bounds.

## Verification and remaining checks

`RackCoolingComparisonDemoTest` runs all six JSON-based scenarios for an hour,
verifies both heat destinations, electrical totals and PUE, independent storage
conservation, and matched utilization/power trajectories. Existing independent
matrix and analytical tests remain in [numerical validation](rack-cooling-validation.md).

The UI repository supplies `RackCoolingExampleConfigurations`, which generates
six complete variants from its own 8-column layout. Its tests load and step every
variant through the actual UI thermal pipeline. The small two-rack backend JSON
is not a replacement for the fixed-layout UI dataset.

Implementation verification at this stage: backend `mvn clean install` and UI
`mvn clean test`; standalone backend CSV execution and UI configuration generator
execution. The prior stage also rendered the rack controls offscreen with
Processing Java2D. Interactive P2D desktop verification remains a manual check on
the user's graphical machine; see the companion UI's scenario guide.

Physical measurements, calibration, humidity, spatial airflow, separate internal
fan heat allocation, rack-unit occupancy and modulating control remain outside
this implementation.

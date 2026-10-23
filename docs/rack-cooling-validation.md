# Rack cooling numerical validation (stage 5)

Stage 5 verifies the server/rack/room thermal implementation introduced in
stage 4. It adds independent reference calculations and operating scenarios;
it does not change production thermal equations or claim calibration against
measured equipment. Parameters in these tests are controlled synthetic fixtures.

## Reproduce

Use the Java release declared in `pom.xml` (currently Java 26) and make the
matching `cpz-sim-foundation` dependency available in the local Maven repository.
From the backend repository, run:

```sh
mvn clean test
```

The complete suite includes configuration, equipment, pre-existing room thermal
regressions, stage 4 rack integration and the new stage 5 verification suite.
Use a clean build to ensure every newly added test is compiled.

## Independent full-node reference

`RackThermalBalanceValidationTest` uses a six-node reference network:

- Three server internal temperature nodes, including two different thermal models.
- Two local rack-air nodes, one per physical column/rack pair.
- One common room-air node; the third server connects directly to it.

The reference assembles the full backward-Euler matrix from thermal capacities,
server-to-air conductances, rack-to-room conductances and room-to-environment
exchange. It enumerates inactive, airflow-limited and capacity-limited cooling
branches, solves each candidate using Gaussian elimination with pivoting, and
accepts the candidate that satisfies the branch inequalities.

It does not call `CoupledRackAirSolver`, use its scalar room residual or reuse
the production server-elimination coefficients. This provides an independent
linear-algebra check of the simultaneous discrete equations. It shares the
specified physical equations, so it cannot establish that those equations match
a real installation.

The parameterized suite contains 32 combinations:

- All four on/off combinations of two rack units.
- All four ROOM/EXTERNAL destination combinations.
- External tick lengths of 0.25 s and 2.4 s.

The 2.4 s case exercises the production subdivision into three 0.8 s substeps.
Fixture profiles include local air below supply temperature, capacity-limited
cooling, airflow-limited cooling, a server colder than its surrounding air and
different thermal capacities by server model. Each combination advances three
ticks and compares all six resulting temperatures.

The reference also independently integrates server-to-air transfer, rack
cooling, rack heat rejection by destination, passive rack-to-room exchange and
environment loss. The tests compare these with the backend ledger, not only
with its computed global residual.

## Operating and analytical checks

| Check | Verification |
| --- | --- |
| Dynamic two-hour scenario | 120 one-minute ticks, both rejection destinations, independent unit schedules, a server shutdown interval and a later reduced IT load |
| Storage accounting | Server, rack-air and room-air energy changes recomputed from capacities and before/after temperatures |
| Global conservation | IT and rack electricity minus external rejection, room cooling removal and environment loss equal the total storage change |
| Rejection identity | Local refrigeration plus rack unit electricity is assigned once to ROOM or EXTERNAL |
| Configuration order | Reversing rack unit definitions preserves temperatures and heat removal for the same physical rack identities |
| Input modes | UTILIZATION_DRIVEN and POWER_DRIVEN produce equal thermal trajectories when they generate equal IT power |
| Continuous analytical limit | A passive two-air-node network converges toward its exponential exact solution when the step is halved from 1 s to 0.5 s to 0.25 s |
| Rejection and retry | A rejected oversized tick preserves the previous successful capture and all temperatures; a subsequent valid retry matches an uninterrupted reference run |

The passive analytical check is independent of the discrete full-matrix
reference: its equilibrium follows the capacity-weighted mean temperature, and
the temperature difference decays with rate `G * (1/C_rack + 1/C_room)`.
The expected backward-Euler time-discretization error decreases with refinement;
finite-step results are not asserted to equal the continuous solution exactly.

## Tolerances

| Quantity | Test tolerance |
| --- | --- |
| Full-matrix temperature comparisons | 2e-10 °C, absolute |
| Per-tick integrated transfers, storage and residual | 2e-5 J, absolute |
| Accumulated two-hour global balance | 1e-3 J, absolute |
| Unit-order temperature comparison | 1e-9 °C, absolute |
| Continuous passive test | Each halving of step must reduce temperature error below 60% of its previous value; total stored energy is also checked |

These tolerances describe the controlled verification cases. They are not
accuracy specifications for arbitrary input magnitudes or real installations.
The production finite-value checks and scale-aware energy tolerance remain as
described in [stage 4 thermal integration](temperature.md#rack-air-thermal-integration-stage-4).

## Coverage and remaining boundaries

The stage 4 suite additionally checks isolated analytical rack cooling,
room SUPPLY/EXHAUST combined with local cooling, signed passive exchange,
offline-server heat release, empty racks, initial/reset behavior, invalid
chronology, late snapshot failure and numerically unresolvable parameters.
The legacy suite continues to cover configurations without local rack air.

Stage 5 verifies numerical consistency and energy accounting for these modeled
boundaries. Physical calibration, spatial airflow, humidity, separate fan motor
heat allocation and measured cooling performance remain outside this validation.

Rack-specific operational/history snapshots and complete facility power,
accumulated energy and PUE aggregation are the next integration stage. Room-only
cooling snapshots still exclude rack unit electricity; use the explicit rack
thermal ledger and equipment power accessors until that integration is complete.

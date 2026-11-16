# Per-model thermal protection

The optional `serverModels[].thermalProtection` block defines simulated equipment
protection. These settings belong to each reusable equipment model, independently
of the display/health alert thresholds. Installed servers referencing a model
inherit its complete profile; different models can use different profiles.

This is a simplified protection controller for the representative internal server
temperature. It does not simulate CPU junction sensors, frequency governors,
component-level failure, firmware, fan curves, or irreversible damage.

## JSON contract

Add the following object to a server model alongside its power and thermal
properties. Every field is explicit and mandatory when the object is present.
These values are illustrative scenario inputs, not manufacturer specifications.

```json
"thermalProtection": {
  "enabled": true,
  "throttleTemperatureCelsius": 90,
  "throttleRecoveryTemperatureCelsius": 85,
  "throttledMaxUtilization": 0.4,
  "shutdownTemperatureCelsius": 105,
  "restartTemperatureCelsius": 70,
  "minimumShutdownSeconds": 120,
  "restartPolicy": "MANUAL",
  "shutdownPowerWatts": 5
}
```

| Field | Meaning |
| --- | --- |
| `enabled` | Apply this profile; `false` retains the unprotected experiment. |
| `throttleTemperatureCelsius` | Enter THROTTLED at or above this temperature. |
| `throttleRecoveryTemperatureCelsius` | Leave THROTTLED at or below this lower temperature. |
| `throttledMaxUtilization` | Delivered utilization cap, a fraction in `[0, 1)`. |
| `shutdownTemperatureCelsius` | Enter SHUTDOWN at or above this temperature, taking priority over throttling. |
| `restartTemperatureCelsius` | Maximum temperature permitted for restart. |
| `minimumShutdownSeconds` | Minimum simulated time in SHUTDOWN before restart. |
| `restartPolicy` | `MANUAL` requires a request; `AUTOMATIC` retries when recovery conditions hold. |
| `shutdownPowerWatts` | Standby electrical power and generated heat while thermally shut down. |

Temperature values must be finite, with:

`restartTemperatureCelsius <= throttleRecoveryTemperatureCelsius < throttleTemperatureCelsius < shutdownTemperatureCelsius`

Off time and standby watts must be finite and non-negative. Standby power cannot
exceed the model's idle power. Unknown keys, missing fields, explicit nulls,
coerced string/boolean/numeric types and invalid enum values are rejected.
These requirements also apply to a present but disabled profile so enabling it
cannot reveal an incomplete configuration. Omitting the whole object preserves
legacy behavior and the old Java constructor signatures remain available.

For customer equipment, obtain thermal thresholds, recovery policy, standby
power and the intended sensor/temperature definition from its specifications or
measurements. A CPU Tjunction limit must not be assigned directly to a calibrated
representative-server model without establishing what that temperature means.
Use a separate model code where equipment requires a different protection profile.

## Effective load and energy

NORMAL has no protection cap. In THROTTLED:

`powerCapWatts = idlePowerWatts + throttledMaxUtilization * (maxPowerWatts - idlePowerWatts)`

Utilization-driven power is computed from requested workload and capped at this
power limit; delivered utilization is capped at the configured fraction. The
limit never increases a lower demand. Repeated ticks apply a cap, not cumulative
multiplication. `Server.getRequestedUtilization()` and
`Server.getRequestedPowerWatts()` expose the demand separately from delivered
`getUtilization()` and `getCurrentPowerWatts()`.

A power-driven source supplies requested power before protection; the delivered
power is capped and utilization is inferred from that delivered value. This is
an active simulation of a protective actuator. Disable protection when replaying
measured power that must remain authoritative and unmodified; enabled protection
does not claim to reconstruct the real firmware's behavior from telemetry.

SHUTDOWN exposes `HardwareStatus.OFFLINE`, zero delivered utilization and the
configured standby power. Workload/power sources cannot re-energize it. Its
thermal mass continues exchanging heat with the room/rack air, including standby
heat, so cooling is not instantaneous. A hot environment can continue heating an
already shut-down machine; no temperature is clipped.

The same effective power is used by cooling loads, temperature integration,
energy accounting and snapshots. In the coupled model the protection decision
is held throughout a tick, preserving the existing energy ledger and solver.

## Pipeline and control resolution

`DatacenterInputPipelineFactory` automatically registers
`ServerThermalProtectionSystem` before the workload/power input systems whenever
an enabled profile exists in a power-derived mode. Hand-assembled pipelines must
register that system explicitly **before** workload, power, cooling, and thermal
integration, using the same `Datacenter` and `TemperatureSystem`.

The controller reads the last completed temperature at the start of each tick.
The tick start is `elapsedSeconds - deltaSeconds`; off-time uses simulated time.
A threshold crossed during integration is acted on at the next tick. This is a
sample-and-hold controller, not a sub-tick hardware interrupt. Choose sufficiently
short simulation ticks when studying protection transients; long ticks can
overshoot a threshold. Advancing only UI frames or pausing does not advance the
controller or its minimum-off timer.

`RackTemperatureInputSystem` evaluates the current observation before setting
inferred power, keeps sampling protection-owned shutdowns, and never changes the
observed temperature. Thus a low reading can permit recovery, while a high
reading remains visible even after simulated shutdown. `TemperatureInputSystem`
also evaluates its observed server temperatures. These modes represent observed
temperatures with hypothetical protection effects on operational state; they do
not predict the temperature response of the real equipment to a simulated action.

## Restart and operator ownership

Automatic restart requires both sufficient cooling and the minimum off time.
Manual restart requires those conditions **and** a queued request:

```java
server.requestThermalRestart();
```

The request waits until all recovery conditions hold. Directly forcing
`setStatus(OK)` or `setStatus(ALERT)` while thermally shut down is rejected.
An explicit operator `setStatus(OFFLINE)` takes ownership of availability and
cancels automatic thermal restart. A server initially or manually offline is
never automatically powered on. A simulation reset clears protection state and
restores availability only for shutdowns owned by protection.

`Server.getThermalProtectionState()` and
`ServerHealthSnapshot.thermalProtectionState()` expose NORMAL, THROTTLED or
SHUTDOWN independently of `thermalAlertLevel()`. Existing health alert thresholds
and hysteresis are unchanged. Offline health clears its usual alert reasons;
consumers should use SHUTDOWN to distinguish thermal protection from a manually
offline server. No new `HardwareStatus` enum values are introduced.

## Reproducible example and checks

`data/config/thermal-protection-demo.json` is a complete synthetic configuration:
two protected servers in a room with no active cooling, manual restart and 5 W
standby per server. Load through `JsonDatacenterConfigLoader` and assemble with
`DatacenterFactory`, `CoupledRoomTemperatureSystemFactory` and
`DatacenterInputPipelineFactory`. Drive full workload with short ticks. Change
only `enabled` to `false` for an otherwise identical unprotected run.

`ThermalProtectionIntegrationTest` compares four simulated hours without cooling,
checks throttling and shutdown against the unprotected temperatures above 150 °C,
and verifies energy conservation and agreement with accumulated electricity.
It also checks legacy power-driven control and observed rack-temperature
recovery. `ServerThermalProtectionTest` covers manual/automatic recovery,
hysteresis, demand capping, repeated ticks, standby power and operator ownership.
`ThermalProtectionConfigTest` verifies strict JSON parsing, ranges, independent
profiles, factory propagation and backward compatibility.

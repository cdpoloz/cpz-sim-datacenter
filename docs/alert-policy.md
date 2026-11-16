# Operator-facing alert policy

The optional top-level `alertPolicy` block selects which evaluated health reasons
are actionable in an operator-facing view:

```json
"alertPolicy": {
  "highUtilizationEnabled": false
}
```

The block is loaded by `JsonDatacenterConfigLoader` and exposed through
`DatacenterDefinition.alertPolicy()`. When present, `highUtilizationEnabled`
must be an explicit JSON boolean; null, missing, string/numeric values and
unknown fields are rejected. Omitting the whole block preserves legacy
visibility (`true`), including existing Java constructors.

`ServerAlertPolicy.visibleReasons()` returns an immutable selection of raw
reasons. With utilization disabled, HIGH_UTILIZATION alone is excluded;
HIGH_TEMPERATURE and all other diagnostic reasons remain eligible. A mixed
utilization/temperature condition therefore keeps its thermal reason.

This policy does not change workload, power, thermal protection, health
thresholds or diagnostic snapshots. `ServerHealthSystem` continues evaluating
HIGH_UTILIZATION and the raw hardware status may remain ALERT. Consumers must
apply the policy consistently to operator notifications, visible status icons
and system-summary severity rather than interpreting the raw ALERT status as
an unfiltered operator alarm.

THROTTLED and SHUTDOWN are independent protection states and are not disabled
by the utilization switch. The UI retains their notifications and severity.
Utilization remains available as a normal metric.

The policy is preserved when a dataset replay changes input mode or copies its
configuration. The synthetic thermal-protection example and the current O&M UI
configuration explicitly disable high-utilization notifications. Customer
configurations can enable them by changing the boolean to true.

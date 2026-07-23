# Workloads

Workload represents the utilization of each server on a tick. Valid utilization is
within the `[0, 1]` range.

## WorkloadSource

`WorkloadSource` is a functional interface:

```java
float getUtilization(Server server, SimulationTick tick);
```

It also defines `reset()` with an empty default implementation.

`WorkloadSystem` calls `WorkloadSource` for each operational server and assigns the
result to `Server.setUtilization(...)`.

Special rule:

- If the server is `OFFLINE`, `WorkloadSystem` forces `utilization = 0.0f`.
- For `OFFLINE` servers, `WorkloadSystem` does not query the `WorkloadSource`.

## NoiseWorkloadSource

`NoiseWorkloadSource` adapts a `NoiseSource` from `cpz-utils` to `WorkloadSource`.
It receives:

- `NoiseSource noiseSource`
- `double speed`
- `float minUtilization`
- `float maxUtilization`

Example with `FractalNoise`:

```java
FractalNoise fractalNoise = new FractalNoise(
        new PerlinNoise(1234L),
        5,
        1.0f,
        2.0f,
        0.5f
);

WorkloadSource workload = new NoiseWorkloadSource(
        fractalNoise,
        0.001,
        0.2f,
        0.9f
);
```

The source uses simulated time (`tick.elapsedSeconds()`), speed, and a deterministic
offset derived from `server.getCode()` so different servers do not have exactly the
same curve.

Recommended use:

- Simulations with variable load.
- Time-varying demo scenarios.
- Reproducible loads using fixed seeds in the noise source.

## ScaledWorkloadSource

`ScaledWorkloadSource` wraps another `WorkloadSource` and multiplies its result by
a per-server factor.

```java
FractalNoise fractalNoise = new FractalNoise(
        new PerlinNoise(1234L),
        5,
        1.0f,
        2.0f,
        0.5f
);
WorkloadSource base = new NoiseWorkloadSource(fractalNoise, 0.001, 0.2f, 0.9f);
ServerWorkloadFactorProvider factors = server -> 1.5f;
WorkloadSource workload = new ScaledWorkloadSource(base, factors);
```

The result is clamped to `[0, 1]`:

```text
clamp(baseUtilization * factor, 0.0, 1.0)
```

## workloadFactor from JSON

JSON defines `workloadFactor` inside each `servers` entry. To use it:

```java
DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(configPath);
Datacenter datacenter = new DatacenterFactory().create(definition);

FractalNoise fractalNoise = new FractalNoise(
        new PerlinNoise(1234L),
        5,
        1.0f,
        2.0f,
        0.5f
);
WorkloadSource base = new NoiseWorkloadSource(fractalNoise, 0.001, 0.2f, 0.9f);
ServerWorkloadFactorProvider factorProvider =
        new WorkloadFactorProviderFactory().create(definition);
WorkloadSource workload = new ScaledWorkloadSource(base, factorProvider);

WorkloadSystem workloadSystem = new WorkloadSystem(datacenter, workload);
```

`WorkloadFactorProviderFactory` creates factors by server code. The code is derived
as `<column>-<rackCode>-<slot>`, for example `C01-R01-S01` or
`C01-RACK-A01-R01-GPU-A`. This avoids collisions when the same rack code and slot
text appear in different columns. The slot portion is the exact opaque slot code
declared by the rack.

## OFFLINE and Power

The load and power of an `OFFLINE` server are corrected in two steps:

1. `WorkloadSystem` assigns `0.0f` utilization and does not call `WorkloadSource`.
2. `PowerConsumptionSystem` calls `Server.updatePowerConsumption()`, which assigns `0.0f` power.

To obtain coherent snapshots, register the systems using the causal order defined
in the architecture.

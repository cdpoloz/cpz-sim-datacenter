# JSON Configuration

JSON configuration is loaded with `JsonDatacenterConfigLoader` and represented as
`DatacenterDefinition`. Before building the domain, `DatacenterFactory` runs
`DatacenterConfigValidator`.

Loading example:

```java
Path configPath = Path.of("data/config/demo-datacenter-medium.json");
DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(configPath);
Datacenter datacenter = new DatacenterFactory().create(definition);
```

## General Structure

```json
{
  "name": "Demo Datacenter",
  "layout": {
    "racks": []
  },
  "serverModels": [],
  "servers": []
}
```

Main fields:

- `name`: non-blank datacenter name.
- `layout.racks`: available physical infrastructure.
- `serverModels`: server model catalog.
- `servers`: servers installed in specific racks and slots.

## layout.racks

Each rack is defined as:

```json
{
  "code": "RACK-A01-R01",
  "column": "A01",
  "row": "R01",
  "slotCount": 42
}
```

Rules:

- `code`, `column`, and `row` cannot be null or blank.
- `code` must be unique.
- `slotCount` must be greater than `0`.
- A rack may have no associated servers; that represents empty physical infrastructure.

## serverModels

Each model is defined as:

```json
{
  "modelCode": "SRV-DEMO-001",
  "manufacturer": "CPZ",
  "model": "Demo Server",
  "idlePowerWatts": 100.0,
  "maxPowerWatts": 300.0
}
```

Rules:

- `modelCode`, `manufacturer`, and `model` cannot be null or blank.
- `modelCode` must be unique.
- `idlePowerWatts` must be finite and `>= 0`.
- `maxPowerWatts` must be finite and greater than `idlePowerWatts`.

## servers

Each installed server is defined as:

```json
{
  "rackCode": "RACK-A01-R01",
  "slot": "U01",
  "modelCode": "SRV-DEMO-001",
  "status": "OK",
  "workloadFactor": 1.5
}
```

Fields:

- `rackCode`: code of an existing rack in `layout.racks`.
- `slot`: position inside the rack, using the `U<n>` format, for example `U01`.
- `modelCode`: code of an existing model in `serverModels`.
- `status`: `HardwareStatus` value: `OK`, `ALERT`, or `OFFLINE`.
- `workloadFactor`: non-negative factor used to scale workload per server.

Rules:

- `rackCode`, `slot`, `modelCode`, and `status` cannot be null or blank.
- `rackCode` must exist.
- `slot` must match the `U<n>` format.
- The slot number must be between `1` and the rack `slotCount`.
- Two servers cannot share the same location `rackCode + "-" + slot`.
- `status` must exactly match the enum name.
- `workloadFactor` must be finite and `>= 0`.
- If `ServerDefinition` is built from Java using the constructor without `workloadFactor`, the default value is `1.0f`.

## workloadFactor

`DatacenterFactory` creates the datacenter, but it does not apply `workloadFactor`
by itself. To use factors from JSON, create a `ServerWorkloadFactorProvider` with
`WorkloadFactorProviderFactory` and wrap the base source with `ScaledWorkloadSource`.

```java
FractalNoise fractalNoise = new FractalNoise(
        new PerlinNoise(1234L),
        5,
        1.0f,
        2.0f,
        0.5f
);
WorkloadSource base = new NoiseWorkloadSource(fractalNoise, 0.001, 0.2f, 0.9f);
ServerWorkloadFactorProvider factors =
        new WorkloadFactorProviderFactory().create(definition);
WorkloadSource workload = new ScaledWorkloadSource(base, factors);
```

`workloadFactor` can be greater than `1.0`. `ScaledWorkloadSource` multiplies the
base utilization by the factor and clamps the result to `[0, 1]`.

## Small Complete Example

```json
{
  "name": "Small Demo Datacenter",
  "layout": {
    "racks": [
      {
        "code": "RACK-A01-R01",
        "column": "A01",
        "row": "R01",
        "slotCount": 42
      },
      {
        "code": "RACK-A01-R02",
        "column": "A01",
        "row": "R02",
        "slotCount": 42
      }
    ]
  },
  "serverModels": [
    {
      "modelCode": "SRV-DEMO-001",
      "manufacturer": "CPZ",
      "model": "Demo Compute Server",
      "idlePowerWatts": 100.0,
      "maxPowerWatts": 300.0
    }
  ],
  "servers": [
    {
      "rackCode": "RACK-A01-R01",
      "slot": "U01",
      "modelCode": "SRV-DEMO-001",
      "status": "OK",
      "workloadFactor": 1.5
    },
    {
      "rackCode": "RACK-A01-R01",
      "slot": "U02",
      "modelCode": "SRV-DEMO-001",
      "status": "OFFLINE",
      "workloadFactor": 1.0
    }
  ]
}
```

In this example `RACK-A01-R02` is an empty rack. The server in `U02` is installed
but `OFFLINE`; after running `WorkloadSystem` and `PowerConsumptionSystem`, it must
end up with `0.0f` utilization and `0.0f` power.

## Temperature Configuration Status

The initial server temperature model is implemented in Java, but JSON temperature
configuration is not available yet in `0.1.0-alpha.1`.

Possible future configuration areas include:

- ambient temperature input for the simplified server model
- default initial server temperature
- thermal capacity and heat dissipation coefficients

These options are not currently part of the JSON schema.

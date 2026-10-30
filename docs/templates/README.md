# Initial configuration template

Use [datacenter-configuration-template.xlsx](datacenter-configuration-template.xlsx)
to collect a room's initial layout, equipment inventory and simulation settings
before preparing the backend JSON configuration.

## Status and compatibility

- Template version: `0.1-proposed` (initial capture format).
- Backend contract reviewed at commit
  [`a03c325`](https://github.com/cdpoloz/cpz-sim-datacenter/tree/a03c325ecac56827467f045b0d2548c37f0082ae).
- Scope: one room per workbook and configuration version, matching the current
  `DatacenterDefinition` contract.
- The backend does **not** load this workbook directly. Automatic Excel-to-JSON
  conversion is not implemented yet. Complete and review the workbook, then map
  its values to JSON and validate the result with `JsonDatacenterConfigLoader`
  and `DatacenterConfigValidator`.
- The workbook describes configuration only. Timestamped operational datasets
  and UI screen coordinates are outside its scope.

See [JSON Configuration](../configuration.md) for the executable contract.
The `Guide` and `Dictionary` worksheets explain completion rules, units and JSON
destinations. All workbook instructions, sheet names and dropdown labels are in
English; technical backend field names are preserved.

## Worksheets

| Worksheet | Content |
| --- | --- |
| Guide | Completion instructions, limitations and pinned references |
| General | Configuration identity, version, effective dates, room and input modes |
| Racks | Rack inventory, identified by column and rack code |
| Slots | All declared slots, including unoccupied slots |
| HotAisles | Hot-aisle assignments by column |
| Models | Reusable server models and electrical/thermal properties |
| Servers | Installed equipment, model references, initial status and role |
| Zones | Cooling-zone definitions using column and rack-code lists |
| RoomUnits | SUPPLY and EXHAUST equipment |
| Influences | Unit-to-zone weights |
| RackUnits | Dedicated rack cooling and local air-node properties |
| Parameters | Global thermal options, room air, cooling options and health thresholds |
| Dictionary | Field descriptions, units, requirements and JSON mapping |

## Completion rules

1. Enter values in the amber cells; data tables start in row 7. Extend the Excel
   tables when more rows are needed and keep technical headers unchanged.
2. Preserve identifiers as text. A rack is identified by `column + code`; a
   server by `column + rackCode + slot`. Slots and models referenced by servers
   must exist in their respective worksheets.
3. Use the stated units: watts, degrees Celsius, cubic meters per second and
   fractions from 0 to 1. Blank means undeclared; it must not be converted to zero.
4. Optional blocks may remain blank. When using a block, complete its dependent
   fields. Model thermal capacity and heat dissipation must be provided together
   or omitted together to inherit global values.
5. Use semicolons for lists in `Zones`. Its columns and rack codes form a
   Cartesian product, matching `CoolingZoneConfigDefinition`. Slot rows are
   grouped into explicit `layout.racks[].slots` arrays instead of `slotCount`.
6. Split `RoomUnits` into `cooling.supplyUnits` and `cooling.exhaustUnits` using
   `unitType`. Leave supply temperature and cooling capacity blank for EXHAUST.
   Group `Influences` by unit; positive weights must sum to 1 per unit.
7. Dedicated rack cooling requires `roomAirTemperature`, allows at most one unit
   per rack, and cannot be used with `TEMPERATURE_DRIVEN`.
8. Record provenance as `CLIENT`, `ESTIMATED` or `ASSUMED`; use `notes` to identify
   assumptions at field level when a row mixes sources. `REPO_DEMO` is reserved
   for repository examples; `TEMPLATE` identifies template metadata.
9. Keep `externalId`, `provenance`, `notes`, template/configuration versions,
   effective dates and time-zone metadata outside the current backend JSON.
   They belong to the exchange layer, not to new JSON properties.

The blank template intentionally includes no client equipment or assumed physical
parameters. The filled UI example used during design is distributed separately
and is not maintained in this backend repository.

## Maintenance

Whenever configuration definitions, loading rules or validation constraints change,
review this workbook and its dictionary in the same change. Update the template
version, reviewed backend revision and this README when compatibility changes.
Validate field mappings and references, check dropdowns and blank handling, and
visually review affected worksheets before committing an updated workbook.

package com.cpz.sim.datacenter.config.definition;

import java.util.List;

/**
 * Complete JSON definition of the datacenter cooling configuration.
 *
 * @param zones logical cooling zones containing installed servers
 * @param supplyUnits units that provide cooled air
 * @param exhaustUnits units that extract hot air
 * @param options physical and environmental cooling-system options
 *
 * @author CPZ
 */
public record CoolingConfigDefinition(
        List<CoolingZoneConfigDefinition> zones,
        List<SupplyCoolingUnitConfigDefinition> supplyUnits,
        List<ExhaustCoolingUnitConfigDefinition> exhaustUnits,
        CoolingSystemOptionsDefinition options
) {
}
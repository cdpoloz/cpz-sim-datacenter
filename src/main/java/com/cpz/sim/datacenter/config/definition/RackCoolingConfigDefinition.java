package com.cpz.sim.datacenter.config.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Optional dedicated rack cooling; independent of legacy room cooling zones. */
public record RackCoolingConfigDefinition(
        double airDensityKilogramsPerCubicMeter,
        double airSpecificHeatJoulesPerKilogramKelvin,
        List<RackCoolingUnitConfigDefinition> units
) {
    public RackCoolingConfigDefinition {
        // Retain invalid null entries for the validator's contextual diagnostics.
        if (units != null) units = Collections.unmodifiableList(new ArrayList<>(units));
    }
}

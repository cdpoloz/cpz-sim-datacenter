package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.RackLocation;

import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves rack columns from the standard demo layout to hot-aisle codes.
 * Prefer {@link ConfiguredHotAisleCodeResolver} for arbitrary layouts.
 *
 * @author CPZ
 */
public final class StandardHotAisleCodeResolver implements Function<RackLocation, String> {

    @Override
    public String apply(RackLocation rackLocation) {
        Objects.requireNonNull(rackLocation, "rackLocation must not be null");
        return switch (rackLocation.column()) {
            case "C01" -> "HA01";
            case "C02", "C03" -> "HA02";
            case "C04", "C05" -> "HA03";
            case "C06", "C07" -> "HA04";
            case "C08" -> "HA05";
            default -> throw new IllegalArgumentException(
                    "Unknown column for standard hot aisle resolution: " + rackLocation.column()
            );
        };
    }
}

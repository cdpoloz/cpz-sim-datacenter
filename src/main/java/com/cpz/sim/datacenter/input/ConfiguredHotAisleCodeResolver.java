package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.config.definition.HotAisleDefinition;
import com.cpz.sim.datacenter.model.RackLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves rack columns to hot-aisle codes declared by layout configuration.
 *
 * @author CPZ
 */
public final class ConfiguredHotAisleCodeResolver implements Function<RackLocation, String> {

    private final Map<String, String> hotAisleCodeByColumn;

    public ConfiguredHotAisleCodeResolver(List<HotAisleDefinition> hotAisles) {
        Objects.requireNonNull(hotAisles, "hotAisles must not be null");
        this.hotAisleCodeByColumn = Map.copyOf(createHotAisleCodeByColumn(hotAisles));
    }

    @Override
    public String apply(RackLocation rackLocation) {
        Objects.requireNonNull(rackLocation, "rackLocation must not be null");
        String hotAisleCode = hotAisleCodeByColumn.get(rackLocation.column());
        if (hotAisleCode == null) {
            throw new IllegalArgumentException(
                    "No configured hot aisle for rack column: " + rackLocation.column()
            );
        }
        return hotAisleCode;
    }

    private static Map<String, String> createHotAisleCodeByColumn(List<HotAisleDefinition> hotAisles) {
        Map<String, String> codeByColumn = new HashMap<>();
        for (HotAisleDefinition hotAisle : hotAisles) {
            for (String column : hotAisle.columns()) {
                codeByColumn.put(column, hotAisle.code());
            }
        }
        return codeByColumn;
    }
}

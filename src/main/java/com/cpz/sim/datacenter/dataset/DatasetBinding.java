package com.cpz.sim.datacenter.dataset;

import com.cpz.sim.datacenter.model.Datacenter;
import java.util.Objects;
import java.util.Set;

/** Exchange identity is supplied explicitly: backend configuration JSON has no version fields. */
public record DatasetBinding(String configurationId, String configurationVersion, String roomCode,
                             Datacenter datacenter, Set<String> coolingUnitKeys) {
    public DatasetBinding(String id, String version, String room, Datacenter datacenter) {
        this(id, version, room, datacenter, Set.of());
    }
    public DatasetBinding {
        for (String value : new String[]{configurationId, configurationVersion, roomCode})
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Binding identifiers must be non-blank");
        Objects.requireNonNull(datacenter, "datacenter");
        coolingUnitKeys = Set.copyOf(coolingUnitKeys);
    }
}

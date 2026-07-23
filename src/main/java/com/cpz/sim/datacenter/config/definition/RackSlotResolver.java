package com.cpz.sim.datacenter.config.definition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves the effective, ordered slot codes declared by a rack definition.
 *
 * @author CPZ
 */
public final class RackSlotResolver {

    private RackSlotResolver() {
    }

    public static List<String> resolveSlotCodes(RackDefinition rackDefinition) {
        Objects.requireNonNull(rackDefinition, "rackDefinition cannot be null");
        if (rackDefinition.hasSlots()) return List.copyOf(rackDefinition.slots());
        return legacySlotCodes(rackDefinition.slotCount());
    }

    public static List<String> legacySlotCodes(int slotCount) {
        List<String> slots = new ArrayList<>();
        for (int i = 1; i <= slotCount; i++) {
            slots.add("U" + String.format("%02d", i));
        }
        return List.copyOf(slots);
    }
}

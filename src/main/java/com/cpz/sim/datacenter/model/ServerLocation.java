package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * @author CPZ
 */
public record ServerLocation(
        RackCode rackCode,
        String slot
) {

    public ServerLocation {
        Objects.requireNonNull(rackCode, "rackCode cannot be null");
        Objects.requireNonNull(slot, "slot cannot be null");
        if (slot.isBlank()) throw new IllegalArgumentException("slot cannot be blank");
    }

    public String code() {
        return rackCode.value() + "-" + slot;
    }
}

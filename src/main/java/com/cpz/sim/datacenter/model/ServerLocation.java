package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * Physical server identity inside a datacenter.
 *
 * <p>{@code column + rackCode + slot} identifies an installed server position.
 * Slot is an opaque code declared by the rack.
 *
 * @author CPZ
 */
public record ServerLocation(
        String column,
        RackCode rackCode,
        String slot
) {

    public ServerLocation {
        Objects.requireNonNull(column, "column cannot be null");
        Objects.requireNonNull(rackCode, "rackCode cannot be null");
        Objects.requireNonNull(slot, "slot cannot be null");
        if (column.isBlank()) throw new IllegalArgumentException("column cannot be blank");
        if (slot.isBlank()) throw new IllegalArgumentException("slot cannot be blank");
    }

    public ServerLocation(String column, String rackCode, String slot) {
        this(column, new RackCode(rackCode), slot);
    }

    public RackLocation rackLocation() {
        return new RackLocation(column, rackCode);
    }

    public String code() {
        return column + "-" + rackCode.value() + "-" + slot;
    }
}

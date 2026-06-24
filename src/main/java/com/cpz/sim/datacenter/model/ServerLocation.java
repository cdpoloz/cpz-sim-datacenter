package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * @author CPZ
 */
public record ServerLocation(
        Column column,
        Row row,
        Slot slot
) {

    public ServerLocation {
        Objects.requireNonNull(column, "column cannot be null");
        Objects.requireNonNull(row, "row cannot be null");
        Objects.requireNonNull(slot, "slot cannot be null");
    }

    public String code() {
        return column + "-" + row + "-" + slot;
    }
}

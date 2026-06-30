package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * @author CPZ
 */
public record RackLocation(
        String column,
        String row
) {

    public RackLocation {
        Objects.requireNonNull(column, "column cannot be null");
        Objects.requireNonNull(row, "row cannot be null");
        if (column.isBlank()) throw new IllegalArgumentException("column cannot be blank");
        if (row.isBlank()) throw new IllegalArgumentException("row cannot be blank");
    }
}

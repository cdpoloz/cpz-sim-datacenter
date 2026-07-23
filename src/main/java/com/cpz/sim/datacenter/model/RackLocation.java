package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * Physical rack identity inside a datacenter.
 *
 * <p>{@code column + rackCode} identifies a rack. {@code row} is rack metadata
 * stored by {@link Rack}; it is not part of this identity.
 *
 * @author CPZ
 */
public record RackLocation(
        String column,
        RackCode rackCode
) {

    public RackLocation {
        Objects.requireNonNull(column, "column cannot be null");
        Objects.requireNonNull(rackCode, "rackCode cannot be null");
        if (column.isBlank()) throw new IllegalArgumentException("column cannot be blank");
    }

    public RackLocation(String column, String rackCode) {
        this(column, new RackCode(rackCode));
    }

    public String code() {
        return column + "-" + rackCode.value();
    }
}

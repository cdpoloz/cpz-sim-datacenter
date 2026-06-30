package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * @author CPZ
 */
public record RackCode(String value) {

    public RackCode {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("value cannot be blank");
    }
}

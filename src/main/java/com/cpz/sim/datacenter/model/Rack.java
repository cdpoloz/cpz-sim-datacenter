package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * @author CPZ
 */
public final class Rack {

    private final RackCode code;
    private final RackLocation location;
    private final int slotCount;

    public Rack(RackCode code, RackLocation location, int slotCount) {
        this.code = Objects.requireNonNull(code, "code cannot be null");
        this.location = Objects.requireNonNull(location, "location cannot be null");
        if (slotCount <= 0) throw new IllegalArgumentException("slotCount must be greater than zero");
        this.slotCount = slotCount;
    }

    public RackCode getCode() {
        return code;
    }

    public RackLocation getLocation() {
        return location;
    }

    public int getSlotCount() {
        return slotCount;
    }
}

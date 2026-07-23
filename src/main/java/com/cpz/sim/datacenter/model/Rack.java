package com.cpz.sim.datacenter.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author CPZ
 */
public final class Rack {

    private final RackCode code;
    private final RackLocation location;
    private final String row;
    private final List<String> slotCodes;

    public Rack(RackLocation location, String row, int slotCount) {
        this(location, row, legacySlotCodes(slotCount));
    }

    public Rack(RackLocation location, String row, List<String> slotCodes) {
        this.location = Objects.requireNonNull(location, "location cannot be null");
        Objects.requireNonNull(row, "row cannot be null");
        if (row.isBlank()) throw new IllegalArgumentException("row cannot be blank");
        this.code = location.rackCode();
        this.row = row;
        this.slotCodes = validateSlotCodes(slotCodes);
    }

    public Rack(RackCode code, RackLocation legacyLocation, int slotCount) {
        this(new RackLocation(legacyLocation.column(), code), legacyLocation.rackCode().value(), slotCount);
    }

    public Rack(RackCode code, RackLocation legacyLocation, List<String> slotCodes) {
        this(new RackLocation(legacyLocation.column(), code), legacyLocation.rackCode().value(), slotCodes);
    }

    public Rack(RackCode code, String column, String row, int slotCount) {
        this(new RackLocation(column, code), row, slotCount);
    }

    public Rack(RackCode code, String column, String row, List<String> slotCodes) {
        this(new RackLocation(column, code), row, slotCodes);
    }

    private static List<String> legacySlotCodes(int slotCount) {
        if (slotCount <= 0) throw new IllegalArgumentException("slotCount must be greater than zero");
        List<String> slots = new ArrayList<>();
        for (int i = 1; i <= slotCount; i++) {
            slots.add("U" + String.format("%02d", i));
        }
        return slots;
    }

    private static List<String> validateSlotCodes(List<String> slotCodes) {
        Objects.requireNonNull(slotCodes, "slotCodes cannot be null");
        if (slotCodes.isEmpty()) throw new IllegalArgumentException("slotCodes cannot be empty");
        Set<String> uniqueSlotCodes = new HashSet<>();
        for (String slotCode : slotCodes) {
            Objects.requireNonNull(slotCode, "slotCodes cannot contain null elements");
            if (slotCode.isBlank()) throw new IllegalArgumentException("slotCodes cannot contain blank elements");
            if (!uniqueSlotCodes.add(slotCode))
                throw new IllegalArgumentException("duplicate slot code: " + slotCode);
        }
        return List.copyOf(slotCodes);
    }

    public RackCode getCode() {
        return code;
    }

    public RackLocation getLocation() {
        return location;
    }

    public String getColumn() {
        return location.column();
    }

    public String getRow() {
        return row;
    }

    public int getSlotCount() {
        return slotCodes.size();
    }

    public List<String> getSlotCodes() {
        return slotCodes;
    }

    public boolean hasSlot(String slotCode) {
        return slotCodes.contains(slotCode);
    }
}

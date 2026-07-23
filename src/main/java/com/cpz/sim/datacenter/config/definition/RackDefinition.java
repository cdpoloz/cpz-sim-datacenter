package com.cpz.sim.datacenter.config.definition;

import com.cpz.sim.datacenter.config.json.RackDefinitionDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author CPZ
 */
@JsonDeserialize(using = RackDefinitionDeserializer.class)
public final class RackDefinition {

    private final String code;
    private final String column;
    private final String row;
    private final Integer slotCount;
    private final List<String> slots;
    private final boolean slotCountPresent;
    private final boolean slotsPresent;

    public RackDefinition(String code, String column, String row, int slotCount) {
        this(code, column, row, slotCount, null, true, false);
    }

    public RackDefinition(String code, String column, String row, List<String> slots) {
        this(code, column, row, null, slots, false, true);
    }

    public RackDefinition(String code, String column, String row, Integer slotCount, List<String> slots) {
        this(code, column, row, slotCount, slots, slotCount != null, slots != null);
    }

    public RackDefinition(
            String code,
            String column,
            String row,
            Integer slotCount,
            List<String> slots,
            boolean slotCountPresent,
            boolean slotsPresent
    ) {
        this.code = code;
        this.column = column;
        this.row = row;
        this.slotCount = slotCount;
        this.slots = slots == null ? null : Collections.unmodifiableList(new ArrayList<>(slots));
        this.slotCountPresent = slotCountPresent;
        this.slotsPresent = slotsPresent;
    }

    public String code() {
        return code;
    }

    public String column() {
        return column;
    }

    public String row() {
        return row;
    }

    public Integer slotCount() {
        return slotCount;
    }

    public List<String> slots() {
        return slots;
    }

    public boolean hasSlotCount() {
        return slotCountPresent;
    }

    public boolean hasSlots() {
        return slotsPresent;
    }
}

package com.cpz.sim.datacenter.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class RackTest {

    @Test
    void shouldRejectNullRackCode() {
        assertThrows(NullPointerException.class, () -> new RackCode(null));
        assertThrows(NullPointerException.class, () -> new Rack(null, new RackLocation("A01", "R01"), 42));
    }

    @Test
    void shouldRejectBlankRackCode() {
        assertThrows(IllegalArgumentException.class, () -> new RackCode(" "));
    }

    @Test
    void shouldRejectInvalidRackLocation() {
        assertThrows(NullPointerException.class, () -> new RackLocation(null, "R01"));
        assertThrows(NullPointerException.class, () -> new RackLocation("A01", (RackCode) null));
        assertThrows(IllegalArgumentException.class, () -> new RackLocation(" ", "R01"));
        assertThrows(IllegalArgumentException.class, () -> new RackLocation("A01", " "));
        assertThrows(NullPointerException.class, () -> new Rack(new RackCode("RACK-A01-R01"), null, 42));
    }

    @Test
    void shouldRejectInvalidSlotCount() {
        RackCode code = new RackCode("RACK-A01-R01");
        RackLocation location = new RackLocation("A01", "R01");
        assertThrows(IllegalArgumentException.class, () -> new Rack(code, location, 0));
        assertThrows(IllegalArgumentException.class, () -> new Rack(code, location, -1));
    }

    @Test
    void shouldAcceptValidRack() {
        RackCode code = new RackCode("RACK-A01-R01");
        RackLocation location = new RackLocation("A01", "R01");
        Rack rack = new Rack(code, location, 42);
        assertEquals(code, rack.getCode());
        assertEquals(new RackLocation("A01", code), rack.getLocation());
        assertEquals("A01", rack.getColumn());
        assertEquals("R01", rack.getRow());
        assertEquals(42, rack.getSlotCount());
        assertEquals("U01", rack.getSlotCodes().getFirst());
        assertEquals("U42", rack.getSlotCodes().getLast());
        assertTrue(rack.hasSlot("U01"));
        assertFalse(rack.hasSlot("S01"));
    }

    @Test
    void shouldUseColumnAndRackCodeAsRackLocationIdentity() {
        RackLocation first = new RackLocation("C01", "R01");
        RackLocation equal = new RackLocation("C01", new RackCode("R01"));
        RackLocation differentColumn = new RackLocation("C02", "R01");
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertFalse(first.equals(differentColumn));
        assertEquals("C01-R01", first.code());
    }

    @Test
    void shouldPreserveLegacyPaddingForSlotCountsGreaterThanNinetyNine() {
        Rack rack = new Rack(new RackCode("RACK-A01-R01"), new RackLocation("A01", "R01"), 100);
        assertEquals("U01", rack.getSlotCodes().getFirst());
        assertEquals("U99", rack.getSlotCodes().get(98));
        assertEquals("U100", rack.getSlotCodes().get(99));
    }

    @Test
    void shouldAcceptExplicitSlotCodesAndPreserveOrder() {
        RackCode code = new RackCode("RACK-A01-R01");
        RackLocation location = new RackLocation("A01", "R01");
        Rack rack = new Rack(code, location, List.of("GPU-A", "NETWORK", "SPARE"));
        assertEquals(3, rack.getSlotCount());
        assertEquals(List.of("GPU-A", "NETWORK", "SPARE"), rack.getSlotCodes());
        assertTrue(rack.hasSlot("GPU-A"));
        assertTrue(rack.hasSlot("NETWORK"));
        assertTrue(rack.hasSlot("SPARE"));
    }

    @Test
    void shouldExposeImmutableSlotCodes() {
        List<String> slotCodes = new ArrayList<>(List.of("S01", "S02"));
        Rack rack = new Rack(new RackCode("RACK-A01-R01"), new RackLocation("A01", "R01"), slotCodes);
        slotCodes.add("S03");
        assertEquals(List.of("S01", "S02"), rack.getSlotCodes());
        assertThrows(UnsupportedOperationException.class, () -> rack.getSlotCodes().add("S03"));
    }

    @Test
    void shouldRejectInvalidExplicitSlotCodes() {
        RackCode code = new RackCode("RACK-A01-R01");
        RackLocation location = new RackLocation("A01", "R01");
        List<String> slotCodesWithNull = new ArrayList<>();
        slotCodesWithNull.add("S01");
        slotCodesWithNull.add(null);
        assertThrows(NullPointerException.class, () -> new Rack(code, location, null));
        assertThrows(IllegalArgumentException.class, () -> new Rack(code, location, List.of()));
        assertThrows(NullPointerException.class, () -> new Rack(code, location, slotCodesWithNull));
        assertThrows(IllegalArgumentException.class, () -> new Rack(code, location, List.of("S01", " ")));
        assertThrows(IllegalArgumentException.class, () -> new Rack(code, location, List.of("S01", "S01")));
    }

    @Test
    void shouldCompareSlotCodesCaseSensitively() {
        Rack rack = new Rack(
                new RackCode("RACK-A01-R01"),
                new RackLocation("A01", "R01"),
                List.of("GPU-A")
        );
        assertTrue(rack.hasSlot("GPU-A"));
        assertFalse(rack.hasSlot("gpu-a"));
    }
}

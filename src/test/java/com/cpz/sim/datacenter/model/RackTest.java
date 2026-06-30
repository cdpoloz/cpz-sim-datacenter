package com.cpz.sim.datacenter.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertThrows(NullPointerException.class, () -> new RackLocation("A01", null));
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
        assertEquals(location, rack.getLocation());
        assertEquals(42, rack.getSlotCount());
    }
}

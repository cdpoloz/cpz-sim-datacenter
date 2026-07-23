package com.cpz.sim.datacenter.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class ServerLocationTest {

    @Test
    void shouldRejectNullRackCode() {
        assertThrows(NullPointerException.class, () -> new ServerLocation("C01", (RackCode) null, "U01"));
    }

    @Test
    void shouldRejectInvalidColumn() {
        RackCode rackCode = new RackCode("R01");
        assertThrows(NullPointerException.class, () -> new ServerLocation(null, rackCode, "S01"));
        assertThrows(IllegalArgumentException.class, () -> new ServerLocation(" ", rackCode, "S01"));
    }

    @Test
    void shouldRejectInvalidSlot() {
        RackCode rackCode = new RackCode("R01");
        assertThrows(NullPointerException.class, () -> new ServerLocation("C01", rackCode, null));
        assertThrows(IllegalArgumentException.class, () -> new ServerLocation("C01", rackCode, " "));
    }

    @Test
    void shouldGenerateCode() {
        ServerLocation location = new ServerLocation("C01", new RackCode("R01"), "S01");
        assertEquals("C01-R01-S01", location.code());
        assertEquals(new RackLocation("C01", "R01"), location.rackLocation());
    }
}

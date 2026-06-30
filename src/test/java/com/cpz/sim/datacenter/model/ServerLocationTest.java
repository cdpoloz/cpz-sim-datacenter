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
        assertThrows(NullPointerException.class, () -> new ServerLocation(null, "U01"));
    }

    @Test
    void shouldRejectInvalidSlot() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        assertThrows(NullPointerException.class, () -> new ServerLocation(rackCode, null));
        assertThrows(IllegalArgumentException.class, () -> new ServerLocation(rackCode, " "));
    }

    @Test
    void shouldGenerateCode() {
        ServerLocation location = new ServerLocation(new RackCode("RACK-A01-R01"), "U01");
        assertEquals("RACK-A01-R01-U01", location.code());
    }
}

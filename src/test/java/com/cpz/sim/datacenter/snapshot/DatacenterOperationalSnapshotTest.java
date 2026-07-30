package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author CPZ
 */
class DatacenterOperationalSnapshotTest {

    private static final RackLocation RACK_LOCATION = new RackLocation("C01", new RackCode("R01"));

    private static RackOperationalSnapshot createRackSnapshot() {
        return new RackOperationalSnapshot(
                RACK_LOCATION,
                2,
                2,
                200.0,
                1000.0,
                640.0,
                60.0,
                0.60
        );
    }

    @Test
    void shouldDefensivelyCopyRackSnapshotsAndSupportLookup() {
        RackOperationalSnapshot rackSnapshot = createRackSnapshot();
        Map<RackLocation, RackOperationalSnapshot> racks = new LinkedHashMap<>();
        racks.put(RACK_LOCATION, rackSnapshot);
        DatacenterOperationalSnapshot snapshot = new DatacenterOperationalSnapshot(10L, 600.0, racks);
        racks.clear();
        assertEquals(1, snapshot.rackCount());
        assertTrue(snapshot.findRack(RACK_LOCATION).isPresent());
        assertSame(rackSnapshot, snapshot.getRack(RACK_LOCATION));
    }

    @Test
    void shouldReturnEmptyOptionalForUnknownRack() {
        DatacenterOperationalSnapshot snapshot =
                new DatacenterOperationalSnapshot(10L, 600.0, Map.of(RACK_LOCATION, createRackSnapshot()));
        RackLocation unknownLocation = new RackLocation("C01", new RackCode("R02"));
        assertFalse(snapshot.findRack(unknownLocation).isPresent());
    }

    @Test
    void shouldRejectGettingUnknownRack() {
        DatacenterOperationalSnapshot snapshot =
                new DatacenterOperationalSnapshot(10L, 600.0, Map.of(RACK_LOCATION, createRackSnapshot()));
        RackLocation unknownLocation = new RackLocation("C01", new RackCode("R02"));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> snapshot.getRack(unknownLocation)
        );
        assertEquals("No operational snapshot for rack: " + unknownLocation, exception.getMessage());
    }

    @Test
    void shouldRejectNegativeTickIndex() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshot(-1L, 600.0, Map.of())
        );
        assertEquals("tickIndex must be >= 0", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidElapsedSeconds() {
        double[] invalidValues = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double invalidValue : invalidValues) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DatacenterOperationalSnapshot(10L, invalidValue, Map.of())
            );
            assertEquals("elapsedSeconds must be finite and >= 0", exception.getMessage());
        }
    }

    @Test
    void shouldRejectNullRackMap() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new DatacenterOperationalSnapshot(10L, 600.0, null)
        );
        assertEquals("racks must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectNullRackLocation() {
        Map<RackLocation, RackOperationalSnapshot> racks = new HashMap<>();
        racks.put(null, createRackSnapshot());
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new DatacenterOperationalSnapshot(10L, 600.0, racks)
        );
        assertEquals("rack location must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectNullRackSnapshot() {
        Map<RackLocation, RackOperationalSnapshot> racks = new HashMap<>();
        racks.put(RACK_LOCATION, null);
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new DatacenterOperationalSnapshot(10L, 600.0, racks)
        );
        assertEquals("rack snapshot must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectRackSnapshotWithDifferentLocation() {
        RackLocation differentLocation = new RackLocation("C01", new RackCode("R02"));
        RackOperationalSnapshot rackSnapshot =
                new RackOperationalSnapshot(
                        differentLocation,
                        2,
                        2,
                        200.0,
                        1000.0,
                        640.0,
                        60.0,
                        0.60
                );
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterOperationalSnapshot(10L, 600.0, Map.of(RACK_LOCATION, rackSnapshot))
        );
        assertEquals("Rack map key does not match snapshot location: " + RACK_LOCATION, exception.getMessage());
    }
}
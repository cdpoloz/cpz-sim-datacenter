package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.RackLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable operational view of the datacenter for one simulation tick.
 *
 * @author CPZ
 */
public record DatacenterOperationalSnapshot(
        long tickIndex,
        double elapsedSeconds,
        Map<RackLocation, RackOperationalSnapshot> racks
) {

    public DatacenterOperationalSnapshot {
        if (tickIndex < 0L) throw new IllegalArgumentException("tickIndex must be >= 0");
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0)
            throw new IllegalArgumentException("elapsedSeconds must be finite and >= 0");
        Objects.requireNonNull(racks, "racks must not be null");
        for (Map.Entry<RackLocation, RackOperationalSnapshot> entry : racks.entrySet()) {
            RackLocation location = Objects.requireNonNull(entry.getKey(), "rack location must not be null");
            RackOperationalSnapshot rackSnapshot = Objects.requireNonNull(entry.getValue(), "rack snapshot must not be null");
            if (!location.equals(rackSnapshot.location()))
                throw new IllegalArgumentException("Rack map key does not match snapshot location: " + location);
        }
        racks = Map.copyOf(racks);
        racks.forEach((location, snapshot) -> {
            Objects.requireNonNull(location, "rack location must not be null");
            Objects.requireNonNull(snapshot, "rack snapshot must not be null");
            if (!location.equals(snapshot.location()))
                throw new IllegalArgumentException("Rack map key does not match snapshot location: " + location);
        });
    }

    public Optional<RackOperationalSnapshot> findRack(RackLocation location) {
        Objects.requireNonNull(location, "location must not be null");
        return Optional.ofNullable(racks.get(location));
    }

    public RackOperationalSnapshot getRack(RackLocation location) {
        return findRack(location).orElseThrow(() -> new IllegalArgumentException("No operational snapshot for rack: " + location));
    }

    public int rackCount() {
        return racks.size();
    }
}
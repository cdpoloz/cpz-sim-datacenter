package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.system.TemperatureSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Provides each server with the inlet-air temperature calculated for its
 * cooling zone in the latest cooling snapshot.
 *
 * <p>The cooling snapshot must be supplied through
 * {@link #updateSnapshot(CoolingSnapshot)} before the provider is consulted.
 * A newer snapshot may be supplied before every simulation tick.</p>
 *
 * <p>This adapter keeps {@link TemperatureSystem} independent from
 * {@link com.cpz.sim.datacenter.system.CoolingSystem}. It depends only on the
 * immutable cooling configuration and the latest cooling result.</p>
 *
 * @author CPZ
 */
public final class CoolingSnapshotTemperatureReferenceProvider implements ServerTemperatureReferenceProvider {

    private final Map<ServerLocation, String> zoneCodeByServerLocation;
    private volatile CoolingSnapshot currentSnapshot;

    /**
     * Creates a provider using the server-to-zone assignments declared in the
     * cooling configuration.
     *
     * @param configuration cooling configuration defining the zones
     *
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public CoolingSnapshotTemperatureReferenceProvider(CoolingConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        this.zoneCodeByServerLocation = createZoneCodeByServerLocation(configuration);
    }

    /**
     * Replaces the cooling snapshot used to resolve inlet-air temperatures.
     *
     * @param snapshot latest cooling snapshot
     *
     * @throws NullPointerException if {@code snapshot} is {@code null}
     */
    public void updateSnapshot(CoolingSnapshot snapshot) {
        this.currentSnapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
    }

    /**
     * Returns the inlet-air temperature of the cooling zone containing the
     * requested server.
     *
     * @param server installed server whose reference temperature is requested
     * @return zone inlet-air temperature in degrees Celsius
     *
     * @throws NullPointerException if {@code server} is {@code null}
     * @throws IllegalStateException if no cooling snapshot has been supplied,
     *         the server is not assigned to a cooling zone, or the current
     *         snapshot does not contain that zone
     */
    @Override
    public double temperatureCelsiusFor(Server server) {
        Objects.requireNonNull(server, "server must not be null");
        CoolingSnapshot snapshot = currentSnapshot;
        if (snapshot == null) throw new IllegalStateException("cooling snapshot has not been initialized");
        ServerLocation location = server.getLocation();
        String zoneCode = zoneCodeByServerLocation.get(location);
        if (zoneCode == null) throw new IllegalStateException("server location is not assigned to a cooling zone: " + location);
        CoolingZoneSnapshot zoneSnapshot = snapshot
                .findZone(zoneCode)
                .orElseThrow(() -> new IllegalStateException("cooling snapshot does not contain zone: " + zoneCode));
        return zoneSnapshot.inletAirTemperatureCelsius();
    }

    /**
     * Returns the currently installed cooling snapshot.
     *
     * @return latest snapshot, or {@code null} if none has been supplied yet
     */
    public CoolingSnapshot currentSnapshot() {
        return currentSnapshot;
    }

    private static Map<ServerLocation, String> createZoneCodeByServerLocation(CoolingConfiguration configuration) {
        Map<ServerLocation, String> result = new HashMap<>();
        for (CoolingZoneDefinition zone : configuration.zones()) {
            for (ServerLocation location : zone.serverLocations()) result.put(location, zone.code());
        }
        return Map.copyOf(result);
    }
}
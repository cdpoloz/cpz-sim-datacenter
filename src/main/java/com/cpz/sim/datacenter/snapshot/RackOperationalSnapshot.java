package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.RackLocation;

import java.util.Objects;

/**
 * Immutable operational metrics aggregated for one rack during one
 * simulation tick.
 *
 * <p>Installed capacity includes every server physically installed in the
 * rack. Operational averages include only servers whose calculated health
 * status is not {@code OFFLINE}.
 *
 * @param location                        rack physical location
 * @param installedServerCount            number of installed servers
 * @param onlineServerCount               number of installed servers not marked OFFLINE
 * @param idlePowerWatts                  accumulated idle power of all installed servers
 * @param maxPowerWatts                   accumulated maximum power of all installed servers
 * @param currentPowerWatts               accumulated current power of all installed servers
 * @param averageOnlineTemperatureCelsius average temperature of online servers
 * @param representativeTemperatureCelsius finite representative rack
 *                                         temperature for spatial or
 *                                         aggregate visualization
 * @param averageOnlineUtilization        average utilization of online servers
 * @author CPZ
 */
public record RackOperationalSnapshot(
        RackLocation location,
        int installedServerCount,
        int onlineServerCount,
        double idlePowerWatts,
        double maxPowerWatts,
        double currentPowerWatts,
        double averageOnlineTemperatureCelsius,
        double representativeTemperatureCelsius,
        double averageOnlineUtilization
) {

    public RackOperationalSnapshot {
        Objects.requireNonNull(location, "location must not be null");
        if (installedServerCount < 0)
            throw new IllegalArgumentException("installedServerCount must be >= 0");
        if (onlineServerCount < 0 || onlineServerCount > installedServerCount)
            throw new IllegalArgumentException("onlineServerCount must be between 0 and installedServerCount");
        requireNonNegativeFinite(idlePowerWatts, "idlePowerWatts");
        requireNonNegativeFinite(maxPowerWatts, "maxPowerWatts");
        requireNonNegativeFinite(currentPowerWatts, "currentPowerWatts");
        if (idlePowerWatts > maxPowerWatts)
            throw new IllegalArgumentException("idlePowerWatts must not exceed maxPowerWatts");
        if (currentPowerWatts > maxPowerWatts)
            throw new IllegalArgumentException("currentPowerWatts must not exceed maxPowerWatts");
        if (!Double.isFinite(representativeTemperatureCelsius))
            throw new IllegalArgumentException("representativeTemperatureCelsius must be finite");
        if (onlineServerCount == 0) {
            if (!Double.isNaN(averageOnlineTemperatureCelsius))
                throw new IllegalArgumentException("averageOnlineTemperatureCelsius must be NaN when there are no online servers");
            if (!Double.isNaN(averageOnlineUtilization))
                throw new IllegalArgumentException("averageOnlineUtilization must be NaN when there are no online servers");
        } else {
            if (!Double.isFinite(averageOnlineTemperatureCelsius))
                throw new IllegalArgumentException("averageOnlineTemperatureCelsius must be finite when online servers exist");
            if (!Double.isFinite(averageOnlineUtilization) || averageOnlineUtilization < 0.0 || averageOnlineUtilization > 1.0)
                throw new IllegalArgumentException("averageOnlineUtilization must be finite and between 0 and 1");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException(name + " must be finite and >= 0");
    }

    public boolean hasInstalledServers() {
        return installedServerCount > 0;
    }

    public boolean hasOnlineServers() {
        return onlineServerCount > 0;
    }
}

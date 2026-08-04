package com.cpz.sim.datacenter.snapshot;

import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable operational aggregate for an application-defined server group.
 *
 * @author CPZ
 */
public record ServerGroupOperationalSnapshot(
        String groupCode,
        int installedServerCount,
        int onlineServerCount,
        double idlePowerWatts,
        double maxPowerWatts,
        double currentPowerWatts,
        double averageOnlineTemperatureCelsius,
        double averageOnlineUtilization,
        double maximumTemperatureCelsius,
        Optional<ServerLocation> maximumTemperatureLocation
) {

    public ServerGroupOperationalSnapshot {
        Objects.requireNonNull(groupCode, "groupCode must not be null");
        if (groupCode.isBlank()) throw new IllegalArgumentException("groupCode must not be blank");
        if (installedServerCount < 0) throw new IllegalArgumentException("installedServerCount must be >= 0");
        if (onlineServerCount < 0 || onlineServerCount > installedServerCount)
            throw new IllegalArgumentException("onlineServerCount must be between 0 and installedServerCount");
        requireFiniteAndNonNegative(idlePowerWatts, "idlePowerWatts");
        requireFiniteAndNonNegative(maxPowerWatts, "maxPowerWatts");
        requireFiniteAndNonNegative(currentPowerWatts, "currentPowerWatts");
        if (idlePowerWatts > maxPowerWatts) throw new IllegalArgumentException("idlePowerWatts must not exceed maxPowerWatts");
        if (currentPowerWatts > maxPowerWatts) throw new IllegalArgumentException("currentPowerWatts must not exceed maxPowerWatts");
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
        Objects.requireNonNull(maximumTemperatureLocation, "maximumTemperatureLocation must not be null");
        if (installedServerCount == 0) {
            if (!Double.isNaN(maximumTemperatureCelsius))
                throw new IllegalArgumentException("maximumTemperatureCelsius must be NaN when there are no installed servers");
            if (maximumTemperatureLocation.isPresent())
                throw new IllegalArgumentException("maximumTemperatureLocation must be empty when there are no installed servers");
        } else {
            if (!Double.isFinite(maximumTemperatureCelsius))
                throw new IllegalArgumentException("maximumTemperatureCelsius must be finite when installed servers exist");
            if (maximumTemperatureLocation.isEmpty())
                throw new IllegalArgumentException("maximumTemperatureLocation must be present when installed servers exist");
        }
    }

    public boolean hasInstalledServers() {
        return installedServerCount > 0;
    }

    public boolean hasOnlineServers() {
        return onlineServerCount > 0;
    }

    private static void requireFiniteAndNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0)
            throw new IllegalArgumentException(fieldName + " must be finite and >= 0");
    }
}
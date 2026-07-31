package com.cpz.sim.datacenter.snapshot;

import java.util.Objects;

/**
 * Aggregated operational data for a datacenter column at a specific
 * simulation tick.
 *
 * <p>Temperature and utilization averages include online servers only.
 * When the column contains no online servers, both averages are represented
 * by {@link Double#NaN}.</p>
 *
 * @param columnCode column identifier
 * @param installedServerCount number of installed servers
 * @param onlineServerCount number of online servers
 * @param idlePowerWatts total idle power of the installed servers, in watts
 * @param maxPowerWatts total maximum power of the installed servers, in watts
 * @param currentPowerWatts current IT power of the column, in watts
 * @param averageOnlineTemperatureCelsius average temperature of online
 *                                          servers, in degrees Celsius
 * @param averageOnlineUtilization average utilization of online servers,
 *                                  between {@code 0.0} and {@code 1.0}
 *
 * @author CPZ
 */
public record ColumnOperationalSnapshot(
        String columnCode,
        int installedServerCount,
        int onlineServerCount,
        double idlePowerWatts,
        double maxPowerWatts,
        double currentPowerWatts,
        double averageOnlineTemperatureCelsius,
        double averageOnlineUtilization
) {

    public ColumnOperationalSnapshot {
        Objects.requireNonNull(columnCode, "columnCode must not be null");
        if (columnCode.isBlank()) throw new IllegalArgumentException("columnCode must not be blank");
        if (installedServerCount < 0) throw new IllegalArgumentException("installedServerCount must be >= 0");
        if (onlineServerCount < 0) throw new IllegalArgumentException("onlineServerCount must be >= 0");
        if (onlineServerCount > installedServerCount) throw new IllegalArgumentException("onlineServerCount must not exceed installedServerCount");
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
    }

    /**
     * Indicates whether the column contains installed servers.
     *
     * @return {@code true} when at least one server is installed
     */
    public boolean hasInstalledServers() {
        return installedServerCount > 0;
    }

    /**
     * Indicates whether the column contains online servers.
     *
     * @return {@code true} when at least one server is online
     */
    public boolean hasOnlineServers() {
        return onlineServerCount > 0;
    }

    private static void requireFiniteAndNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException(fieldName + " must be finite and >= 0");
    }
}
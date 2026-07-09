package com.cpz.sim.datacenter.temperature;

/**
 * Mutable thermal state for one installed server.
 */
public final class ServerThermalState {

    private final String serverCode;
    private double temperatureCelsius;

    public ServerThermalState(String serverCode, double initialTemperatureCelsius) {
        this.serverCode = requireNonBlank(serverCode, "serverCode");
        this.temperatureCelsius = requireFinite(initialTemperatureCelsius, "initialTemperatureCelsius");
    }

    public String getServerCode() {
        return serverCode;
    }

    public double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(double temperatureCelsius) {
        this.temperatureCelsius = requireFinite(temperatureCelsius, "temperatureCelsius");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be null or blank.");
        return value;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite.");
        return value;
    }

}

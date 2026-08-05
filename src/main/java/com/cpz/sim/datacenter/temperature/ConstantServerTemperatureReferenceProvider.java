package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.model.Server;

import java.util.Objects;

/**
 * Provides the same external reference temperature for every installed server.
 *
 * <p>This implementation preserves the original temperature-system behavior,
 * where all servers exchange heat with one globally configured ambient
 * temperature.</p>
 *
 * @author CPZ
 */
public final class ConstantServerTemperatureReferenceProvider implements ServerTemperatureReferenceProvider {

    private final double temperatureCelsius;

    /**
     * Creates a provider with a constant reference temperature.
     *
     * @param temperatureCelsius constant reference temperature in degrees Celsius
     *
     * @throws IllegalArgumentException if {@code temperatureCelsius} is not finite
     */
    public ConstantServerTemperatureReferenceProvider(double temperatureCelsius) {
        if (!Double.isFinite(temperatureCelsius)) throw new IllegalArgumentException("temperatureCelsius must be finite");
        this.temperatureCelsius = temperatureCelsius;
    }

    /**
     * Returns the configured constant temperature.
     *
     * @param server installed server whose reference temperature is requested
     * @return configured constant temperature in degrees Celsius
     *
     * @throws NullPointerException if {@code server} is {@code null}
     */
    @Override
    public double temperatureCelsiusFor(Server server) {
        Objects.requireNonNull(server, "server must not be null");
        return temperatureCelsius;
    }

    /**
     * Returns the configured constant temperature.
     *
     * @return constant reference temperature in degrees Celsius
     */
    public double temperatureCelsius() {
        return temperatureCelsius;
    }
}
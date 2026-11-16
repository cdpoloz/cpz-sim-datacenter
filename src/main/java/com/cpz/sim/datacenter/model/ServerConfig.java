package com.cpz.sim.datacenter.model;

import java.util.Objects;

/**
 * Defines the reusable physical configuration of a server model.
 *
 * <p>If {@code thermalProperties} is {@code null}, the server uses the
 * global thermal properties defined by the temperature system options.</p>
 *
 * @param modelCode
 *        unique code identifying the server model
 * @param manufacturer
 *        server manufacturer
 * @param model
 *        server model name
 * @param idlePowerWatts
 *        power consumption while the server is idle
 * @param maxPowerWatts
 *        maximum power consumption of the server
 * @param thermalProperties
 *        model-specific thermal properties, or {@code null} to use the
 *        global thermal configuration
 *
 * @param thermalProtection optional per-model protection; null preserves unprotected behavior
 * @author CPZ
 */
public record ServerConfig(
        String modelCode,
        String manufacturer,
        String model,
        float idlePowerWatts,
        float maxPowerWatts,
        ServerThermalProperties thermalProperties,
        ServerThermalProtection thermalProtection
) {

    /**
     * Creates and validates a server configuration.
     */
    public ServerConfig {
        if (thermalProtection != null && thermalProtection.shutdownPowerWatts() > idlePowerWatts)
            throw new IllegalArgumentException("shutdownPowerWatts must not exceed idlePowerWatts");
        Objects.requireNonNull(modelCode);
        Objects.requireNonNull(manufacturer);
        Objects.requireNonNull(model);
        if (!Float.isFinite(idlePowerWatts) || idlePowerWatts < 0.0f)
            throw new IllegalArgumentException("invalid idlePowerWatts");
        if (!Float.isFinite(maxPowerWatts) || maxPowerWatts <= idlePowerWatts)
            throw new IllegalArgumentException("maxPowerWatts must be higher than idlePowerWatts");
    }

    public ServerConfig(String modelCode, String manufacturer, String model, float idlePowerWatts,
                        float maxPowerWatts, ServerThermalProperties thermalProperties) {
        this(modelCode, manufacturer, model, idlePowerWatts, maxPowerWatts, thermalProperties, null);
    }

    /**
     * Creates a server configuration without model-specific thermal
     * properties.
     *
     * <p>The server will use the global thermal properties defined by the
     * temperature system options.</p>
     *
     * @param modelCode
     *        unique code identifying the server model
     * @param manufacturer
     *        server manufacturer
     * @param model
     *        server model name
     * @param idlePowerWatts
     *        power consumption while the server is idle
     * @param maxPowerWatts
     *        maximum power consumption of the server
     */
    public ServerConfig(
            String modelCode,
            String manufacturer,
            String model,
            float idlePowerWatts,
            float maxPowerWatts
    ) {
        this(
                modelCode,
                manufacturer,
                model,
                idlePowerWatts,
                maxPowerWatts,
                null
        );
    }

}

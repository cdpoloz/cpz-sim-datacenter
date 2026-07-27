package com.cpz.sim.datacenter.config.definition;

import com.cpz.sim.datacenter.config.json.ServerModelDefinitionDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Defines a reusable server model loaded from the datacenter configuration.
 *
 * <p>The thermal properties are optional. When both values are absent, the
 * server model uses the global thermal properties defined in the temperature
 * configuration.</p>
 *
 * <p>If model-specific thermal properties are provided, both values must be
 * specified together.</p>
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
 * @param thermalCapacityJoulesPerCelsius
 *        model-specific thermal capacity, or {@code null} to use the global
 *        thermal configuration
 * @param heatDissipationWattsPerCelsius
 *        model-specific heat dissipation coefficient, or {@code null} to use
 *        the global thermal configuration
 *
 * @author CPZ
 */
@JsonDeserialize(using = ServerModelDefinitionDeserializer.class)
public record ServerModelDefinition(
        String modelCode,
        String manufacturer,
        String model,
        float idlePowerWatts,
        float maxPowerWatts,
        Double thermalCapacityJoulesPerCelsius,
        Double heatDissipationWattsPerCelsius
) {
    /**
     * Creates a server model definition without model-specific thermal
     * properties.
     *
     * <p>The resulting server model will use the global thermal properties
     * defined in the temperature configuration.</p>
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
    public ServerModelDefinition(
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
                null,
                null
        );
    }
}

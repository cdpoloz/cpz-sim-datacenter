package com.cpz.sim.datacenter.config.json;

import com.cpz.sim.datacenter.model.ServerThermalProtection;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Deserializes a server model while preserving the distinction between
 * omitted model-specific thermal properties and explicit {@code null} values.
 *
 * @author CPZ
 */
public final class ServerModelDefinitionDeserializer extends JsonDeserializer<ServerModelDefinition> {

    private static final String THERMAL_CAPACITY = "thermalCapacityJoulesPerCelsius";
    private static final String HEAT_DISSIPATION = "heatDissipationWattsPerCelsius";
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "modelCode",
            "manufacturer",
            "model",
            "idlePowerWatts",
            "maxPowerWatts",
            THERMAL_CAPACITY,
            HEAT_DISSIPATION,
            "thermalProtection"
    );

    @Override
    public ServerModelDefinition deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (!node.isObject()) {
            throw JsonMappingException.from(parser, "Server model definition must be a JSON object");
        }
        validateKnownFields(parser, node);
        validateThermalPropertyPresence(parser, node);
        return new ServerModelDefinition(
                readRequiredString(parser, node, "modelCode"),
                readRequiredString(parser, node, "manufacturer"),
                readRequiredString(parser, node, "model"),
                readRequiredFloat(parser, node, "idlePowerWatts"),
                readRequiredFloat(parser, node, "maxPowerWatts"),
                readOptionalThermalDouble(parser, node, THERMAL_CAPACITY),
                readOptionalThermalDouble(parser, node, HEAT_DISSIPATION),
                readProtection(parser, node)
        );
    }

    private static ServerThermalProtection readProtection(JsonParser parser, JsonNode model)
            throws IOException {
        JsonNode block = model.get("thermalProtection");
        if (block == null) return null;
        if (!block.isObject()) throw JsonMappingException.from(parser, "thermalProtection must be a non-null object");
        Set<String> numbers = Set.of("throttleTemperatureCelsius", "throttleRecoveryTemperatureCelsius",
                "throttledMaxUtilization", "shutdownTemperatureCelsius", "restartTemperatureCelsius",
                "minimumShutdownSeconds", "shutdownPowerWatts");
        var fields = block.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!numbers.contains(field) && !field.equals("enabled") && !field.equals("restartPolicy"))
                throw JsonMappingException.from(parser, "Unknown thermalProtection field: " + field);
        }
        for (String field : numbers) {
            JsonNode value = block.get(field);
            if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue()))
                throw JsonMappingException.from(parser, "thermalProtection." + field + " must be an explicit finite number");
        }
        if (!block.has("enabled") || !block.get("enabled").isBoolean())
            throw JsonMappingException.from(parser, "thermalProtection.enabled must be an explicit boolean");
        if (!block.has("restartPolicy") || !block.get("restartPolicy").isTextual())
            throw JsonMappingException.from(parser, "thermalProtection.restartPolicy must be MANUAL or AUTOMATIC");
        return parser.getCodec().treeToValue(block, ServerThermalProtection.class);
    }

    private static void validateKnownFields(JsonParser parser, JsonNode node) throws JsonMappingException {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!KNOWN_FIELDS.contains(fieldName)) {
                throw JsonMappingException.from(parser, "Unrecognized server model field: " + fieldName);
            }
        }
    }

    private static void validateThermalPropertyPresence(JsonParser parser, JsonNode node) throws JsonMappingException {
        boolean thermalCapacityPresent = node.has(THERMAL_CAPACITY);
        boolean heatDissipationPresent = node.has(HEAT_DISSIPATION);
        if (thermalCapacityPresent != heatDissipationPresent) {
            throw JsonMappingException.from(
                    parser,
                    "Server model must specify both " + THERMAL_CAPACITY + " and " + HEAT_DISSIPATION
                            + ", or neither"
            );
        }
    }

    private static String readRequiredString(JsonParser parser, JsonNode node, String fieldName)
            throws JsonMappingException {
        JsonNode field = requireField(parser, node, fieldName);
        if (field.isNull()) return null;
        if (!field.isTextual()) {
            throw JsonMappingException.from(parser, "Server model field '" + fieldName + "' must be a string");
        }
        return field.textValue();
    }

    private static float readRequiredFloat(JsonParser parser, JsonNode node, String fieldName)
            throws JsonMappingException {
        JsonNode field = requireField(parser, node, fieldName);
        if (field.isNull()) {
            throw JsonMappingException.from(parser, "Server model field '" + fieldName + "' cannot be null");
        }
        if (!field.isNumber()) {
            throw JsonMappingException.from(parser, "Server model field '" + fieldName + "' must be a number");
        }
        return field.floatValue();
    }

    private static Double readOptionalThermalDouble(JsonParser parser, JsonNode node, String fieldName)
            throws JsonMappingException {
        JsonNode field = node.get(fieldName);
        if (field == null) return null;
        if (field.isNull()) {
            throw JsonMappingException.from(parser, "Server model field '" + fieldName + "' cannot be null");
        }
        if (!field.isNumber()) {
            throw JsonMappingException.from(parser, "Server model field '" + fieldName + "' must be a number");
        }
        return field.doubleValue();
    }

    private static JsonNode requireField(JsonParser parser, JsonNode node, String fieldName)
            throws JsonMappingException {
        JsonNode field = node.get(fieldName);
        if (field == null) {
            throw JsonMappingException.from(parser, "Missing required server model field '" + fieldName + "'");
        }
        return field;
    }
}

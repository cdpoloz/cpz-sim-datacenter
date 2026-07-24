package com.cpz.sim.datacenter.config.json;

import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.model.ServerRole;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/**
 * @author CPZ
 */
public final class ServerDefinitionDeserializer extends JsonDeserializer<ServerDefinition> {

    private static final Set<String> KNOWN_FIELDS =
            Set.of("column", "rackCode", "slot", "modelCode", "status", "role", "workloadFactor");

    @Override
    public ServerDefinition deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (!node.isObject()) {
            throw JsonMappingException.from(parser, "Server definition must be a JSON object");
        }
        validateKnownFields(parser, node);
        return new ServerDefinition(
                readOptionalString(parser, node, "column"),
                readOptionalString(parser, node, "rackCode"),
                readOptionalString(parser, node, "slot"),
                readOptionalString(parser, node, "modelCode"),
                readOptionalString(parser, node, "status"),
                parseRole(parser, node),
                readWorkloadFactor(parser, node)
        );
    }

    private static void validateKnownFields(JsonParser parser, JsonNode node) throws JsonMappingException {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!KNOWN_FIELDS.contains(fieldName)) {
                throw JsonMappingException.from(parser, "Unrecognized server field: " + fieldName);
            }
        }
    }

    private static String readOptionalString(JsonParser parser, JsonNode node, String fieldName) throws JsonMappingException {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        if (!field.isTextual()) {
            throw JsonMappingException.from(parser, "Server field '" + fieldName + "' must be a string");
        }
        return field.asText();
    }

    private static float readWorkloadFactor(JsonParser parser, JsonNode node) throws JsonMappingException {
        JsonNode field = node.get("workloadFactor");
        if (field == null) return 1.0f;
        if (field.isNull() || !field.isNumber()) {
            throw JsonMappingException.from(parser, "Server field 'workloadFactor' must be a number");
        }
        return field.floatValue();
    }

    private static ServerRole parseRole(JsonParser parser, JsonNode node) throws JsonMappingException {
        JsonNode roleNode = node.get("role");
        if (roleNode == null) return null;
        if (roleNode.isNull()) {
            throw JsonMappingException.from(parser, "Server field 'role' cannot be null");
        }
        if (!roleNode.isTextual()) {
            throw JsonMappingException.from(parser, "Server field 'role' must be a string");
        }
        String value = roleNode.textValue();
        try {
            return ServerRole.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw JsonMappingException.from(
                    parser,
                    "Server field 'role' has invalid ServerRole value '" + value
                            + "'. Allowed values: " + Arrays.toString(ServerRole.values()),
                    exception
            );
        }
    }
}

package com.cpz.sim.datacenter.config.json;

import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author CPZ
 */
public final class RackDefinitionDeserializer extends JsonDeserializer<RackDefinition> {

    private static final Set<String> KNOWN_FIELDS = Set.of("code", "column", "row", "slotCount", "slots");

    @Override
    public RackDefinition deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (!node.isObject()) {
            throw JsonMappingException.from(parser, "Rack definition must be a JSON object");
        }
        validateKnownFields(parser, node);
        boolean slotCountPresent = node.has("slotCount");
        boolean slotsPresent = node.has("slots");
        return new RackDefinition(
                readString(parser, node, "code"),
                readString(parser, node, "column"),
                readString(parser, node, "row"),
                readInteger(parser, node, "slotCount"),
                readStringList(parser, node, "slots"),
                slotCountPresent,
                slotsPresent
        );
    }

    private static void validateKnownFields(JsonParser parser, JsonNode node) throws JsonMappingException {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!KNOWN_FIELDS.contains(fieldName)) {
                throw JsonMappingException.from(parser, "Unrecognized rack field: " + fieldName);
            }
        }
    }

    private static String readString(JsonParser parser, JsonNode node, String fieldName) throws JsonMappingException {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        if (!field.isTextual()) {
            throw JsonMappingException.from(parser, "Rack field '" + fieldName + "' must be a string");
        }
        return field.asText();
    }

    private static Integer readInteger(JsonParser parser, JsonNode node, String fieldName) throws JsonMappingException {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        if (!field.canConvertToInt()) {
            throw JsonMappingException.from(parser, "Rack field '" + fieldName + "' must be an integer");
        }
        return field.asInt();
    }

    private static List<String> readStringList(JsonParser parser, JsonNode node, String fieldName) throws JsonMappingException {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        if (!field.isArray()) {
            throw JsonMappingException.from(parser, "Rack field '" + fieldName + "' must be an array");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : field) {
            if (element.isNull()) {
                values.add(null);
                continue;
            }
            if (!element.isTextual()) {
                throw JsonMappingException.from(parser, "Rack field '" + fieldName + "' must contain only strings");
            }
            values.add(element.asText());
        }
        return values;
    }
}

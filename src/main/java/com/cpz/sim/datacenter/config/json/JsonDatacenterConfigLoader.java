package com.cpz.sim.datacenter.config.json;

import com.cpz.sim.datacenter.config.DatacenterConfigException;
import com.cpz.sim.datacenter.config.DatacenterConfigLoader;
import com.cpz.sim.datacenter.config.definition.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * @author CPZ
 */
public final class JsonDatacenterConfigLoader implements DatacenterConfigLoader {

    private static final TypeReference<List<ServerModelDefinition>> SERVER_MODELS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ServerDefinition>> SERVERS_TYPE = new TypeReference<>() {
    };
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    @Override
    public DatacenterDefinition load(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        try (InputStream inputStream = Files.newInputStream(path)) {
            JsonNode root = JSON_MAPPER.readTree(inputStream);
            return new DatacenterDefinition(
                    readRequired(root, "name", path, String.class),
                    readRequired(root, "layout", path, DatacenterLayoutDefinition.class),
                    readRequired(root, "serverModels", path, SERVER_MODELS_TYPE),
                    readRequired(root, "servers", path, SERVERS_TYPE),
                    readOptionalTemperature(root, path),
                    readOptionalHealth(root, path),
                    readOptionalCooling(root, path)
            );
        } catch (IOException exception) {
            throw new DatacenterConfigException("Could not load datacenter config from path: " + path, exception);
        }
    }

    private static TemperatureSystemOptionsDefinition readOptionalTemperature(JsonNode root, Path path) throws IOException {
        JsonNode temperatureNode = root.get("temperature");
        if (temperatureNode == null) return null;
        if (temperatureNode.isNull())
            throw new DatacenterConfigException("Temperature block cannot be null in datacenter config: " + path);
        return JSON_MAPPER.readValue(temperatureNode.traverse(JSON_MAPPER), TemperatureSystemOptionsDefinition.class);
    }

    private static HealthSystemOptionsDefinition readOptionalHealth(JsonNode root, Path path) throws IOException {
        JsonNode healthNode = root.get("health");
        if (healthNode == null) return null;
        if (healthNode.isNull())
            throw new DatacenterConfigException("Health block cannot be null in datacenter config: " + path);
        return JSON_MAPPER.readValue(healthNode.traverse(JSON_MAPPER), HealthSystemOptionsDefinition.class);
    }

    private static CoolingConfigDefinition readOptionalCooling(JsonNode root, Path path) throws IOException {
        JsonNode coolingNode = root.get("cooling");
        if (coolingNode == null) return null;
        if (coolingNode.isNull()) throw new DatacenterConfigException("Cooling block cannot be null in datacenter config: " + path);
        return JSON_MAPPER.readValue(coolingNode.traverse(JSON_MAPPER), CoolingConfigDefinition.class);
    }

    private static <T> T readRequired(JsonNode root, String propertyName, Path path, Class<T> type) throws IOException {
        JsonNode node = requireProperty(root, propertyName, path);
        return JSON_MAPPER.readValue(node.traverse(JSON_MAPPER), type);
    }

    private static <T> T readRequired(JsonNode root, String propertyName, Path path, TypeReference<T> type) throws IOException {
        JsonNode node = requireProperty(root, propertyName, path);
        return JSON_MAPPER.readValue(node.traverse(JSON_MAPPER), type);
    }

    private static JsonNode requireProperty(JsonNode root, String propertyName, Path path) {
        JsonNode node = root.get(propertyName);
        if (node == null)
            throw new DatacenterConfigException("Missing required property '" + propertyName + "' in datacenter config: " + path);
        return node;
    }

}

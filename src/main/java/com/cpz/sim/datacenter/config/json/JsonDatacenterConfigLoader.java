package com.cpz.sim.datacenter.config.json;

import com.cpz.sim.datacenter.config.DatacenterConfigException;
import com.cpz.sim.datacenter.config.DatacenterConfigLoader;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * @author CPZ
 */
public final class JsonDatacenterConfigLoader implements DatacenterConfigLoader {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    @Override
    public DatacenterDefinition load(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        try (InputStream inputStream = Files.newInputStream(path)) {
            return JSON_MAPPER.readValue(inputStream, DatacenterDefinition.class);
        } catch (IOException exception) {
            throw new DatacenterConfigException("Could not load datacenter config from path: " + path, exception);
        }
    }

}

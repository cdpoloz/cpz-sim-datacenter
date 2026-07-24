package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.HealthSystemOptionsDefinition;
import com.cpz.sim.datacenter.config.definition.HealthThresholdDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.health.HealthThreshold;
import com.cpz.sim.datacenter.health.ServerHealthOptions;

import java.util.Objects;

/**
 * Creates runtime server health options from config definitions.
 *
 * @author CPZ
 */
public final class ServerHealthOptionsFactory {

    private final DatacenterConfigValidator validator;

    public ServerHealthOptionsFactory() {
        this(new DatacenterConfigValidator());
    }

    public ServerHealthOptionsFactory(DatacenterConfigValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
    }

    public ServerHealthOptions create(DatacenterDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        validator.validate(definition);
        return create(definition.health());
    }

    public ServerHealthOptions create(HealthSystemOptionsDefinition definition) {
        if (definition == null) return ServerHealthOptions.defaults();
        return new ServerHealthOptions(createThreshold(definition.utilization()), createThreshold(definition.temperatureCelsius()));
    }

    private static HealthThreshold createThreshold(HealthThresholdDefinition definition) {
        Objects.requireNonNull(definition, "threshold definition cannot be null");
        return new HealthThreshold(definition.alertAtOrAbove(), definition.clearAtOrBelow());
    }
}

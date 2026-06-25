package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.model.Datacenter;

/**
 * @author CPZ
 */
public final class DatacenterFactory {

    private final DatacenterConfigValidator validator;

    public DatacenterFactory() {
        this(new DatacenterConfigValidator());
    }

    public DatacenterFactory(DatacenterConfigValidator validator) {
        this.validator = validator;
    }

    public Datacenter create(DatacenterDefinition definition) {
        validator.validate(definition);
        // 1. Convertir serverModels a Map<String, ServerConfig>
        // 2. Convertir servers a List<Server>
        // 3. Construir Datacenter
        throw new UnsupportedOperationException("Not implemented yet");
    }

}

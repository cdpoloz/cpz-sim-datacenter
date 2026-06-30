package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.workload.MapServerWorkloadFactorProvider;
import com.cpz.sim.datacenter.workload.ServerWorkloadFactorProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author CPZ
 */
public class WorkloadFactorProviderFactory {

    private final DatacenterConfigValidator validator;

    public WorkloadFactorProviderFactory() {
        this(new DatacenterConfigValidator());
    }

    public WorkloadFactorProviderFactory(DatacenterConfigValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
    }

    public ServerWorkloadFactorProvider create(DatacenterDefinition definition) {
        validator.validate(definition);
        Map<String, Float> factorsByServerCode = new HashMap<>();
        for (ServerDefinition server : definition.servers()) {
            String serverCode = server.rackCode() + "-" + server.slot();
            factorsByServerCode.put(serverCode, server.workloadFactor());
        }
        return new MapServerWorkloadFactorProvider(factorsByServerCode);
    }
}

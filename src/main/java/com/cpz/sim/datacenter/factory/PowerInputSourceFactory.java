package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.input.NoiseRackPowerInputSource;
import com.cpz.sim.datacenter.input.NoiseServerPowerInputSource;
import com.cpz.sim.datacenter.input.PowerInputGranularity;
import com.cpz.sim.datacenter.input.RackPowerToServerPowerInputSource;
import com.cpz.sim.datacenter.input.ServerPowerInputSource;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.utils.noise.NoiseSource;

import java.util.Objects;

/**
 * Creates simulated server power input sources for power-driven simulations.
 *
 * @author CPZ
 */
public final class PowerInputSourceFactory {

    private final DatacenterConfigValidator validator;

    public PowerInputSourceFactory() {
        this(new DatacenterConfigValidator());
    }

    public PowerInputSourceFactory(DatacenterConfigValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
    }

    public ServerPowerInputSource create(
            DatacenterDefinition definition,
            Datacenter datacenter,
            NoiseSource noiseSource,
            double speed,
            double minDynamicPowerRatio,
            double maxDynamicPowerRatio
    ) {
        validator.validate(definition);
        Objects.requireNonNull(datacenter, "datacenter must not be null");
        Objects.requireNonNull(noiseSource, "noiseSource must not be null");
        PowerInputGranularity granularity = definition.powerInputGranularity();
        return switch (granularity) {
            case SERVER -> new NoiseServerPowerInputSource(
                    noiseSource,
                    speed,
                    minDynamicPowerRatio,
                    maxDynamicPowerRatio
            );
            case RACK -> new RackPowerToServerPowerInputSource(
                    datacenter,
                    new NoiseRackPowerInputSource(datacenter, noiseSource, speed)
            );
        };
    }
}

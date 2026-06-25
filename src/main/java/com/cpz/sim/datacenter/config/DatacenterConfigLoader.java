package com.cpz.sim.datacenter.config;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;

import java.nio.file.Path;

/**
 * @author CPZ
 */
public interface DatacenterConfigLoader {

    DatacenterDefinition load(Path path);

}

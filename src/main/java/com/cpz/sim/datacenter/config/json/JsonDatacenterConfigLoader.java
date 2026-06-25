package com.cpz.sim.datacenter.config.json;

import com.cpz.sim.datacenter.config.DatacenterConfigLoader;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;

import java.nio.file.Path;

/**
 * @author CPZ
 */
public final class JsonDatacenterConfigLoader implements DatacenterConfigLoader {

    @Override
    public DatacenterDefinition load(Path path) {
        // Aquí luego usaremos Jackson, Gson u otro parser.
        // Por ahora solo definimos la arquitectura.
        throw new UnsupportedOperationException("Not implemented yet");
    }

}

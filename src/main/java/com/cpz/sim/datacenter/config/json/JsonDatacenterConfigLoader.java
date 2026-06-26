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
        // CONTINUAR AQUÍ ***********************************************************
        // IMPLEMENTAR CARGA DE ARCHIVO DE CONFIGURACIÓN JSON USANDO Jackson
        // **************************************************************************
        throw new UnsupportedOperationException("JSON loading is not implemented yet");
    }

}

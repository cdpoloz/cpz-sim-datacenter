package com.cpz.sim.datacenter.model;

/**
 * Describes the primary functional role assigned to a server.
 *
 * <p>Each server has exactly one role. The role is static domain metadata and is
 * not derived from hardware status or simulation state.
 *
 * @author CPZ
 */
public enum ServerRole {

    GENERAL_PURPOSE,
    /**
     * Training or inference of artificial intelligence is the server's primary function.
     */
    AI,
    STORAGE,
    DATABASE,
    EDGE,
    /**
     * GPU-accelerated compute not primarily classified as artificial intelligence.
     */
    GPU,
    MANAGEMENT

}

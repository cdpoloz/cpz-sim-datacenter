package com.cpz.sim.datacenter.factory;

/**
 * @author CPZ
 */
public class DatacenterBuildException extends RuntimeException {

    public DatacenterBuildException(String message) {
        super(message);
    }

    public DatacenterBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.cpz.sim.datacenter.config.definition;

/**
 * JSON metadata describing the room associated with the active layout.
 *
 * @param code room code
 * @param name room name
 *
 * @author CPZ
 */
public record RoomDefinition(
        String code,
        String name
) {
}

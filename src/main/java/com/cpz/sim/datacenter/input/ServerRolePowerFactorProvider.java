package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerRole;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Provides role-based power factors for power-driven simulations.
 *
 * @author CPZ
 */
public final class ServerRolePowerFactorProvider {

    private final Map<ServerRole, Double> factorsByRole;

    public ServerRolePowerFactorProvider() {
        this(defaultFactors());
    }

    public ServerRolePowerFactorProvider(Map<ServerRole, Double> factorsByRole) {
        Objects.requireNonNull(factorsByRole, "factorsByRole must not be null");
        this.factorsByRole = new EnumMap<>(ServerRole.class);
        for (Map.Entry<ServerRole, Double> entry : factorsByRole.entrySet()) {
            ServerRole role = Objects.requireNonNull(entry.getKey(), "role must not be null");
            double factor = entry.getValue();
            if (!Double.isFinite(factor) || factor < 0.0) throw new IllegalArgumentException("factor must be finite and >= 0");
            this.factorsByRole.put(role, factor);
        }
    }

    public double factorFor(Server server) {
        Objects.requireNonNull(server, "server must not be null");
        return factorsByRole.getOrDefault(server.getRole(), 1.0);
    }

    private static Map<ServerRole, Double> defaultFactors() {
        Map<ServerRole, Double> factors = new EnumMap<>(ServerRole.class);
        factors.put(ServerRole.GENERAL_PURPOSE, 1.00);
        factors.put(ServerRole.AI, 1.35);
        factors.put(ServerRole.GPU, 1.25);
        factors.put(ServerRole.DATABASE, 1.10);
        factors.put(ServerRole.STORAGE, 0.90);
        factors.put(ServerRole.EDGE, 0.85);
        factors.put(ServerRole.MANAGEMENT, 0.70);
        return factors;
    }
}
package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapts rack-level power input to server-level power input.
 *
 * <p>Rack power is distributed only across installed online servers in the rack,
 * weighted by each server's dynamic power range. If no useful weights are
 * available, the source falls back to uniform distribution. Returned server
 * values are capped at each server's configured maximum because
 * {@link Server#setCurrentPowerWatts(double)} rejects values above that physical
 * limit. When the rack input exceeds the sum of online server maximums, that cap
 * prevents strict rack-power conservation.</p>
 *
 * @author CPZ
 */
public final class RackPowerToServerPowerInputSource implements ServerPowerInputSource {

    private final Datacenter datacenter;
    private final RackPowerInputSource rackPowerInputSource;
    private final DynamicPowerWeightProvider dynamicPowerWeightProvider;
    private final Map<RackLocation, Map<ServerLocation, Double>> cachedPowerByRack;
    private long cachedTickIndex;

    /**
     * Creates a server power input source backed by rack-level power input.
     *
     * @param datacenter datacenter topology used to find installed servers
     * @param rackPowerInputSource source of current rack power
     */
    public RackPowerToServerPowerInputSource(
            Datacenter datacenter,
            RackPowerInputSource rackPowerInputSource
    ) {
        this(datacenter, rackPowerInputSource, RackPowerToServerPowerInputSource::dynamicPowerWeight);
    }

    RackPowerToServerPowerInputSource(
            Datacenter datacenter,
            RackPowerInputSource rackPowerInputSource,
            DynamicPowerWeightProvider dynamicPowerWeightProvider
    ) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
        this.rackPowerInputSource =
                Objects.requireNonNull(rackPowerInputSource, "rackPowerInputSource must not be null");
        this.dynamicPowerWeightProvider =
                Objects.requireNonNull(dynamicPowerWeightProvider, "dynamicPowerWeightProvider must not be null");
        this.cachedPowerByRack = new HashMap<>();
        this.cachedTickIndex = Long.MIN_VALUE;
    }

    @Override
    public double currentPowerWatts(Server server, SimulationTick tick) {
        Objects.requireNonNull(server, "server must not be null");
        Objects.requireNonNull(tick, "tick must not be null");
        if (tick.index() != cachedTickIndex) {
            cachedPowerByRack.clear();
            cachedTickIndex = tick.index();
        }
        RackLocation rackLocation = server.getLocation().rackLocation();
        Map<ServerLocation, Double> rackPowerByServer =
                cachedPowerByRack.computeIfAbsent(rackLocation, location -> distributeRackPower(location, tick));
        return rackPowerByServer.getOrDefault(server.getLocation(), 0.0);
    }

    private Map<ServerLocation, Double> distributeRackPower(RackLocation rackLocation, SimulationTick tick) {
        double rackPowerWatts = rackPowerInputSource.currentPowerWatts(rackLocation, tick);
        validateRackPower(rackPowerWatts);
        List<Server> installedServers = datacenter.getServers(rackLocation);
        List<Server> onlineServers = installedServers.stream()
                .filter(server -> server.getStatus() != HardwareStatus.OFFLINE)
                .toList();
        if (onlineServers.isEmpty()) return zeroPowerByServer(installedServers);

        double totalWeight = totalDynamicPowerWeight(onlineServers);
        boolean useUniformFallback = totalWeight <= 0.0;
        double uniformWeight = 1.0 / onlineServers.size();
        Map<ServerLocation, Double> powerByServer = zeroPowerByServer(installedServers);
        for (Server server : onlineServers) {
            double share = useUniformFallback
                    ? uniformWeight
                    : usableDynamicPowerWeight(server) / totalWeight;
            double allocatedPowerWatts = rackPowerWatts * share;
            powerByServer.put(server.getLocation(), Math.min(allocatedPowerWatts, server.getConfig().maxPowerWatts()));
        }
        return Map.copyOf(powerByServer);
    }

    private static Map<ServerLocation, Double> zeroPowerByServer(List<Server> servers) {
        Map<ServerLocation, Double> powerByServer = new HashMap<>();
        for (Server server : servers) powerByServer.put(server.getLocation(), 0.0);
        return powerByServer;
    }

    private double totalDynamicPowerWeight(List<Server> servers) {
        double total = 0.0;
        for (Server server : servers) {
            total += usableDynamicPowerWeight(server);
        }
        return total;
    }

    private double usableDynamicPowerWeight(Server server) {
        double weight = dynamicPowerWeightProvider.weightFor(server);
        if (!Double.isFinite(weight) || weight <= 0.0) return 0.0;
        return weight;
    }

    private static double dynamicPowerWeight(Server server) {
        return server.getConfig().maxPowerWatts() - server.getConfig().idlePowerWatts();
    }

    private static void validateRackPower(double rackPowerWatts) {
        if (!Double.isFinite(rackPowerWatts) || rackPowerWatts < 0.0)
            throw new IllegalArgumentException("rackPowerWatts must be finite and >= 0");
    }

    @FunctionalInterface
    interface DynamicPowerWeightProvider {

        double weightFor(Server server);
    }
}

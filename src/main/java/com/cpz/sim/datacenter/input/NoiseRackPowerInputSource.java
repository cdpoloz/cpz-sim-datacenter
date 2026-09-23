package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.foundation.time.SimulationTick;
import com.cpz.utils.noise.NoiseSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates variable aggregate rack power for rack-level power-driven
 * simulations.
 *
 * <p>The source sums idle and maximum power across installed online servers in
 * the rack, selects an activity range from the dominant online server role, and
 * applies deterministic noise to produce watts between the aggregate idle and
 * aggregate maximum rack power.</p>
 *
 * @author CPZ
 */
public final class NoiseRackPowerInputSource implements RackPowerInputSource {

    private static final Map<ServerRole, ActivityRange> DEFAULT_ACTIVITY_RANGES = defaultActivityRanges();

    private final Datacenter datacenter;
    private final NoiseSource noiseSource;
    private final double speed;

    public NoiseRackPowerInputSource(
            Datacenter datacenter,
            NoiseSource noiseSource,
            double speed
    ) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
        this.noiseSource = Objects.requireNonNull(noiseSource, "noiseSource must not be null");
        validateFiniteNonNegative(speed, "speed");
        this.speed = speed;
    }

    @Override
    public double currentPowerWatts(RackLocation rackLocation, SimulationTick tick) {
        Objects.requireNonNull(rackLocation, "rackLocation must not be null");
        Objects.requireNonNull(tick, "tick must not be null");
        List<Server> onlineServers = datacenter.getServers(rackLocation).stream()
                .filter(server -> server.getStatus() != HardwareStatus.OFFLINE)
                .toList();
        if (onlineServers.isEmpty()) return 0.0;

        double aggregatedIdlePowerWatts = 0.0;
        double aggregatedMaxPowerWatts = 0.0;
        for (Server server : onlineServers) {
            aggregatedIdlePowerWatts += server.getConfig().idlePowerWatts();
            aggregatedMaxPowerWatts += server.getConfig().maxPowerWatts();
        }

        ActivityRange activityRange = activityRangeFor(dominantRole(onlineServers));
        double normalizedNoise = normalizedNoise(rackLocation, tick);
        double activityFactor = activityRange.min()
                + normalizedNoise * (activityRange.max() - activityRange.min());
        double aggregatedDynamicRangeWatts = aggregatedMaxPowerWatts - aggregatedIdlePowerWatts;
        return aggregatedIdlePowerWatts + activityFactor * aggregatedDynamicRangeWatts;
    }

    private double normalizedNoise(RackLocation rackLocation, SimulationTick tick) {
        double timePosition = tick.elapsedSeconds() * speed;
        double rackOffset = deterministicOffset(rackLocation);
        float noiseValue = noiseSource.noise((float) (timePosition + rackOffset));
        return clamp(noiseValue, 0.0, 1.0);
    }

    private static ServerRole dominantRole(List<Server> onlineServers) {
        Map<ServerRole, Integer> countsByRole = new EnumMap<>(ServerRole.class);
        for (Server server : onlineServers) countsByRole.merge(server.getRole(), 1, Integer::sum);
        ServerRole dominantRole = ServerRole.GENERAL_PURPOSE;
        int dominantCount = 0;
        for (Map.Entry<ServerRole, Integer> entry : countsByRole.entrySet()) {
            ServerRole candidate = entry.getKey();
            int count = entry.getValue();
            if (count > dominantCount || isHigherPriorityTie(candidate, dominantRole, count, dominantCount)) {
                dominantRole = candidate;
                dominantCount = count;
            }
        }
        return dominantRole;
    }

    private static boolean isHigherPriorityTie(
            ServerRole candidate,
            ServerRole current,
            int candidateCount,
            int currentCount
    ) {
        if (candidateCount != currentCount) return false;
        ActivityRange candidateRange = activityRangeFor(candidate);
        ActivityRange currentRange = activityRangeFor(current);
        int minComparison = Double.compare(candidateRange.min(), currentRange.min());
        if (minComparison != 0) return minComparison > 0;
        return candidate.ordinal() < current.ordinal();
    }

    private static ActivityRange activityRangeFor(ServerRole role) {
        return DEFAULT_ACTIVITY_RANGES.getOrDefault(role, DEFAULT_ACTIVITY_RANGES.get(ServerRole.GENERAL_PURPOSE));
    }

    private static Map<ServerRole, ActivityRange> defaultActivityRanges() {
        Map<ServerRole, ActivityRange> ranges = new EnumMap<>(ServerRole.class);
        ranges.put(ServerRole.AI, new ActivityRange(0.65, 1.00));
        ranges.put(ServerRole.GPU, new ActivityRange(0.65, 1.00));
        ranges.put(ServerRole.DATABASE, new ActivityRange(0.45, 0.85));
        ranges.put(ServerRole.STORAGE, new ActivityRange(0.30, 0.70));
        ranges.put(ServerRole.GENERAL_PURPOSE, new ActivityRange(0.25, 0.80));
        ranges.put(ServerRole.MANAGEMENT, new ActivityRange(0.15, 0.45));
        ranges.put(ServerRole.EDGE, new ActivityRange(0.20, 0.65));
        return Map.copyOf(ranges);
    }

    private static void validateFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0)
            throw new IllegalArgumentException(name + " must be finite and non-negative");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float deterministicOffset(RackLocation rackLocation) {
        int hash = stableHash(rackLocation.code());
        int bucket = Math.floorMod(hash, 10_000);
        return bucket / 10_000.0f * 1_000.0f;
    }

    private static int stableHash(String value) {
        int hash = 17;
        for (int i = 0; i < value.length(); i++) hash = 31 * hash + value.charAt(i);
        return hash;
    }

    private record ActivityRange(
            double min,
            double max
    ) {
    }
}

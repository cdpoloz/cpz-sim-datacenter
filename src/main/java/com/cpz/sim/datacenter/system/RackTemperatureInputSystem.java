package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.input.RackTemperatureInputSource;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Updates temperature, inferred power, and inferred utilization from rack-level
 * temperature observations.
 *
 * <p>This system is the first rack-level implementation for
 * temperature-driven simulations. It applies the observed rack temperature to
 * installed online servers in that rack and infers server power from a clamped
 * temperature ratio. Utilization is then inferred from power through
 * {@link Server#estimateUtilizationFromCurrentPower()}.</p>
 *
 * @author CPZ
 */
public final class RackTemperatureInputSystem implements Simulatable {

    private static final double DEFAULT_MAX_REFERENCE_TEMPERATURE_CELSIUS = 85.0;

    private final Datacenter datacenter;
    private final TemperatureSystem temperatureSystem;
    private final RackTemperatureInputSource rackTemperatureInputSource;
    private final double ambientTemperatureCelsius;
    private final double maxReferenceTemperatureCelsius;

    public RackTemperatureInputSystem(
            Datacenter datacenter,
            TemperatureSystem temperatureSystem,
            RackTemperatureInputSource rackTemperatureInputSource,
            TemperatureSystemOptions options
    ) {
        this(
                datacenter,
                temperatureSystem,
                rackTemperatureInputSource,
                options,
                DEFAULT_MAX_REFERENCE_TEMPERATURE_CELSIUS
        );
    }

    public RackTemperatureInputSystem(
            Datacenter datacenter,
            TemperatureSystem temperatureSystem,
            RackTemperatureInputSource rackTemperatureInputSource,
            TemperatureSystemOptions options,
            double maxReferenceTemperatureCelsius
    ) {
        this.datacenter = Objects.requireNonNull(datacenter, "datacenter must not be null");
        this.temperatureSystem = Objects.requireNonNull(temperatureSystem, "temperatureSystem must not be null");
        this.rackTemperatureInputSource =
                Objects.requireNonNull(rackTemperatureInputSource, "rackTemperatureInputSource must not be null");
        TemperatureSystemOptions safeOptions = Objects.requireNonNull(options, "options must not be null");
        this.ambientTemperatureCelsius = safeOptions.ambientTemperatureCelsius();
        validateMaxReferenceTemperature(maxReferenceTemperatureCelsius, this.ambientTemperatureCelsius);
        this.maxReferenceTemperatureCelsius = maxReferenceTemperatureCelsius;
    }

    @Override
    public void update(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null");
        Map<RackLocation, Double> observedTemperatureByRack = new HashMap<>();
        for (Server server : datacenter.getServers()) {
            if (server.getStatus() == HardwareStatus.OFFLINE) {
                server.setCurrentPowerWatts(0.0);
                server.estimateUtilizationFromCurrentPower();
                continue;
            }
            RackLocation rackLocation = server.getLocation().rackLocation();
            double observedTemperatureCelsius =
                    observedTemperatureByRack.computeIfAbsent(
                            rackLocation,
                            location -> rackTemperatureInputSource.temperatureCelsius(location, tick)
                    );
            validateObservedTemperature(observedTemperatureCelsius);
            double inferredPowerWatts = inferPowerWatts(server, observedTemperatureCelsius);
            server.setCurrentPowerWatts(inferredPowerWatts);
            server.estimateUtilizationFromCurrentPower();
            temperatureSystem.setTemperatureCelsius(server.getCode(), observedTemperatureCelsius);
        }
    }

    private double inferPowerWatts(Server server, double observedTemperatureCelsius) {
        double temperatureRatio =
                (observedTemperatureCelsius - ambientTemperatureCelsius)
                        / (maxReferenceTemperatureCelsius - ambientTemperatureCelsius);
        double clampedRatio = clamp(temperatureRatio, 0.0, 1.0);
        double idlePowerWatts = server.getConfig().idlePowerWatts();
        double maxPowerWatts = server.getConfig().maxPowerWatts();
        return idlePowerWatts + clampedRatio * (maxPowerWatts - idlePowerWatts);
    }

    private static void validateMaxReferenceTemperature(
            double maxReferenceTemperatureCelsius,
            double ambientTemperatureCelsius
    ) {
        if (!Double.isFinite(maxReferenceTemperatureCelsius)
                || maxReferenceTemperatureCelsius <= ambientTemperatureCelsius)
            throw new IllegalArgumentException(
                    "maxReferenceTemperatureCelsius must be finite and greater than ambientTemperatureCelsius"
            );
    }

    private static void validateObservedTemperature(double observedTemperatureCelsius) {
        if (!Double.isFinite(observedTemperatureCelsius))
            throw new IllegalArgumentException("observedRackTemperatureCelsius must be finite");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

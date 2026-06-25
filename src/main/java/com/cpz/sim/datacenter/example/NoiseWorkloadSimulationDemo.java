package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.workload.NoiseWorkloadSource;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.PerlinNoise;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * @author CPZ
 */
public class NoiseWorkloadSimulationDemo {

    public static void main(String[] args) {
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                300.0f
        );
        Server serverA = createServer(config, Slot.S01);
        Server serverB = createServer(config, Slot.S02);
        Datacenter datacenter = new Datacenter(List.of(serverA, serverB));
        EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
        PerlinNoise perlinNoise = new PerlinNoise(1234L);
        FractalNoise fractalNoise = new FractalNoise(
                perlinNoise,
                5,
                1.0f,
                2.0f,
                0.5f
        );
        WorkloadSource workloadSource = new NoiseWorkloadSource(
                fractalNoise,
                0.001,
                0.2f,
                0.9f
        );
        engine.register(new WorkloadSystem(datacenter, workloadSource));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.register(energySystem);
        for (int i = 0; i < 4; i++) {
            SimulationTick tick = engine.step();
            String serverUtilizations = datacenter.getServers().stream()
                    .map(server -> String.format(
                            Locale.US,
                            "%s: %.3f",
                            server.getCode(),
                            server.getUtilization()
                    ))
                    .collect(Collectors.joining(" | "));
            System.out.printf(
                    Locale.US,
                    "Tick %d | Time: %s | %s | Power: %.1f W | Energy: %.3f kWh%n",
                    tick.index(),
                    tick.elapsedTime(),
                    serverUtilizations,
                    datacenter.getTotalItPowerWatts(),
                    energySystem.getConsumedEnergyKWh()
            );
        }
    }

    private static Server createServer(ServerConfig config, Slot slot) {
        return new Server(new ServerLocation(Column.A01, Row.R01, slot), config, HardwareStatus.OK);
    }
}

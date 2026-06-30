package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.WorkloadFactorProviderFactory;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.workload.*;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.PerlinNoise;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * @author CPZ
 */
public class JsonDatacenterSimulationDemo {

    private JsonDatacenterSimulationDemo() {
    }

    public static void main(String[] args) {
        Path configPath = Path.of("data/config/demo-datacenter-medium.json");
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterDefinition definition = loader.load(configPath);
        DatacenterFactory factory = new DatacenterFactory();
        Datacenter datacenter = factory.create(definition);
        System.out.printf(
                Locale.US,
                "Loaded datacenter: %s | Racks: %d | Servers: %d%n",
                definition.name(),
                datacenter.getRackCount(),
                datacenter.getServerCount()
        );
        PerlinNoise perlinNoise = new PerlinNoise(1234L);
        FractalNoise fractalNoise = new FractalNoise(
                perlinNoise,
                5,
                1.0f,
                2.0f,
                0.5f
        );
        WorkloadSource baseWorkloadSource = new NoiseWorkloadSource(
                fractalNoise,
                0.001,
                0.2f,
                0.9f
        );
        ServerWorkloadFactorProvider factorProvider = new WorkloadFactorProviderFactory().create(definition);
        WorkloadSource workloadSource = new ScaledWorkloadSource(baseWorkloadSource, factorProvider);
        SimulationClock clock = new SimulationClock(Duration.ofMinutes(30));
        SimulationEngine engine = new SimulationEngine(clock);
        EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);
        engine.register(new WorkloadSystem(datacenter, workloadSource));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.register(energySystem);
        for (int i = 0; i < 4; i++) {
            SimulationTick tick = engine.step();
            System.out.printf(
                    Locale.US,
                    "Tick %d | Time: %s | Servers: %d | Power: %.1f W | Energy: %.3f kWh%n",
                    tick.index(),
                    tick.elapsedTime(),
                    datacenter.getServerCount(),
                    datacenter.getTotalItPowerWatts(),
                    energySystem.getConsumedEnergyKWh()
            );
        }
    }

}

package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.config.DatacenterConfigLoader;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.WorkloadFactorProviderFactory;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshot;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.ServerEnergySnapshot;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.workload.NoiseWorkloadSource;
import com.cpz.sim.datacenter.workload.ScaledWorkloadSource;
import com.cpz.sim.datacenter.workload.ServerWorkloadFactorProvider;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.PerlinNoise;

import java.nio.file.Path;
import java.time.Duration;

/**
 * @author CPZ
 */
public class EnergySnapshotSimulationDemo {

    private EnergySnapshotSimulationDemo() {
    }

    public static void main(String[] args) {
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("data/config/demo-datacenter-medium.json");
        DatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterDefinition definition = loader.load(configPath);
        Datacenter datacenter = new DatacenterFactory().create(definition);
        FractalNoise fractalNoise = new FractalNoise(
                new PerlinNoise(1234L),
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
        WorkloadSystem workloadSystem = new WorkloadSystem(datacenter, workloadSource);
        PowerConsumptionSystem powerConsumptionSystem = new PowerConsumptionSystem(datacenter);
        EnergyConsumptionSystem energyConsumptionSystem = new EnergyConsumptionSystem(datacenter);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
        engine.register(workloadSystem);
        engine.register(powerConsumptionSystem);
        engine.register(energyConsumptionSystem);
        EnergyConsumptionSnapshotProvider snapshotProvider = new EnergyConsumptionSnapshotProvider(datacenter, energyConsumptionSystem);
        System.out.println("Loaded datacenter config: " + definition.name());
        System.out.println("Workload source: FractalNoise + workloadFactor");
        System.out.println();
        for (int i = 0; i < 8; i++) {
            SimulationTick tick = engine.step();
            EnergyConsumptionSnapshot snapshot = snapshotProvider.snapshot(tick);
            printSnapshot(snapshot);
        }
    }

    private static void printSnapshot(EnergyConsumptionSnapshot snapshot) {
        System.out.printf(
                "Tick %d | Time: %.0f s | Servers: %d | Power: %.1f W | Energy: %.3f kWh%n",
                snapshot.tickIndex(),
                snapshot.elapsedSeconds(),
                snapshot.serverCount(),
                snapshot.totalItPowerWatts(),
                snapshot.consumedEnergyKWh()
        );
        for (ServerEnergySnapshot server : snapshot.servers()) {
            System.out.printf(
                    "  %s | %s | %s/%s/%s | util: %.2f | power: %.1f W%n",
                    server.serverCode(),
                    server.status(),
                    server.column(),
                    server.rackCode().value(),
                    server.slot(),
                    server.utilization(),
                    server.currentPowerWatts()
            );
        }
        System.out.println();
    }

}

package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.TemperatureSystemOptionsFactory;
import com.cpz.sim.datacenter.factory.WorkloadFactorProviderFactory;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshot;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.ServerTemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshotProvider;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.workload.NoiseWorkloadSource;
import com.cpz.sim.datacenter.workload.ScaledWorkloadSource;
import com.cpz.sim.datacenter.workload.ServerWorkloadFactorProvider;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.NoiseSource;
import com.cpz.utils.noise.PerlinNoise;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
/**
 * @author CPZ
 */
public class JsonTemperatureSimulationDemo {

    private static final String CONFIG_PATH = "data/config/datacenter-with-temperature.json";

    private JsonTemperatureSimulationDemo() {
    }

    public static void main(String[] args) {
        DatacenterDefinition definition = loadDefinition();
        Datacenter datacenter = new DatacenterFactory().create(definition);
        TemperatureSystemOptions temperatureOptions =
                new TemperatureSystemOptionsFactory().create(definition);
        WorkloadSource workloadSource = createWorkloadSource(definition);
        WorkloadSystem workloadSystem = new WorkloadSystem(datacenter, workloadSource);
        PowerConsumptionSystem powerConsumptionSystem = new PowerConsumptionSystem(datacenter);
        TemperatureSystem temperatureSystem = new TemperatureSystem(
                datacenter,
                temperatureOptions,
                new SimpleServerTemperatureModel()
        );
        EnergyConsumptionSystem energyConsumptionSystem =
                new EnergyConsumptionSystem(datacenter);
        EnergyConsumptionSnapshotProvider energySnapshotProvider =
                new EnergyConsumptionSnapshotProvider(datacenter, energyConsumptionSystem);
        TemperatureSnapshotProvider temperatureSnapshotProvider =
                new TemperatureSnapshotProvider(datacenter, temperatureSystem, temperatureOptions);
        SimulationEngine engine = new SimulationEngine(
                new SimulationClock(Duration.ofMinutes(5))
        );
        engine.register(workloadSystem);
        engine.register(powerConsumptionSystem);
        engine.register(temperatureSystem);
        engine.register(energyConsumptionSystem);
        printLoadedConfiguration(definition, datacenter, temperatureOptions);
        for (int i = 0; i < 6; i++) {
            SimulationTick tick = engine.step();
            EnergyConsumptionSnapshot energySnapshot = energySnapshotProvider.snapshot(tick);
            TemperatureSnapshot temperatureSnapshot = temperatureSnapshotProvider.snapshot(tick);
            printTickSummary(energySnapshot, temperatureSnapshot);
            printServerTemperatures(temperatureSnapshot);
            System.out.println();
        }
    }

    private static DatacenterDefinition loadDefinition() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        return loader.load(configPath(CONFIG_PATH));
    }

    private static WorkloadSource createWorkloadSource(DatacenterDefinition definition) {
        NoiseSource noiseSource = new FractalNoise(
                new PerlinNoise(1234L),
                5,
                1.0f,
                2.0f,
                0.5f
        );
        WorkloadSource baseWorkloadSource = new NoiseWorkloadSource(
                noiseSource,
                0.001,
                0.20f,
                0.90f
        );
        ServerWorkloadFactorProvider factorProvider =
                new WorkloadFactorProviderFactory().create(definition);
        return new ScaledWorkloadSource(baseWorkloadSource, factorProvider);
    }

    private static void printLoadedConfiguration(
            DatacenterDefinition definition,
            Datacenter datacenter,
            TemperatureSystemOptions temperatureOptions
    ) {
        System.out.printf(
                Locale.US,
                "Loaded datacenter: %s%n",
                definition.name()
        );
        System.out.printf(
                Locale.US,
                "Racks: %d | Servers: %d%n",
                datacenter.getRackCount(),
                datacenter.getServerCount()
        );
        System.out.printf(
                Locale.US,
                "Temperature options | ambient=%.2f °C | initial=%.2f °C | capacity=%.2f J/°C | dissipation=%.2f W/°C%n%n",
                temperatureOptions.ambientTemperatureCelsius(),
                temperatureOptions.defaultInitialTemperatureCelsius(),
                temperatureOptions.thermalCapacityJoulesPerCelsius(),
                temperatureOptions.heatDissipationWattsPerCelsius()
        );
    }

    private static void printTickSummary(
            EnergyConsumptionSnapshot energySnapshot,
            TemperatureSnapshot temperatureSnapshot
    ) {
        System.out.printf(
                Locale.US,
                "Tick %d | Time: %.0f s | IT Power: %.1f W | Energy: %.3f kWh | Avg Temp: %.2f °C | Max Temp: %.2f °C%n",
                energySnapshot.tickIndex(),
                energySnapshot.elapsedSeconds(),
                energySnapshot.totalItPowerWatts(),
                energySnapshot.consumedEnergyKWh(),
                temperatureSnapshot.averageTemperatureCelsius(),
                temperatureSnapshot.maxTemperatureCelsius()
        );
    }

    private static void printServerTemperatures(TemperatureSnapshot snapshot) {
        for (ServerTemperatureSnapshot server : snapshot.servers()) {
            System.out.printf(
                    Locale.US,
                    "  - %s | rack=%s | slot=%s | status=%s | util=%.2f | power=%.1f W | temp=%.2f °C%n",
                    server.serverCode(),
                    server.rackCode().value(),
                    server.slot(),
                    server.status(),
                    server.utilization(),
                    server.currentPowerWatts(),
                    server.temperatureCelsius()
            );
        }
    }

    private static Path configPath(String configPath) {
        Path path = Path.of(configPath).toAbsolutePath().normalize();
        if (!path.toFile().isFile()) throw new IllegalStateException("Missing config file: " + path);
        return path;
    }
}

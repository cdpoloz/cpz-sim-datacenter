package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.workload.NoiseWorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.NoiseSource;
import com.cpz.utils.noise.PerlinNoise;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * @author CPZ
 */
public class TemperatureSimulationDemo {

    private TemperatureSimulationDemo() {
    }

    public static void main(String[] args) {
        Datacenter datacenter = createDatacenter();
        WorkloadSystem workloadSystem = new WorkloadSystem(
                datacenter,
                createNoiseWorkloadSource()
        );
        PowerConsumptionSystem powerConsumptionSystem = new PowerConsumptionSystem(datacenter);
        TemperatureSystemOptions temperatureOptions = new TemperatureSystemOptions(
                24.0,
                30.0,
                5000.0,
                8.0
        );
        TemperatureSystem temperatureSystem = new TemperatureSystem(
                datacenter,
                temperatureOptions,
                new SimpleServerTemperatureModel()
        );
        EnergyConsumptionSystem energyConsumptionSystem = new EnergyConsumptionSystem(datacenter);
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
        for (int i = 0; i < 6; i++) {
            SimulationTick tick = engine.step();
            EnergyConsumptionSnapshot energySnapshot =
                    energySnapshotProvider.snapshot(tick);
            TemperatureSnapshot temperatureSnapshot =
                    temperatureSnapshotProvider.snapshot(tick);
            printTickSummary(energySnapshot, temperatureSnapshot);
            printServerTemperatures(temperatureSnapshot);
            System.out.println();
        }
    }

    private static Datacenter createDatacenter() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(
                rackCode,
                new RackLocation("A01", "R01"),
                42
        );
        ServerConfig standardServerConfig = new ServerConfig(
                "SRV-TEMP-DEMO-001",
                "CPZ",
                "Temperature Demo Server",
                100.0f,
                500.0f
        );
        Server serverA = new Server(
                new ServerLocation(rackCode, "U01"),
                standardServerConfig,
                HardwareStatus.OK
        );
        Server serverB = new Server(
                new ServerLocation(rackCode, "U02"),
                standardServerConfig,
                HardwareStatus.OK
        );
        Server offlineServer = new Server(
                new ServerLocation(rackCode, "U03"),
                standardServerConfig,
                HardwareStatus.OFFLINE
        );
        return new Datacenter(
                List.of(rack),
                List.of(serverA, serverB, offlineServer)
        );
    }

    private static NoiseWorkloadSource createNoiseWorkloadSource() {
        NoiseSource noiseSource = new FractalNoise(
                new PerlinNoise(1234L),
                5,
                1.0f,
                2.0f,
                0.5f
        );

        return new NoiseWorkloadSource(
                noiseSource,
                0.001,
                0.20f,
                0.90f
        );
    }

    private static void printTickSummary(EnergyConsumptionSnapshot energySnapshot, TemperatureSnapshot temperatureSnapshot) {
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
                    "  - %s | slot=%s | status=%s | util=%.2f | power=%.1f W | temp=%.2f °C%n",
                    server.serverCode(),
                    server.slot(),
                    server.status(),
                    server.utilization(),
                    server.currentPowerWatts(),
                    server.temperatureCelsius()
            );
        }
    }

}

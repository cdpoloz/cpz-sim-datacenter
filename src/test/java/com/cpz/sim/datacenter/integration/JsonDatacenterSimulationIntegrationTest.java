package com.cpz.sim.datacenter.integration;


import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.model.Datacenter;
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
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author CPZ
 */
class JsonDatacenterSimulationIntegrationTest {

    private static Datacenter loadDatacenterFromJson() {
        JsonDatacenterConfigLoader loader = new JsonDatacenterConfigLoader();
        DatacenterDefinition definition = loader.load(
                resourcePath("datacenter/valid-datacenter.json")
        );
        DatacenterFactory factory = new DatacenterFactory();
        return factory.create(definition);
    }

    private static Path resourcePath(String resourceName) {
        try {
            return Path.of(
                    Objects.requireNonNull(
                            JsonDatacenterSimulationIntegrationTest.class
                                    .getClassLoader()
                                    .getResource(resourceName)
                    ).toURI()
            );
        } catch (URISyntaxException exception) {
            throw new AssertionError("Invalid test resource path: " + resourceName, exception);
        }
    }

    private static WorkloadSource createNoiseWorkloadSource() {
        PerlinNoise perlinNoise = new PerlinNoise(1234L);
        FractalNoise fractalNoise = new FractalNoise(perlinNoise, 5, 1.0f, 2.0f, 0.5f);
        return new NoiseWorkloadSource(fractalNoise, 0.001, 0.2f, 0.9f);
    }

    @Test
    void shouldRunNoiseSimulationFromJsonConfiguration() {
        Datacenter datacenter = loadDatacenterFromJson();
        WorkloadSource workloadSource = createNoiseWorkloadSource();
        SimulationClock clock = new SimulationClock(Duration.ofMinutes(30));
        SimulationEngine engine = new SimulationEngine(clock);
        EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);
        engine.register(new WorkloadSystem(datacenter, workloadSource));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.register(energySystem);
        double[] expectedPowerWatts = {
                432.9,
                416.5,
                424.1,
                412.6
        };
        double[] expectedEnergyKWh = {
                0.216,
                0.425,
                0.637,
                0.843
        };
        for (int i = 0; i < 4; i++) {
            SimulationTick tick = engine.step();
            assertEquals(i + 1L, tick.index());
            assertEquals(expectedPowerWatts[i], datacenter.getTotalItPowerWatts(), 0.1);
            assertEquals(expectedEnergyKWh[i], energySystem.getConsumedEnergyKWh(), 0.0015);
        }
    }

}

package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.input.NoiseServerPowerInputSource;
import com.cpz.sim.datacenter.input.PowerInputGranularity;
import com.cpz.sim.datacenter.input.RackPowerToServerPowerInputSource;
import com.cpz.sim.datacenter.input.ServerPowerInputSource;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.system.PowerInputSystem;
import com.cpz.sim.foundation.time.SimulationTick;
import com.cpz.utils.noise.NoiseSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerInputSourceFactoryTest {

    private static final NoiseSource CONSTANT_NOISE = position -> 0.5f;

    @Test
    void shouldCreateServerLevelSourceByDefault() {
        DatacenterDefinition definition = definition(null);
        Datacenter datacenter = new DatacenterFactory().create(definition);

        ServerPowerInputSource source = new PowerInputSourceFactory()
                .create(definition, datacenter, CONSTANT_NOISE, 0.001, 0.2, 0.8);

        assertInstanceOf(NoiseServerPowerInputSource.class, source);
    }

    @Test
    void shouldCreateServerLevelSourceWhenConfiguredExplicitly() {
        DatacenterDefinition definition = definition(PowerInputGranularity.SERVER);
        Datacenter datacenter = new DatacenterFactory().create(definition);

        ServerPowerInputSource source = new PowerInputSourceFactory()
                .create(definition, datacenter, CONSTANT_NOISE, 0.001, 0.2, 0.8);

        assertInstanceOf(NoiseServerPowerInputSource.class, source);
    }

    @Test
    void shouldCreateRackLevelAdapterWhenConfigured() {
        DatacenterDefinition definition = definition(PowerInputGranularity.RACK);
        Datacenter datacenter = new DatacenterFactory().create(definition);

        ServerPowerInputSource source = new PowerInputSourceFactory()
                .create(definition, datacenter, CONSTANT_NOISE, 0.001, 0.2, 0.8);

        assertInstanceOf(RackPowerToServerPowerInputSource.class, source);
        assertEquals(205.0, source.currentPowerWatts(datacenter.getServers().getFirst(), tick()), 0.0001);
        assertEquals(205.0, source.currentPowerWatts(datacenter.getServers().get(1), tick()), 0.0001);
    }

    @Test
    void shouldRunPowerDrivenRackGranularityStep() {
        DatacenterDefinition definition = definition(PowerInputGranularity.RACK);
        Datacenter datacenter = new DatacenterFactory().create(definition);
        ServerPowerInputSource source = new PowerInputSourceFactory()
                .create(definition, datacenter, CONSTANT_NOISE, 0.001, 0.2, 0.8);
        PowerInputSystem system = new PowerInputSystem(datacenter, source);

        system.update(tick());

        assertTrue(datacenter.getServers().getFirst().getCurrentPowerWatts() > 0.0f);
        assertTrue(datacenter.getServers().get(1).getCurrentPowerWatts() > 0.0f);
        assertEquals(410.0f, datacenter.getTotalItPowerWatts());
    }

    private static DatacenterDefinition definition(PowerInputGranularity granularity) {
        return new DatacenterDefinition(
                "Power Input Source Factory Test",
                new DatacenterLayoutDefinition(
                        List.of(new RackDefinition("R01", "A01", "R01", List.of("U01", "U02")))
                ),
                List.of(
                        new ServerModelDefinition(
                                "MODEL-01",
                                "CPZ",
                                "Power Test Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "A01",
                                "R01",
                                "U01",
                                "MODEL-01",
                                "OK",
                                ServerRole.GENERAL_PURPOSE,
                                1.0f
                        ),
                        new ServerDefinition(
                                "A01",
                                "R01",
                                "U02",
                                "MODEL-01",
                                "OK",
                                ServerRole.GENERAL_PURPOSE,
                                1.0f
                        )
                ),
                null,
                null,
                null,
                DatacenterDataInputMode.POWER_DRIVEN,
                granularity
        );
    }

    private static SimulationTick tick() {
        return new SimulationTick(1L, Duration.ofMinutes(1), Duration.ofMinutes(1));
    }
}

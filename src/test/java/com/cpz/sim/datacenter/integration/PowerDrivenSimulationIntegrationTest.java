package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSnapshotCoordinator;
import com.cpz.sim.datacenter.cooling.CoolingSystemOptions;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.DatacenterCoolingTickInputProvider;
import com.cpz.sim.datacenter.cooling.ExhaustCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
import com.cpz.sim.datacenter.health.HealthThreshold;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.datacenter.input.ServerPowerInputSource;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.system.CoolingSystem;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerInputSystem;
import com.cpz.sim.datacenter.system.ServerHealthSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerDrivenSimulationIntegrationTest {

    private static final double EPSILON = 0.000001;

    @Test
    void shouldRunPowerDrivenPipelineUsingServerPowerAsInput() {
        Datacenter datacenter = createDatacenter();
        Server server = datacenter.getServers().getFirst();

        AtomicReference<CoolingSnapshot> coolingSnapshot = new AtomicReference<>();

        ServerPowerInputSource powerInputSource =
                (ignored, tick) -> 100.0 + tick.index() * 50.0;

        PowerInputSystem powerInputSystem =
                new PowerInputSystem(datacenter, powerInputSource);

        CoolingConfiguration coolingConfiguration =
                createCoolingConfiguration(server.getLocation());

        CoolingSystem coolingSystem =
                new CoolingSystem(coolingConfiguration);

        CoolingSnapshotTemperatureReferenceProvider temperatureReferenceProvider =
                new CoolingSnapshotTemperatureReferenceProvider(coolingConfiguration);

        CoolingSnapshotCoordinator coolingSnapshotCoordinator =
                new CoolingSnapshotCoordinator(
                        new DatacenterCoolingTickInputProvider(datacenter),
                        coolingSystem,
                        temperatureReferenceProvider
                );

        TemperatureSystem temperatureSystem =
                new TemperatureSystem(
                        datacenter,
                        temperatureOptions(),
                        new SimpleServerTemperatureModel(),
                        temperatureReferenceProvider
                );

        ServerHealthSystem healthSystem =
                new ServerHealthSystem(
                        datacenter,
                        temperatureSystem,
                        new ServerHealthOptions(
                                new HealthThreshold(0.90, 0.85),
                                new HealthThreshold(80.0, 75.0)
                        )
                );

        EnergyConsumptionSystem energySystem =
                new EnergyConsumptionSystem(datacenter);

        SimulationEngine engine =
                new SimulationEngine(new SimulationClock(Duration.ofMinutes(1)));

        engine.register(powerInputSystem);
        engine.register(tick -> coolingSnapshot.set(coolingSnapshotCoordinator.update(tick)));
        engine.register(temperatureSystem);
        engine.register(healthSystem);
        engine.register(energySystem);

        SimulationTick firstTick = engine.step();

        assertEquals(1L, firstTick.index());
        assertEquals(150.0f, server.getCurrentPowerWatts());
        assertEquals(0.25, server.getUtilization(), EPSILON);
        assertEquals(150.0, datacenter.getTotalItPowerWatts(), EPSILON);
        assertEquals(150.0, coolingSnapshot.get().zones().getFirst().generatedHeatWatts(), EPSILON);
        assertEquals(2.5, energySystem.getConsumedEnergyWh(), EPSILON);
        assertTrue(temperatureSystem.getThermalState(server.getCode()).getTemperatureCelsius() > 25.0);

        SimulationTick secondTick = engine.step();

        assertEquals(2L, secondTick.index());
        assertEquals(200.0f, server.getCurrentPowerWatts());
        assertEquals(0.50, server.getUtilization(), EPSILON);
        assertEquals(200.0, datacenter.getTotalItPowerWatts(), EPSILON);
        assertEquals(200.0, coolingSnapshot.get().zones().getFirst().generatedHeatWatts(), EPSILON);
        assertEquals(5.833333333333333, energySystem.getConsumedEnergyWh(), EPSILON);
        assertEquals(HardwareStatus.OK, server.getStatus());
    }

    private static Datacenter createDatacenter() {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(
                rackCode,
                new RackLocation("A01", "R01"),
                42
        );
        ServerConfig serverConfig =
                new ServerConfig(
                        "MODEL-01",
                        "CPZ",
                        "Power Driven Test Server",
                        100.0f,
                        300.0f
                );
        Server server =
                new Server(
                        new ServerLocation("A01", rackCode, "U01"),
                        serverConfig,
                        HardwareStatus.OK,
                        ServerRole.AI
                );

        return new Datacenter(List.of(rack), List.of(server));
    }

    private static CoolingConfiguration createCoolingConfiguration(ServerLocation serverLocation) {
        String zoneCode = "ZONE-01";

        CoolingZoneDefinition zone =
                new CoolingZoneDefinition(
                        zoneCode,
                        Set.of(serverLocation)
                );

        CoolingZoneInfluence influence =
                new CoolingZoneInfluence(zoneCode, 1.0);

        SupplyCoolingUnitDefinition supply =
                new SupplyCoolingUnitDefinition(
                        "SUPPLY-01",
                        4.0,
                        0.0,
                        12_000.0,
                        18.0,
                        List.of(influence),
                        true
                );

        ExhaustCoolingUnitDefinition exhaust =
                new ExhaustCoolingUnitDefinition(
                        "EXHAUST-01",
                        4.0,
                        0.0,
                        List.of(influence),
                        true
                );

        return new CoolingConfiguration(
                List.of(zone),
                List.of(supply, exhaust),
                CoolingSystemOptions.defaults()
        );
    }

    private static TemperatureSystemOptions temperatureOptions() {
        return new TemperatureSystemOptions(
                24.0,
                25.0,
                5000.0,
                8.0
        );
    }
}
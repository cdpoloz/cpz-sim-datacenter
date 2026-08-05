package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSystemOptions;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.ServerHeatLoadProvider;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
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
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoolingSimulationSystemTest {

    private static final ServerLocation SERVER_LOCATION =
            new ServerLocation(
                    "A01",
                    "RACK-A01-R01",
                    "U01"
            );

    private static final ServerConfig SERVER_CONFIG =
            new ServerConfig(
                    "model-01",
                    "Example",
                    "Server X",
                    100.0f,
                    300.0f
            );

    @Test
    void shouldRejectNullDependencies() {
        CoolingSystem coolingSystem =
                new CoolingSystem(configuration());

        ServerHeatLoadProvider heatLoadProvider =
                new ServerHeatLoadProvider(datacenter(createServer()));

        NullPointerException coolingException = assertThrows(
                NullPointerException.class,
                () -> new CoolingSimulationSystem(
                        null,
                        heatLoadProvider
                )
        );

        assertEquals(
                "coolingSystem must not be null",
                coolingException.getMessage()
        );

        NullPointerException providerException = assertThrows(
                NullPointerException.class,
                () -> new CoolingSimulationSystem(
                        coolingSystem,
                        null
                )
        );

        assertEquals(
                "heatLoadProvider must not be null",
                providerException.getMessage()
        );
    }

    @Test
    void shouldHaveNoSnapshotBeforeFirstUpdate() {
        CoolingSystem coolingSystem =
                new CoolingSystem(configuration());

        CoolingSimulationSystem simulationSystem =
                createSimulationSystem(
                        coolingSystem,
                        createServer()
                );

        assertTrue(simulationSystem.lastSnapshot().isEmpty());
        assertSame(
                coolingSystem,
                simulationSystem.coolingSystem()
        );
    }

    @Test
    void shouldRejectNullSimulationTick() {
        CoolingSimulationSystem simulationSystem =
                createSimulationSystem(
                        new CoolingSystem(configuration()),
                        createServer()
                );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> simulationSystem.update(null)
        );

        assertEquals(
                "tick must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldCreateSnapshotUsingSimulationTickIndexAndCurrentPower() {
        Server server = createServer();
        server.setUtilization(0.50);
        server.updatePowerConsumption();

        CoolingSimulationSystem simulationSystem =
                createSimulationSystem(
                        new CoolingSystem(configuration()),
                        server
                );

        simulationSystem.update(tick(7L));

        CoolingSnapshot snapshot =
                simulationSystem.lastSnapshot().orElseThrow();

        assertEquals(7L, snapshot.tickIndex());
        assertEquals(
                200.0,
                snapshot.zones()
                        .getFirst()
                        .generatedHeatWatts()
        );
    }

    @Test
    void shouldReplaceSnapshotAndReadUpdatedPowerOnEveryTick() {
        Server server = createServer();

        CoolingSimulationSystem simulationSystem =
                createSimulationSystem(
                        new CoolingSystem(configuration()),
                        server
                );

        simulationSystem.update(tick(1L));

        CoolingSnapshot firstSnapshot =
                simulationSystem.lastSnapshot().orElseThrow();

        assertEquals(
                100.0,
                firstSnapshot.zones()
                        .getFirst()
                        .generatedHeatWatts()
        );

        server.setUtilization(0.75);
        server.updatePowerConsumption();

        simulationSystem.update(tick(2L));

        CoolingSnapshot secondSnapshot =
                simulationSystem.lastSnapshot().orElseThrow();

        assertNotSame(firstSnapshot, secondSnapshot);
        assertEquals(2L, secondSnapshot.tickIndex());
        assertEquals(
                250.0,
                secondSnapshot.zones()
                        .getFirst()
                        .generatedHeatWatts()
        );
    }

    @Test
    void shouldReflectCoolingUnitStateChangeOnNextTick() {
        Server server = createServer();

        CoolingSystem coolingSystem =
                new CoolingSystem(configuration());

        CoolingSimulationSystem simulationSystem =
                createSimulationSystem(
                        coolingSystem,
                        server
                );

        simulationSystem.update(tick(1L));

        CoolingSnapshot enabledSnapshot =
                simulationSystem.lastSnapshot().orElseThrow();

        assertTrue(
                enabledSnapshot.units()
                        .getFirst()
                        .enabled()
        );
        assertEquals(
                12_000.0,
                enabledSnapshot.zones()
                        .getFirst()
                        .availableCoolingCapacityWatts()
        );

        coolingSystem.toggle("SUPPLY-01");

        simulationSystem.update(tick(2L));

        CoolingSnapshot disabledSnapshot =
                simulationSystem.lastSnapshot().orElseThrow();

        assertFalse(
                disabledSnapshot.units()
                        .getFirst()
                        .enabled()
        );
        assertEquals(
                0.0,
                disabledSnapshot.zones()
                        .getFirst()
                        .availableCoolingCapacityWatts()
        );
        assertEquals(
                server.getCurrentPowerWatts(),
                disabledSnapshot.zones()
                        .getFirst()
                        .coolingDeficitWatts()
        );
    }

    private static CoolingSimulationSystem createSimulationSystem(
            CoolingSystem coolingSystem,
            Server server
    ) {
        Datacenter datacenter = datacenter(server);

        return new CoolingSimulationSystem(
                coolingSystem,
                new ServerHeatLoadProvider(datacenter)
        );
    }

    private static Datacenter datacenter(Server server) {
        Rack rack = createRack();

        return new Datacenter(
                List.of(rack),
                List.of(server)
        );
    }

    private static Server createServer() {
        return new Server(
                SERVER_LOCATION,
                SERVER_CONFIG,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );
    }

    private static Rack createRack() {
        return new Rack(
                new RackCode("RACK-A01-R01"),
                new RackLocation("A01", "R01"),
                42
        );
    }

    private static CoolingConfiguration configuration() {
        CoolingZoneDefinition zone =
                new CoolingZoneDefinition(
                        "ZONE-01",
                        Set.of(SERVER_LOCATION)
                );

        SupplyCoolingUnitDefinition supply =
                new SupplyCoolingUnitDefinition(
                        "SUPPLY-01",
                        4.0,
                        12_000.0,
                        18.0,
                        List.of(
                                new CoolingZoneInfluence(
                                        "ZONE-01",
                                        1.0
                                )
                        ),
                        true
                );

        return new CoolingConfiguration(
                List.of(zone),
                List.of(supply),
                CoolingSystemOptions.defaults()
        );
    }

    private static SimulationTick tick(long index) {
        Duration deltaTime = Duration.ofMinutes(1);

        return new SimulationTick(
                index,
                deltaTime.multipliedBy(index),
                deltaTime
        );
    }

    @Test
    void shouldResetCoolingSystemAndClearLastSnapshot() {
        CoolingSystem coolingSystem = new CoolingSystem(configuration());
        CoolingSimulationSystem simulationSystem = createSimulationSystem(coolingSystem, createServer());
        coolingSystem.disable("SUPPLY-01");
        simulationSystem.update(tick(1L));
        assertFalse(coolingSystem.isEnabled("SUPPLY-01"));
        assertTrue(simulationSystem.lastSnapshot().isPresent());
        simulationSystem.reset();
        assertTrue(coolingSystem.isEnabled("SUPPLY-01"));
        assertTrue(simulationSystem.lastSnapshot().isEmpty());
    }

}
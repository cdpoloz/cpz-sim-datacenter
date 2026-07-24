package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.health.HealthThreshold;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.datacenter.health.ServerHealthState;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class ServerHealthSystemTest {

    private static final String SERVER_CODE = "A01-RACK-A01-R01-U01";

    private static final ServerHealthOptions HEALTH_OPTIONS =
            new ServerHealthOptions(
                    new HealthThreshold(0.90, 0.85),
                    new HealthThreshold(80.0, 75.0)
            );

    @Test
    void changesOkServerToAlertWhenUtilizationReachesThreshold() {
        TestFixture fixture = createFixture(HardwareStatus.OK, 0.90, 25.0);
        ServerHealthSystem healthSystem = createHealthSystem(fixture);
        healthSystem.update(tickAtSeconds(1, 60, 60));
        ServerHealthState healthState = healthSystem.getHealthState(SERVER_CODE);
        assertAll(
                () -> assertEquals(HardwareStatus.ALERT, fixture.server().getStatus()),
                () -> assertTrue(healthState.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION)),
                () -> assertFalse(healthState.hasAlertReason(ServerAlertReason.HIGH_TEMPERATURE)),
                () -> assertEquals(Set.of(ServerAlertReason.HIGH_UTILIZATION), healthState.getAlertReasons())
        );
    }

    @Test
    void changesOkServerToAlertWhenTemperatureReachesThreshold() {
        TestFixture fixture = createFixture(HardwareStatus.OK, 0.50, 80.0);
        ServerHealthSystem healthSystem = createHealthSystem(fixture);
        healthSystem.update(tickAtSeconds(1, 60, 60));
        ServerHealthState healthState = healthSystem.getHealthState(SERVER_CODE);
        assertAll(
                () -> assertEquals(HardwareStatus.ALERT, fixture.server().getStatus()),
                () -> assertFalse(healthState.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION)),
                () -> assertTrue(healthState.hasAlertReason(ServerAlertReason.HIGH_TEMPERATURE)),
                () -> assertEquals(Set.of(ServerAlertReason.HIGH_TEMPERATURE), healthState.getAlertReasons())
        );
    }

    private static ServerHealthSystem createHealthSystem(TestFixture fixture) {
        TemperatureSystem temperatureSystem =
                new TemperatureSystem(
                        fixture.datacenter(),
                        new TemperatureSystemOptions(
                                25.0,
                                fixture.initialTemperatureCelsius(),
                                5000.0,
                                8.0
                        ),
                        new SimpleServerTemperatureModel()
                );

        return new ServerHealthSystem(fixture.datacenter(), temperatureSystem, HEALTH_OPTIONS);
    }

    private static TestFixture createFixture(HardwareStatus status, double utilization, double initialTemperatureCelsius) {
        RackCode rackCode = new RackCode("RACK-A01-R01");
        Rack rack = new Rack(rackCode, new RackLocation("A01", "R01"), 42);
        ServerConfig config = new ServerConfig("model-01", "Example", "Server X", 100.0f, 500.0f);
        Server server = new Server(
                new ServerLocation("A01", rackCode, "U01"),
                config,
                status,
                ServerRole.GENERAL_PURPOSE
        );
        server.setUtilization(utilization);
        server.updatePowerConsumption();
        Datacenter datacenter = new Datacenter(List.of(rack), List.of(server));
        return new TestFixture(datacenter, server, initialTemperatureCelsius);
    }

    private static SimulationTick tickAtSeconds(long index, long elapsedSeconds, long deltaSeconds) {
        return new SimulationTick(index, Duration.ofSeconds(elapsedSeconds), Duration.ofSeconds(deltaSeconds));
    }

    @Test
    void appliesUtilizationHysteresisUntilClearThresholdIsReached() {
        TestFixture fixture = createFixture(HardwareStatus.OK, 0.90, 25.0);
        ServerHealthSystem healthSystem = createHealthSystem(fixture);
        // Alcanza el umbral de alerta.
        healthSystem.update(tickAtSeconds(1, 60, 60));
        assertAll(
                () -> assertEquals(HardwareStatus.ALERT, fixture.server().getStatus()),
                () -> assertTrue(healthSystem.getHealthState(SERVER_CODE).hasAlertReason(ServerAlertReason.HIGH_UTILIZATION))
        );
        // Desciende dentro de la banda de histéresis.
        fixture.server().setUtilization(0.87);
        healthSystem.update(tickAtSeconds(2, 120, 60));
        assertAll(
                () -> assertEquals(HardwareStatus.ALERT, fixture.server().getStatus()),
                () -> assertTrue(healthSystem.getHealthState(SERVER_CODE).hasAlertReason(ServerAlertReason.HIGH_UTILIZATION))
        );
        // Alcanza el umbral de recuperación.
        fixture.server().setUtilization(0.85);
        healthSystem.update(tickAtSeconds(3, 180, 60));
        assertAll(
                () -> assertEquals(HardwareStatus.OK, fixture.server().getStatus()),
                () -> assertFalse(healthSystem.getHealthState(SERVER_CODE).hasAlertReason(ServerAlertReason.HIGH_UTILIZATION)),
                () -> assertFalse(healthSystem.getHealthState(SERVER_CODE).hasAlertReasons())
        );
    }

    private record TestFixture(
            Datacenter datacenter,
            Server server,
            double initialTemperatureCelsius
    ) {
    }

}

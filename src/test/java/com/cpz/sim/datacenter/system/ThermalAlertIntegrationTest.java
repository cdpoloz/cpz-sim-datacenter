package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.config.DatacenterConfigException;
import com.cpz.sim.datacenter.config.definition.HealthSystemOptionsDefinition;
import com.cpz.sim.datacenter.config.definition.HealthThresholdDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidationException;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.ServerHealthOptionsFactory;
import com.cpz.sim.datacenter.health.*;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.temperature.*;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ThermalAlertIntegrationTest {
    @TempDir Path directory;

    private static final ServerHealthOptions OPTIONS = new ServerHealthOptions(
            new HealthThreshold(0.90, 0.85), new HealthThreshold(80, 75), new HealthThreshold(100, 95));

    @Test
    void tracksBothHysteresisBandsIncludingBoundariesAndDirectJumps() {
        ServerHealthState state = new ServerHealthState("server");
        double[] temperatures = {79, 80, 78, 100, 98, 95, 78, 75, 110, 70};
        ThermalAlertLevel[] expected = {ThermalAlertLevel.NORMAL, ThermalAlertLevel.ELEVATED,
                ThermalAlertLevel.ELEVATED, ThermalAlertLevel.CRITICAL, ThermalAlertLevel.CRITICAL,
                ThermalAlertLevel.ELEVATED, ThermalAlertLevel.ELEVATED, ThermalAlertLevel.NORMAL,
                ThermalAlertLevel.CRITICAL, ThermalAlertLevel.NORMAL};
        for (int i = 0; i < temperatures.length; i++) {
            state.updateTemperature(temperatures[i], OPTIONS);
            assertEquals(expected[i], state.getThermalAlertLevel(), "temperature " + temperatures[i]);
            assertEquals(expected[i] != ThermalAlertLevel.NORMAL, state.hasAlertReason(ServerAlertReason.HIGH_TEMPERATURE));
        }
    }

    @Test
    void legacyOptionsDoNotInferCriticalityFromDegrees() {
        ServerHealthState state = new ServerHealthState("server");
        state.updateTemperature(150, new ServerHealthOptions(OPTIONS.utilizationThreshold(), OPTIONS.temperatureThreshold()));
        assertEquals(ThermalAlertLevel.ELEVATED, state.getThermalAlertLevel());
        assertNull(ServerHealthOptions.defaults().criticalTemperatureThreshold());
        state.clearAlertReasons();
        assertEquals(ThermalAlertLevel.NORMAL, state.getThermalAlertLevel());
    }

    @Test
    void customCriticalThresholdAndClearResetBothBands() {
        ServerHealthState state = new ServerHealthState("server");
        ServerHealthOptions options = new ServerHealthOptions(OPTIONS.utilizationThreshold(),
                OPTIONS.temperatureThreshold(), new HealthThreshold(90, 85));
        state.updateTemperature(90, options);
        assertEquals(ThermalAlertLevel.CRITICAL, state.getThermalAlertLevel());
        state.clearAlertReasons();
        state.updateTemperature(88, options);
        assertEquals(ThermalAlertLevel.ELEVATED, state.getThermalAlertLevel());
    }

    @Test
    void rejectsOverlappingCriticalRecoveryAndInvalidThresholds() {
        for (HealthThreshold critical : new HealthThreshold[]{new HealthThreshold(80, 79),
                new HealthThreshold(100, 74), new HealthThreshold(100, 79)}) {
            assertThrows(IllegalArgumentException.class, () -> new ServerHealthOptions(
                    OPTIONS.utilizationThreshold(), OPTIONS.temperatureThreshold(), critical));
        }
        assertThrows(IllegalArgumentException.class, () -> new HealthThreshold(Double.NaN, 95));
        assertThrows(IllegalArgumentException.class, () -> new HealthThreshold(100, 100));
    }

    @Test
    void snapshotsPreserveCriticalEscalationAndDowngradeWithoutChangingReasons() throws Exception {
        var definition = new JsonDatacenterConfigLoader().load(Path.of("src/test/resources/datacenter/valid-datacenter.json"));
        var datacenter = new DatacenterFactory().create(definition);
        var temperatures = new TemperatureSystem(datacenter,
                new TemperatureSystemOptions(24, 24, 5000, 8), new SimpleServerTemperatureModel());
        var system = new ServerHealthSystem(datacenter, temperatures, OPTIONS);
        var provider = new HealthSnapshotProvider(datacenter, system, temperatures);
        var server = datacenter.getServers().getFirst();
        var thermal = temperatures.getThermalState(server.getCode());
        server.setUtilization(0.95);
        thermal.setTemperatureCelsius(80);
        system.update(tick(1));
        var elevated = provider.snapshot(tick(1)).servers().getFirst();
        thermal.setTemperatureCelsius(100);
        system.update(tick(2));
        var criticalSnapshot = provider.snapshot(tick(2));
        var critical = criticalSnapshot.servers().getFirst();
        assertEquals(ThermalAlertLevel.ELEVATED, elevated.thermalAlertLevel());
        assertEquals(ThermalAlertLevel.CRITICAL, critical.thermalAlertLevel());
        assertEquals(elevated.alertReasons(), critical.alertReasons());
        assertEquals(elevated.location(), critical.location());
        assertEquals(HardwareStatus.ALERT, critical.status());
        assertEquals(2, criticalSnapshot.tickIndex());
        assertEquals(120, criticalSnapshot.elapsedSeconds());
        thermal.setTemperatureCelsius(95);
        system.update(tick(3));
        assertEquals(ThermalAlertLevel.ELEVATED, provider.snapshot(tick(3)).servers().getFirst().thermalAlertLevel());
        thermal.setTemperatureCelsius(75);
        system.update(tick(4));
        var recoveredThermal = provider.snapshot(tick(4)).servers().getFirst();
        assertEquals(ThermalAlertLevel.NORMAL, recoveredThermal.thermalAlertLevel());
        assertEquals(Set.of(ServerAlertReason.HIGH_UTILIZATION), recoveredThermal.alertReasons());
        assertEquals(HardwareStatus.ALERT, recoveredThermal.status());
        // Previously captured records must not change when the engine advances.
        assertEquals(ThermalAlertLevel.CRITICAL, critical.thermalAlertLevel());
        thermal.setTemperatureCelsius(110);
        server.setStatus(HardwareStatus.OFFLINE);
        system.update(tick(5));
        var offline = provider.snapshot(tick(5)).servers().getFirst();
        assertEquals(ThermalAlertLevel.NORMAL, offline.thermalAlertLevel());
        assertTrue(offline.alertReasons().isEmpty());
        assertEquals(HardwareStatus.OFFLINE, offline.status());
        server.setStatus(HardwareStatus.OK);
        system.update(tick(6));
        assertEquals(ThermalAlertLevel.CRITICAL, system.getHealthState(server.getCode()).getThermalAlertLevel());
        system.reset();
        assertEquals(ThermalAlertLevel.NORMAL, system.getHealthState(server.getCode()).getThermalAlertLevel());
        assertEquals(HardwareStatus.OK, server.getStatus());
    }

    @Test
    void legacySnapshotConstructorRetainsDescriptorAndDoesNotInferCriticality() {
        var snapshot = new ServerHealthSnapshot("server", "C01", new RackCode("R01"), "S01",
                HardwareStatus.ALERT, Set.of(ServerAlertReason.HIGH_TEMPERATURE), 0.5, 150);
        assertEquals(ThermalAlertLevel.ELEVATED, snapshot.thermalAlertLevel());
        assertThrows(IllegalArgumentException.class, () -> new ServerHealthSnapshot("server", "C01", new RackCode("R01"), "S01",
                HardwareStatus.OK, Set.of(), 0.5, 100, ThermalAlertLevel.CRITICAL));
        assertThrows(NullPointerException.class, () -> new ServerHealthSnapshot("server", "C01", new RackCode("R01"), "S01",
                HardwareStatus.OK, Set.of(), 0.5, 25, null));
    }

    @Test
    void loadsOptionalCriticalJsonAndCreatesRuntimeOptions() throws Exception {
        var definition = new JsonDatacenterConfigLoader().load(config("""
                {"alertAtOrAbove":100,"clearAtOrBelow":95}
                """));
        var options = new ServerHealthOptionsFactory().create(definition);
        assertEquals(new HealthThreshold(100, 95), options.criticalTemperatureThreshold());
        assertEquals(new HealthThreshold(80, 75), options.temperatureThreshold());
    }

    @Test
    void preservesLegacyHealthJsonAndTwoArgumentDefinition() throws Exception {
        var definition = new JsonDatacenterConfigLoader().load(config(null));
        assertNull(definition.health().criticalTemperatureCelsius());
        assertNull(new ServerHealthOptionsFactory().create(definition).criticalTemperatureThreshold());
        var legacy = new HealthSystemOptionsDefinition(new HealthThresholdDefinition(.9, .85), new HealthThresholdDefinition(80, 75));
        assertNull(new ServerHealthOptionsFactory().create(legacy).criticalTemperatureThreshold());
    }

    @Test
    void rejectsMalformedCriticalJsonWithoutWeakeningExistingLoader() throws Exception {
        for (String value : new String[]{"null", "[]", "5", "{}",
                "{\"alertAtOrAbove\":100}",
                "{\"alertAtOrAbove\":null,\"clearAtOrBelow\":95}",
                "{\"alertAtOrAbove\":100,\"clearAtOrBelow\":95,\"typo\":1}"}) {
            Path path = config(value);
            assertThrows(DatacenterConfigException.class, () -> new JsonDatacenterConfigLoader().load(path), value);
        }
    }

    @Test
    void validatorAndFactoryBothRejectInvalidOrdering() throws Exception {
        for (String value : new String[]{"{\"alertAtOrAbove\":100,\"clearAtOrBelow\":74}",
                "{\"alertAtOrAbove\":80,\"clearAtOrBelow\":79}",
                "{\"alertAtOrAbove\":100,\"clearAtOrBelow\":100}"}) {
            var definition = new JsonDatacenterConfigLoader().load(config(value));
            assertThrows(IllegalArgumentException.class, () -> new ServerHealthOptionsFactory().create(definition.health()));
            assertThrows(DatacenterConfigValidationException.class, () -> new DatacenterConfigValidator().validate(definition));
        }
    }

    private Path config(String critical) throws Exception {
        String base = Files.readString(Path.of("src/test/resources/datacenter/valid-datacenter.json"));
        String health = "\"health\":{\"utilization\":{\"alertAtOrAbove\":0.9,\"clearAtOrBelow\":0.85},"
                + "\"temperatureCelsius\":{\"alertAtOrAbove\":80,\"clearAtOrBelow\":75}"
                + (critical == null ? "" : ",\"criticalTemperatureCelsius\":" + critical) + "},";
        Path path = directory.resolve("config.json");
        Files.writeString(path, "{" + health + base.substring(base.indexOf('{') + 1));
        return path;
    }

    private static SimulationTick tick(long index) {
        return new SimulationTick(index, Duration.ofMinutes(index), Duration.ofMinutes(1));
    }
}

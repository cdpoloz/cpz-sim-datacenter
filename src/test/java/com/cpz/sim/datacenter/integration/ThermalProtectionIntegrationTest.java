package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.*;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.foundation.time.SimulationTick;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ThermalProtectionIntegrationTest {
    @TempDir Path dir;
    private static SimulationTick tick(int i, int seconds) {
        return new SimulationTick(i, Duration.ofSeconds((long)i * seconds), Duration.ofSeconds(seconds));
    }

    @Test void realJsonFactoryAndCoupledSolverProtectEquipmentAndConserveEnergyWithAllCoolingOff() throws Exception {
        for (boolean enabled : new boolean[] {true, false}) {
            var mapper = JsonMapper.builder().build();
            var root = (ObjectNode)mapper.readTree(Path.of("data/config/thermal-protection-demo.json").toFile());
            ((ObjectNode)root.path("serverModels").get(0).path("thermalProtection")).put("enabled", enabled);
            Path file = dir.resolve("scenario-" + enabled + ".json");
            mapper.writeValue(file.toFile(), root);
            var definition = new JsonDatacenterConfigLoader().load(file);
            var dc = new DatacenterFactory().create(definition);
            var options = new TemperatureSystemOptionsFactory().create(definition);
            var temperatures = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel());
            var coupled = new CoupledRoomTemperatureSystemFactory().create(definition, dc, temperatures, Optional.empty()).orElseThrow();
            var systems = new DatacenterInputPipelineFactory().createInputSystems(definition, dc, temperatures, options,
                    (server, t) -> 1, null, coupled);
            var energy = new EnergyConsumptionSystem(dc);
            var health = new ServerHealthSystem(dc, temperatures, ServerHealthOptions.defaults());
            double integratedElectricalJoules = 0;
            boolean throttled = false;
            boolean shutdown = false;
            for (int i = 1; i <= 1440; i++) {
                var t = tick(i, 10);
                systems.forEach(system -> system.update(t));
                health.update(t); energy.update(t);
                throttled |= dc.getServers().stream().anyMatch(s -> s.getThermalProtectionState() == ThermalProtectionState.THROTTLED);
                shutdown |= dc.getServers().stream().anyMatch(s -> s.getThermalProtectionState() == ThermalProtectionState.SHUTDOWN);
                var thermal = coupled.lastSnapshot().orElseThrow();
                assertEquals(0, thermal.energyBalanceResidualJoules(), 1e-5);
                integratedElectricalJoules += thermal.inputElectricalEnergyJoules();
                assertEquals(energy.getConsumedEnergyWh(), integratedElectricalJoules / 3600, 1e-6);
            }
            if (enabled) {
                assertTrue(throttled); assertTrue(shutdown);
                for (Server s : dc.getServers()) {
                    assertEquals(ThermalProtectionState.SHUTDOWN, s.getThermalProtectionState());
                    assertEquals(5, s.getCurrentPowerWatts());
                    assertTrue(temperatures.getThermalState(s.getCode()).getTemperatureCelsius() < 110);
                }
                var snapshots = new HealthSnapshotProvider(dc, health, temperatures).snapshot(tick(1440, 10));
                assertTrue(snapshots.servers().stream().allMatch(s -> s.thermalProtectionState() == ThermalProtectionState.SHUTDOWN));
                systems.forEach(system -> system.reset());
                assertTrue(dc.getServers().stream().allMatch(s -> s.getThermalProtectionState() == ThermalProtectionState.NORMAL));
            } else {
                assertFalse(throttled); assertFalse(shutdown);
                assertTrue(dc.getServers().stream().allMatch(s -> temperatures.getThermalState(s.getCode()).getTemperatureCelsius() > 150));
            }
        }
    }

    @Test void standbyPowerAlsoMatchesTheFacilityLedgerWithRoomAndRackCoolingEquipmentPresent() throws Exception {
        var mapper = JsonMapper.builder().build();
        var root = (ObjectNode)mapper.readTree(Path.of("data/config/rack-cooling-comparison.json").toFile());
        var profile = mapper.readTree(Path.of("data/config/thermal-protection-demo.json").toFile())
                .path("serverModels").get(0).path("thermalProtection");
        ((ObjectNode)root.path("serverModels").get(0)).set("thermalProtection", profile);
        ((ObjectNode)root.path("temperature")).put("defaultInitialTemperatureCelsius", 110);
        for (String kind : new String[] {"supplyUnits", "exhaustUnits"})
            root.path("cooling").path(kind).forEach(unit -> ((ObjectNode)unit).put("initiallyEnabled", false));
        root.path("rackCooling").path("units").forEach(unit -> ((ObjectNode)unit).put("initiallyEnabled", false));
        Path file = dir.resolve("rack-standby.json"); mapper.writeValue(file.toFile(), root);
        var definition = new JsonDatacenterConfigLoader().load(file);
        var dc = new DatacenterFactory().create(definition);
        var options = new TemperatureSystemOptionsFactory().create(definition);
        var temps = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel());
        var cooling = new CoolingSystem(new CoolingConfigurationFactory().create(definition, dc).orElseThrow());
        var coupled = new CoupledRoomTemperatureSystemFactory().create(definition, dc, temps, Optional.of(cooling)).orElseThrow();
        var systems = new DatacenterInputPipelineFactory().createInputSystems(definition, dc, temps, options, (s,t) -> 1, null, coupled);
        var energy = new EnergyConsumptionSystem(dc);
        for (int i = 1; i <= 10; i++) {
            var t = tick(i, 60); systems.forEach(system -> system.update(t)); energy.update(t);
            var facility = coupled.lastFacilityEnergySnapshot().orElseThrow();
            assertEquals(energy.getConsumedEnergyWh(), facility.consumedItEnergyWh(), 1e-6);
            assertEquals(20, facility.currentItPowerWatts(), 1e-6);
            assertEquals(0, coupled.lastSnapshot().orElseThrow().energyBalanceResidualJoules(), 1e-5);
        }
    }

    @Test void powerDemandIsCappedBeforeThermalAndEnergyIntegration() throws Exception {
        var mapper = JsonMapper.builder().build();
        var root = (ObjectNode)mapper.readTree(Path.of("data/config/thermal-protection-demo.json").toFile());
        root.remove("roomAirTemperature"); root.put("dataInputMode", "POWER_DRIVEN");
        Path file = dir.resolve("power.json"); mapper.writeValue(file.toFile(), root);
        var definition = new JsonDatacenterConfigLoader().load(file);
        var dc = new DatacenterFactory().create(definition);
        var options = new TemperatureSystemOptionsFactory().create(definition);
        var temps = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel());
        dc.getServers().forEach(s -> temps.setTemperatureCelsius(s.getCode(), 95));
        var systems = new DatacenterInputPipelineFactory().createInputSystems(definition, dc, temps, options, null, (s,t) -> 300);
        assertInstanceOf(ServerThermalProtectionSystem.class, systems.getFirst());
        var t = tick(1, 60); systems.forEach(s -> s.update(t));
        var energy = new EnergyConsumptionSystem(dc); energy.update(t);
        assertEquals(360.0 / 60, energy.getConsumedEnergyWh(), 1e-5);
        dc.getServers().forEach(s -> { assertEquals(180, s.getCurrentPowerWatts()); assertEquals(.4, s.getUtilization(), 1e-6); });
    }

    @Test void rackObservationsStayAuthoritativeWhileThermallyShutdownAndCanTriggerRecovery() throws Exception {
        var mapper = JsonMapper.builder().build();
        var root = (ObjectNode)mapper.readTree(Path.of("data/config/thermal-protection-demo.json").toFile());
        root.remove("roomAirTemperature"); root.put("dataInputMode", "TEMPERATURE_DRIVEN");
        ((ObjectNode)root.path("serverModels").get(0).path("thermalProtection")).put("restartPolicy", "AUTOMATIC");
        Path file = dir.resolve("observed.json"); mapper.writeValue(file.toFile(), root);
        var definition = new JsonDatacenterConfigLoader().load(file);
        var dc = new DatacenterFactory().create(definition);
        var options = new TemperatureSystemOptionsFactory().create(definition);
        var temps = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel());
        double[] observed = {160};
        var input = new RackTemperatureInputSystem(dc, temps, (rack,t) -> observed[0], options);
        input.update(tick(1, 60));
        dc.getServers().forEach(s -> {
            assertEquals(ThermalProtectionState.SHUTDOWN, s.getThermalProtectionState());
            assertEquals(160, temps.getThermalState(s.getCode()).getTemperatureCelsius());
            assertEquals(5, s.getCurrentPowerWatts());
        });
        observed[0] = 60;
        input.update(tick(2, 60));
        assertEquals(HardwareStatus.OFFLINE, dc.getServers().iterator().next().getStatus());
        input.update(tick(3, 60));
        assertTrue(dc.getServers().stream().allMatch(s -> s.getStatus() == HardwareStatus.OK));
    }
}

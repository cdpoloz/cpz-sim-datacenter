package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.HotAisleDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.input.SimulatedAisleTemperatureInputSource;
import com.cpz.sim.datacenter.input.TemperatureInputGranularity;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.HealthSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshotProvider;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.RackTemperatureInputSystem;
import com.cpz.sim.datacenter.system.ServerHealthSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DatacenterInputPipelineFactoryTest {

    private static final double EPSILON = 0.0001;
    private static final TemperatureSystemOptions OPTIONS =
            new TemperatureSystemOptions(25.0, 25.0, 5000.0, 8.0);

    @Test
    void shouldWireTemperatureDrivenAislePipelineWithConfiguredHotAisles() {
        DatacenterDefinition definition = standardDefinition(TemperatureInputGranularity.AISLE);
        Datacenter datacenter = new DatacenterFactory().create(definition);
        TemperatureSystem temperatureSystem = temperatureSystem(datacenter);
        EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);
        ServerHealthSystem healthSystem =
                new ServerHealthSystem(datacenter, temperatureSystem, ServerHealthOptions.defaults());
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(1)));
        DatacenterInputPipelineFactory factory = new DatacenterInputPipelineFactory();

        List<Simulatable> inputSystems =
                factory.createInputSystems(definition, datacenter, temperatureSystem, OPTIONS, null, null);
        assertEquals(1, inputSystems.size());
        assertInstanceOf(RackTemperatureInputSystem.class, inputSystems.getFirst());
        factory.registerInputSystems(engine, definition, datacenter, temperatureSystem, OPTIONS, null, null);
        engine.register(healthSystem);
        engine.register(energySystem);

        SimulationTick tick = engine.step();

        assertSharedHotAisleTemperature(temperatureSystem, tick, "C02", "C03", "HA02");
        assertSharedHotAisleTemperature(temperatureSystem, tick, "C04", "C05", "HA03");
        assertSharedHotAisleTemperature(temperatureSystem, tick, "C06", "C07", "HA04");
        assertNotEquals(
                temperature(temperatureSystem, "C01"),
                temperature(temperatureSystem, "C08"),
                EPSILON
        );
    }

    @Test
    void shouldNotOverwriteAisleTemperatureBeforeOperationalSnapshot() {
        DatacenterDefinition definition = standardDefinition(TemperatureInputGranularity.AISLE);
        Datacenter datacenter = new DatacenterFactory().create(definition);
        TemperatureSystem temperatureSystem = temperatureSystem(datacenter);
        EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);
        ServerHealthSystem healthSystem =
                new ServerHealthSystem(datacenter, temperatureSystem, ServerHealthOptions.defaults());
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(1)));
        new DatacenterInputPipelineFactory()
                .registerInputSystems(engine, definition, datacenter, temperatureSystem, OPTIONS, null, null);
        engine.register(healthSystem);
        engine.register(energySystem);

        SimulationTick tick = engine.step();
        TemperatureSnapshot temperatureSnapshot =
                new TemperatureSnapshotProvider(datacenter, temperatureSystem, OPTIONS).snapshot(tick);
        DatacenterOperationalSnapshot operationalSnapshot =
                new DatacenterOperationalSnapshotProvider(datacenter).snapshot(
                        new EnergyConsumptionSnapshotProvider(datacenter, energySystem).snapshot(tick),
                        temperatureSnapshot,
                        new HealthSnapshotProvider(datacenter, healthSystem, temperatureSystem).snapshot(tick)
                );
        double expectedHa03Temperature =
                new SimulatedAisleTemperatureInputSource().temperatureCelsius("HA03", tick);

        assertEquals(expectedHa03Temperature, temperature(temperatureSystem, "C04"), EPSILON);
        assertEquals(expectedHa03Temperature, temperature(temperatureSystem, "C05"), EPSILON);
        assertEquals(
                expectedHa03Temperature,
                operationalSnapshot.racks().get(rackLocation("C04")).representativeTemperatureCelsius(),
                EPSILON
        );
        assertEquals(
                expectedHa03Temperature,
                operationalSnapshot.racks().get(rackLocation("C05")).representativeTemperatureCelsius(),
                EPSILON
        );
    }

    @Test
    void shouldKeepTemperatureDrivenRackPipelineOnRackInput() {
        DatacenterDefinition definition = standardDefinition(TemperatureInputGranularity.RACK);
        Datacenter datacenter = new DatacenterFactory().create(definition);
        TemperatureSystem temperatureSystem = temperatureSystem(datacenter);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(1)));
        new DatacenterInputPipelineFactory()
                .registerInputSystems(engine, definition, datacenter, temperatureSystem, OPTIONS, null, null);

        SimulationTick tick = engine.step();

        assertNotEquals(temperature(temperatureSystem, "C04"), temperature(temperatureSystem, "C05"), EPSILON);
        assertNotEquals(
                new SimulatedAisleTemperatureInputSource().temperatureCelsius("HA03", tick),
                temperature(temperatureSystem, "C04"),
                EPSILON
        );
    }

    private static void assertSharedHotAisleTemperature(
            TemperatureSystem temperatureSystem,
            SimulationTick tick,
            String firstColumn,
            String secondColumn,
            String aisleCode
    ) {
        double expected = new SimulatedAisleTemperatureInputSource().temperatureCelsius(aisleCode, tick);
        assertEquals(expected, temperature(temperatureSystem, firstColumn), EPSILON);
        assertEquals(expected, temperature(temperatureSystem, secondColumn), EPSILON);
        assertEquals(
                temperature(temperatureSystem, firstColumn),
                temperature(temperatureSystem, secondColumn),
                EPSILON
        );
    }

    private static TemperatureSystem temperatureSystem(Datacenter datacenter) {
        return new TemperatureSystem(datacenter, OPTIONS, new SimpleServerTemperatureModel());
    }

    private static double temperature(TemperatureSystem temperatureSystem, String column) {
        return temperatureSystem.getThermalState(serverCode(column)).getTemperatureCelsius();
    }

    private static DatacenterDefinition standardDefinition(TemperatureInputGranularity granularity) {
        return new DatacenterDefinition(
                "Datacenter Input Pipeline Factory Test",
                new DatacenterLayoutDefinition(
                        null,
                        standardHotAisles(),
                        List.of(
                                rackDefinition("C01"),
                                rackDefinition("C02"),
                                rackDefinition("C03"),
                                rackDefinition("C04"),
                                rackDefinition("C05"),
                                rackDefinition("C06"),
                                rackDefinition("C07"),
                                rackDefinition("C08")
                        )
                ),
                List.of(new ServerModelDefinition("MODEL-01", "CPZ", "Temperature Test Server", 100.0f, 300.0f)),
                List.of(
                        serverDefinition("C01"),
                        serverDefinition("C02"),
                        serverDefinition("C03"),
                        serverDefinition("C04"),
                        serverDefinition("C05"),
                        serverDefinition("C06"),
                        serverDefinition("C07"),
                        serverDefinition("C08")
                ),
                null,
                null,
                null,
                DatacenterDataInputMode.TEMPERATURE_DRIVEN,
                null,
                granularity
        );
    }

    private static List<HotAisleDefinition> standardHotAisles() {
        return List.of(
                new HotAisleDefinition("HA01", List.of("C01")),
                new HotAisleDefinition("HA02", List.of("C02", "C03")),
                new HotAisleDefinition("HA03", List.of("C04", "C05")),
                new HotAisleDefinition("HA04", List.of("C06", "C07")),
                new HotAisleDefinition("HA05", List.of("C08"))
        );
    }

    private static RackDefinition rackDefinition(String column) {
        return new RackDefinition(rackCode(column).value(), column, "R01", List.of("U01"));
    }

    private static ServerDefinition serverDefinition(String column) {
        return new ServerDefinition(
                rackCode(column).value(),
                "U01",
                "MODEL-01",
                "OK",
                ServerRole.GENERAL_PURPOSE
        );
    }

    private static RackLocation rackLocation(String column) {
        return new RackLocation(column, rackCode(column));
    }

    private static RackCode rackCode(String column) {
        return new RackCode("RACK-" + column + "-R01");
    }

    private static String serverCode(String column) {
        return column + "-" + rackCode(column).value() + "-U01";
    }
}

package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidationException;
import com.cpz.sim.datacenter.cooling.HeatRejectionDestination;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.system.RackCoolingSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RackCoolingConfigurationFactoryTest {
    @TempDir Path directory;

    private DatacenterDefinition definition(RackCoolingConfigDefinition rackCooling) {
        return new DatacenterDefinition("Equipment test", new DatacenterLayoutDefinition(List.of(
                new RackDefinition("R01", "C01", "R01", 42))), List.of(), List.of(),
                null, null, null, null, null, null,
                new RoomAirTemperatureOptionsDefinition(24, 2000000, 20, 0), rackCooling);
    }

    @Test
    void absentBlockProducesNoEquipmentOrDefaults() {
        var factory = new RackCoolingConfigurationFactory();
        assertTrue(factory.create(definition(null)).isEmpty());
        assertThrows(NullPointerException.class, () -> factory.create(null));
    }

    @Test
    void validatesBeforeConstructingEquipment() {
        var invalid = definition(new RackCoolingConfigDefinition(1.204, 1005, List.of(
                new RackCoolingUnitConfigDefinition("RC01", "C99", "R01", 24, 10000, 50,
                        0.1, 2000, 18, 500, HeatRejectionDestination.ROOM, true))));
        assertThrows(DatacenterConfigValidationException.class, () -> new RackCoolingConfigurationFactory().create(invalid));
    }

    @Test
    void mapsEveryJsonEquipmentParameterAndSupportsControlsWithoutStartingThermalSimulation() throws Exception {
        Path path = directory.resolve("datacenter.json");
        Files.writeString(path, """
                {"name":"Equipment test", "layout":{"racks":[
                    {"code":"R01","column":"C01","row":"R01","slotCount":42}]},
                 "serverModels":[],"servers":[],
                 "roomAirTemperature":{"initialTemperatureCelsius":24,
                    "effectiveThermalCapacityJoulesPerCelsius":2000000,
                    "environmentTemperatureCelsius":20,"environmentHeatTransferWattsPerCelsius":0},
                 "rackCooling":{"airDensityKilogramsPerCubicMeter":1.19,
                    "airSpecificHeatJoulesPerKilogramKelvin":1006,
                    "units":[{"code":"RC01","column":"C01","rackCode":"R01",
                      "initialAirTemperatureCelsius":25,"effectiveAirThermalCapacityJoulesPerCelsius":15000,
                      "roomHeatTransferWattsPerCelsius":40,"ratedAirflowCubicMetersPerSecond":0.15,
                      "ratedCoolingCapacityWatts":2100,"supplyAirTemperatureCelsius":19,
                      "ratedElectricalPowerWatts":600,"heatRejectionDestination":"EXTERNAL",
                      "initiallyEnabled":false}]}}
                """);
        var definition = new JsonDatacenterConfigLoader().load(path);
        var config = new RackCoolingConfigurationFactory().create(definition).orElseThrow();
        assertEquals(1.19, config.airDensityKilogramsPerCubicMeter());
        assertEquals(1006, config.airSpecificHeatJoulesPerKilogramKelvin());
        var unit = config.units().getFirst();
        assertEquals("RC01", unit.code());
        assertEquals(new RackLocation("C01", "R01"), unit.rackLocation());
        assertEquals(25, unit.initialAirTemperatureCelsius());
        assertEquals(15000, unit.effectiveAirThermalCapacityJoulesPerCelsius());
        assertEquals(40, unit.roomHeatTransferWattsPerCelsius());
        assertEquals(0.15, unit.ratedAirflowCubicMetersPerSecond());
        assertEquals(2100, unit.ratedCoolingCapacityWatts());
        assertEquals(19, unit.supplyAirTemperatureCelsius());
        assertEquals(600, unit.ratedElectricalPowerWatts());
        assertEquals(HeatRejectionDestination.EXTERNAL, unit.heatRejectionDestination());
        assertFalse(unit.initiallyEnabled());
        var system = new RackCoolingSystem(config);
        assertEquals(0, system.currentElectricalPowerWatts());
        system.enable("RC01");
        assertEquals(600, system.currentElectricalPowerWatts());
        assertThrows(UnsupportedOperationException.class, () -> new DatacenterFactory().create(definition));
    }
}

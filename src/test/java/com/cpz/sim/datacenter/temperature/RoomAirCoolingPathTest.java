package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.cooling.*;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.system.CoolingSystem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoomAirCoolingPathTest {

    @Test
    void removalIsTemperatureDependentAndCapacityLimited() {
        var path = new RoomAirCoolingPath(100.0, 18.0, 500.0);
        assertEquals(0.0, path.removedHeatWatts(17.0));
        assertEquals(0.0, path.removedHeatWatts(18.0));
        assertEquals(200.0, path.removedHeatWatts(20.0));
        assertEquals(500.0, path.removedHeatWatts(30.0));
        assertEquals(0.0, new RoomAirCoolingPath(0.0, 18.0, 500.0).removedHeatWatts(30.0));
        assertEquals(0.0, new RoomAirCoolingPath(100.0, 18.0, 0.0).removedHeatWatts(30.0));
    }

    @Test
    void rejectsInvalidThermalPathInputs() {
        for (double value : new double[]{-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new RoomAirCoolingPath(value, 18.0, 500.0));
            assertThrows(IllegalArgumentException.class, () -> new RoomAirCoolingPath(100.0, 18.0, value));
        }
        assertThrows(IllegalArgumentException.class, () -> new RoomAirCoolingPath(100.0, Double.NaN, 500.0));
        assertThrows(IllegalArgumentException.class, () -> new RoomAirCoolingPath(100.0, 18.0, 500.0).removedHeatWatts(Double.NaN));
    }

    @Test
    void partitionsSharedSupplyCapacityAndDoesNotAdvanceLegacyZoneState() {
        var locations = List.of(new ServerLocation("C01", new RackCode("R01"), "S01"),
                new ServerLocation("C02", new RackCode("R01"), "S01"));
        var influences = List.of(new CoolingZoneInfluence("Z1", 0.3), new CoolingZoneInfluence("Z2", 0.7));
        var config = new CoolingConfiguration(List.of(
                new CoolingZoneDefinition("Z1", Set.of(locations.get(0))),
                new CoolingZoneDefinition("Z2", Set.of(locations.get(1)))), List.of(
                new SupplyCoolingUnitDefinition("SUP", 1.0, 400.0, 1000.0, 18.0, influences, true),
                new ExhaustCoolingUnitDefinition("EXH", 1.0, 200.0, influences, true)), CoolingSystemOptions.defaults());
        var read = new CoolingSystem(config);
        var untouched = new CoolingSystem(config);
        for (int i = 0; i < 20; i++) {
            var paths = read.roomAirCoolingPaths();
            assertEquals(2, paths.size());
            assertEquals(1000.0, paths.stream().mapToDouble(RoomAirCoolingPath::capacityWatts).sum(), 1e-12);
            assertEquals(1000.0, paths.stream().mapToDouble(p -> p.removedHeatWatts(10000.0)).sum(), 1e-12);
            assertEquals(600.0, read.currentUnitSnapshots().stream().mapToDouble(u -> u.currentElectricalPowerWatts()).sum());
        }
        var input = new CoolingTickInput(1, 60.0, List.of(
                new ServerHeatLoad(locations.get(0), 500.0), new ServerHeatLoad(locations.get(1), 500.0)));
        assertEquals(untouched.tick(input), read.tick(input));
        read.disable("SUP");
        assertTrue(read.roomAirCoolingPaths().isEmpty());
    }

    @Test
    void rejectsUnpartitionedInfluenceWeightsInDirectRuntimeConfigurations() {
        var location = new ServerLocation("C01", new RackCode("R01"), "S01");
        var config = new CoolingConfiguration(List.of(new CoolingZoneDefinition("Z1", Set.of(location))),
                List.of(new SupplyCoolingUnitDefinition("SUP", 1.0, 1000.0, 18.0,
                        List.of(new CoolingZoneInfluence("Z1", 0.5)), true)), CoolingSystemOptions.defaults());
        assertThrows(IllegalArgumentException.class, () -> new CoolingSystem(config).roomAirCoolingPaths());
    }
}

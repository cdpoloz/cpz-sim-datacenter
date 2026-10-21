package com.cpz.sim.datacenter.temperature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SimpleRoomAirTemperatureModelTest {

    private final SimpleRoomAirTemperatureModel model = new SimpleRoomAirTemperatureModel();

    private static RoomAirTemperatureOptions options(double capacity, double conductance) {
        return new RoomAirTemperatureOptions(24.0, capacity, 20.0, conductance);
    }

    @Test
    void insulatedRoomConservesEnergyAndUsesSecondsAndWatts() {
        double capacity = 2_000_000.0;
        double next = model.nextTemperatureCelsius(24.0, 60.0,
                new RoomAirThermalInput(220_000.0, 20_000.0), options(capacity, 0.0));
        assertEquals(30.0, next, 1e-12);
        assertEquals((220_000.0 - 20_000.0) * 60.0, capacity * (next - 24.0), 1e-6);
    }

    @Test
    void balancedHeatTransfersKeepInsulatedRoomStable() {
        assertEquals(24.0, model.nextTemperatureCelsius(24.0, 60.0,
                new RoomAirThermalInput(220_000.0, 220_000.0), options(2_000_000.0, 0.0)));
    }

    @Test
    void coolingCanExceedServerHeatAndRemoveStoredEnergy() {
        double next = model.nextTemperatureCelsius(24.0, 60.0,
                new RoomAirThermalInput(100_000.0, 200_000.0), options(2_000_000.0, 0.0));
        assertEquals(21.0, next, 1e-12);
    }

    @Test
    void airCanTransferHeatIntoColderServers() {
        double next = model.nextTemperatureCelsius(24.0, 60.0,
                new RoomAirThermalInput(-100_000.0, 0.0), options(2_000_000.0, 0.0));
        assertEquals(21.0, next, 1e-12);
    }

    @Test
    void environmentExchangeMatchesAnalyticExponentialWithoutOvershoot() {
        double next = model.nextTemperatureCelsius(40.0, 60.0,
                new RoomAirThermalInput(0.0, 0.0), options(6000.0, 100.0));
        assertEquals(20.0 + 20.0 * Math.exp(-1.0), next, 1e-12);
        assertTrue(next > 20.0 && next < 40.0);
        assertEquals(20.0, model.nextTemperatureCelsius(40.0, 1_000_000.0,
                new RoomAirThermalInput(0.0, 0.0), options(6000.0, 100.0)), 1e-12);
    }

    @Test
    void warmerEnvironmentHeatsTheRoom() {
        var options = new RoomAirTemperatureOptions(10.0, 6000.0, 20.0, 100.0);
        double next = model.nextTemperatureCelsius(10.0, 60.0,
                new RoomAirThermalInput(0.0, 0.0), options);
        assertEquals(20.0 - 10.0 * Math.exp(-1.0), next, 1e-12);
    }

    @Test
    void constantHeatApproachesExpectedEquilibrium() {
        var input = new RoomAirThermalInput(2000.0, 500.0);
        var options = options(6000.0, 100.0);
        double next = model.nextTemperatureCelsius(24.0, 60.0, input, options);
        assertEquals(35.0 + (24.0 - 35.0) * Math.exp(-1.0), next, 1e-12);
        assertEquals(35.0, model.nextTemperatureCelsius(24.0, 1_000_000.0, input, options), 1e-12);
        assertEquals(35.0, model.nextTemperatureCelsius(35.0, 60.0, input, options), 1e-12);
    }

    @Test
    void oneLongStepEqualsManyShortStepsForConstantInputs() {
        var input = new RoomAirThermalInput(200_000.0, 100_000.0);
        for (double conductance : new double[]{0.0, 100.0, 10_000.0}) {
            var options = options(2_000_000.0, conductance);
            double longStep = model.nextTemperatureCelsius(24.0, 1260.0, input, options);
            double shortSteps = 24.0;
            for (int i = 0; i < 1260; i++)
                shortSteps = model.nextTemperatureCelsius(shortSteps, 1.0, input, options);
            assertEquals(longStep, shortSteps, 1e-9);
        }
    }

    @Test
    void nearZeroConductanceHasContinuousInsulatedLimit() {
        double next = model.nextTemperatureCelsius(24.0, 60.0,
                new RoomAirThermalInput(220_000.0, 20_000.0), options(2_000_000.0, 1e-300));
        assertEquals(30.0, next, 1e-12);
    }

    @Test
    void higherThermalCapacityReducesShortTermHeating() {
        var input = new RoomAirThermalInput(200_000.0, 0.0);
        double smaller = model.nextTemperatureCelsius(24.0, 60.0, input, options(2_000_000.0, 0.0));
        double larger = model.nextTemperatureCelsius(24.0, 60.0, input, options(4_000_000.0, 0.0));
        assertEquals((smaller - 24.0) / 2.0, larger - 24.0, 1e-12);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidStepDuration(double seconds) {
        assertThrows(IllegalArgumentException.class, () -> model.nextTemperatureCelsius(24.0, seconds,
                new RoomAirThermalInput(0.0, 0.0), options(6000.0, 100.0)));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteInputs(double value) {
        assertThrows(IllegalArgumentException.class, () -> new RoomAirThermalInput(value, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new RoomAirThermalInput(0.0, value));
        assertThrows(IllegalArgumentException.class, () -> model.nextTemperatureCelsius(value, 60.0,
                new RoomAirThermalInput(0.0, 0.0), options(6000.0, 100.0)));
    }

    @Test
    void rejectsNegativeRemovalNullsAndNonFiniteCalculations() {
        assertThrows(IllegalArgumentException.class, () -> new RoomAirThermalInput(0.0, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new RoomAirThermalInput(-Double.MAX_VALUE, Double.MAX_VALUE));
        assertThrows(NullPointerException.class,
                () -> model.nextTemperatureCelsius(24.0, 60.0, null, options(6000.0, 100.0)));
        assertThrows(NullPointerException.class,
                () -> model.nextTemperatureCelsius(24.0, 60.0, new RoomAirThermalInput(0.0, 0.0), null));
        assertThrows(IllegalArgumentException.class, () -> model.nextTemperatureCelsius(24.0, 60.0,
                new RoomAirThermalInput(Double.MAX_VALUE, 0.0), options(1.0, 0.0)));
    }
}

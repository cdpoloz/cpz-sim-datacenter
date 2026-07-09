package com.cpz.sim.datacenter.temperature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
/**
 * @author CPZ
 */
class SimpleServerTemperatureModelTest {


    @Test
    void increasesTemperatureWhenServerProducesHeat() {
        ServerTemperatureModel model = new SimpleServerTemperatureModel();
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                25.0,
                5000.0,
                8.0
        );
        double nextTemperature = model.nextTemperatureCelsius(
                25.0,
                400.0,
                60.0,
                options
        );
        assertTrue(nextTemperature > 25.0);
    }

    @Test
    void decreasesTemperatureWhenPowerIsZeroAndServerIsHotterThanAmbient() {
        ServerTemperatureModel model = new SimpleServerTemperatureModel();
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                25.0,
                5000.0,
                8.0
        );
        double nextTemperature = model.nextTemperatureCelsius(
                40.0,
                0.0,
                60.0,
                options
        );
        assertTrue(nextTemperature < 40.0);
        assertTrue(nextTemperature > 25.0);
    }

    @Test
    void keepsTemperatureStableWhenPowerIsZeroAndTemperatureEqualsAmbient() {
        ServerTemperatureModel model = new SimpleServerTemperatureModel();
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                25.0,
                5000.0,
                8.0
        );
        double nextTemperature = model.nextTemperatureCelsius(
                25.0,
                0.0,
                60.0,
                options
        );
        assertEquals(25.0, nextTemperature);
    }

    @Test
    void calculatesExpectedTemperatureDelta() {
        ServerTemperatureModel model = new SimpleServerTemperatureModel();
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                25.0,
                5000.0,
                8.0
        );
        double nextTemperature = model.nextTemperatureCelsius(
                25.0,
                400.0,
                60.0,
                options
        );
        // heatLoss = 8 * (25 - 25) = 0 W
        // netPower = 400 W
        // delta = 400 / 5000 * 60 = 4.8 °C
        assertEquals(29.8, nextTemperature, 0.000001);
    }
}

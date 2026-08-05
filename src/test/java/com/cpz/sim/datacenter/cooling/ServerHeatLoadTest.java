package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.ServerLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *  @author CPZ
 */
class ServerHeatLoadTest {

    private static final ServerLocation SERVER_LOCATION =
            new ServerLocation("C01", "R01", "S01");

    @Test
    void shouldCreateServerHeatLoad() {
        ServerHeatLoad heatLoad = new ServerHeatLoad(
                SERVER_LOCATION,
                450.0
        );

        assertEquals(SERVER_LOCATION, heatLoad.serverLocation());
        assertEquals(450.0, heatLoad.generatedHeatWatts());
    }

    @Test
    void shouldAllowZeroGeneratedHeat() {
        ServerHeatLoad heatLoad = new ServerHeatLoad(
                SERVER_LOCATION,
                0.0
        );

        assertEquals(0.0, heatLoad.generatedHeatWatts());
    }

    @Test
    void shouldRejectNullServerLocation() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ServerHeatLoad(null, 450.0)
        );

        assertEquals(
                "serverLocation must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNegativeGeneratedHeat() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServerHeatLoad(SERVER_LOCATION, -0.1)
        );

        assertEquals(
                "generatedHeatWatts must be greater than or equal to zero",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNaNGeneratedHeat() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServerHeatLoad(SERVER_LOCATION, Double.NaN)
        );

        assertEquals(
                "generatedHeatWatts must be finite",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectPositiveInfinityGeneratedHeat() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServerHeatLoad(
                        SERVER_LOCATION,
                        Double.POSITIVE_INFINITY
                )
        );

        assertEquals(
                "generatedHeatWatts must be finite",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNegativeInfinityGeneratedHeat() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServerHeatLoad(
                        SERVER_LOCATION,
                        Double.NEGATIVE_INFINITY
                )
        );

        assertEquals(
                "generatedHeatWatts must be finite",
                exception.getMessage()
        );
    }
}
package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class ConstantServerTemperatureReferenceProviderTest {

    private static final ServerConfig SERVER_CONFIG =
            new ServerConfig(
                    "model-01",
                    "Example",
                    "Server X",
                    100.0f,
                    300.0f
            );

    @Test
    void shouldRejectNonFiniteTemperature() {
        IllegalArgumentException nanException = assertThrows(IllegalArgumentException.class, () -> new ConstantServerTemperatureReferenceProvider(Double.NaN));
        assertEquals("temperatureCelsius must be finite", nanException.getMessage());
        IllegalArgumentException positiveInfinityException = assertThrows(
                IllegalArgumentException.class,
                () -> new ConstantServerTemperatureReferenceProvider(Double.POSITIVE_INFINITY)
        );
        assertEquals("temperatureCelsius must be finite", positiveInfinityException.getMessage());
        IllegalArgumentException negativeInfinityException = assertThrows(
                IllegalArgumentException.class,
                () -> new ConstantServerTemperatureReferenceProvider(Double.NEGATIVE_INFINITY)
        );
        assertEquals("temperatureCelsius must be finite", negativeInfinityException.getMessage());
    }

    @Test
    void shouldExposeConfiguredTemperature() {
        ConstantServerTemperatureReferenceProvider provider = new ConstantServerTemperatureReferenceProvider(24.5);
        assertEquals(24.5, provider.temperatureCelsius());
    }

    @Test
    void shouldReturnSameTemperatureForEveryServer() {
        ConstantServerTemperatureReferenceProvider provider = new ConstantServerTemperatureReferenceProvider(22.0);
        Server firstServer = createServer("U01");
        Server secondServer = createServer("U02");
        assertEquals(22.0, provider.temperatureCelsiusFor(firstServer));
        assertEquals(22.0, provider.temperatureCelsiusFor(secondServer));
    }

    @Test
    void shouldRejectNullServer() {
        ConstantServerTemperatureReferenceProvider provider = new ConstantServerTemperatureReferenceProvider(24.0);
        NullPointerException exception = assertThrows(NullPointerException.class, () -> provider.temperatureCelsiusFor(null));
        assertEquals("server must not be null", exception.getMessage());
    }

    private static Server createServer(String slot) {
        return new Server(
                new ServerLocation(
                        "A01",
                        "RACK-A01-R01",
                        slot
                ),
                SERVER_CONFIG,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );
    }
}
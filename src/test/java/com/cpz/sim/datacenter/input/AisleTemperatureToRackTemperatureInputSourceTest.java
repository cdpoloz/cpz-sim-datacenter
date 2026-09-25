package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AisleTemperatureToRackTemperatureInputSourceTest {

    private static final RackLocation FIRST_RACK_LOCATION = new RackLocation("C02", "R01");
    private static final RackLocation SECOND_RACK_LOCATION = new RackLocation("C03", "R01");

    @Test
    void shouldUseResolvedAisleTemperatureForRack() {
        AisleTemperatureToRackTemperatureInputSource source =
                new AisleTemperatureToRackTemperatureInputSource(
                        new MapAisleTemperatureInputSource(Map.of("HA01", 54.0), 25.0),
                        rackLocation -> "HA01"
                );

        assertEquals(54.0, source.temperatureCelsius(FIRST_RACK_LOCATION, tick()), 0.0001);
    }

    @Test
    void shouldAllowDifferentRacksToResolveToDifferentAisles() {
        AisleTemperatureToRackTemperatureInputSource source =
                new AisleTemperatureToRackTemperatureInputSource(
                        new MapAisleTemperatureInputSource(Map.of("HA01", 50.0, "HA02", 60.0), 25.0),
                        rackLocation -> rackLocation.column().equals("C02") ? "HA01" : "HA02"
                );

        assertEquals(50.0, source.temperatureCelsius(FIRST_RACK_LOCATION, tick()), 0.0001);
        assertEquals(60.0, source.temperatureCelsius(SECOND_RACK_LOCATION, tick()), 0.0001);
    }

    @Test
    void shouldUseStandardHotAisleLayoutForSharedColumns() {
        StandardHotAisleCodeResolver resolver = new StandardHotAisleCodeResolver();

        assertEquals("HA01", resolver.apply(new RackLocation("C01", "R01")));
        assertEquals("HA02", resolver.apply(new RackLocation("C02", "R01")));
        assertEquals("HA02", resolver.apply(new RackLocation("C03", "R01")));
        assertEquals("HA03", resolver.apply(new RackLocation("C04", "R01")));
        assertEquals("HA03", resolver.apply(new RackLocation("C05", "R01")));
        assertEquals("HA04", resolver.apply(new RackLocation("C06", "R01")));
        assertEquals("HA04", resolver.apply(new RackLocation("C07", "R01")));
        assertEquals("HA05", resolver.apply(new RackLocation("C08", "R01")));
    }

    @Test
    void shouldReturnSameTemperatureForColumnsSharingStandardHotAisle() {
        AisleTemperatureToRackTemperatureInputSource source =
                new AisleTemperatureToRackTemperatureInputSource(
                        new MapAisleTemperatureInputSource(
                                Map.of("HA01", 45.0, "HA02", 52.0, "HA03", 58.0, "HA04", 61.0, "HA05", 49.0),
                                25.0
                        ),
                        new StandardHotAisleCodeResolver()
                );

        assertEquals(
                source.temperatureCelsius(new RackLocation("C02", "R01"), tick()),
                source.temperatureCelsius(new RackLocation("C03", "R01"), tick()),
                0.0001
        );
        assertEquals(
                source.temperatureCelsius(new RackLocation("C04", "R01"), tick()),
                source.temperatureCelsius(new RackLocation("C05", "R01"), tick()),
                0.0001
        );
        assertEquals(
                source.temperatureCelsius(new RackLocation("C06", "R01"), tick()),
                source.temperatureCelsius(new RackLocation("C07", "R01"), tick()),
                0.0001
        );
        assertNotEquals(
                source.temperatureCelsius(new RackLocation("C01", "R01"), tick()),
                source.temperatureCelsius(new RackLocation("C08", "R01"), tick())
        );
    }

    @Test
    void shouldRejectInvalidResolvedAisleCode() {
        AisleTemperatureToRackTemperatureInputSource nullAisleSource =
                new AisleTemperatureToRackTemperatureInputSource(
                        new MapAisleTemperatureInputSource(Map.of(), 25.0),
                        rackLocation -> null
                );
        AisleTemperatureToRackTemperatureInputSource blankAisleSource =
                new AisleTemperatureToRackTemperatureInputSource(
                        new MapAisleTemperatureInputSource(Map.of(), 25.0),
                        rackLocation -> " "
                );

        assertThrows(NullPointerException.class, () -> nullAisleSource.temperatureCelsius(FIRST_RACK_LOCATION, tick()));
        assertThrows(
                IllegalArgumentException.class,
                () -> blankAisleSource.temperatureCelsius(FIRST_RACK_LOCATION, tick())
        );
    }

    @Test
    void shouldRejectInvalidConstructorArguments() {
        MapAisleTemperatureInputSource aisleSource = new MapAisleTemperatureInputSource(Map.of(), 25.0);

        assertThrows(
                NullPointerException.class,
                () -> new AisleTemperatureToRackTemperatureInputSource(null, RackLocation::column)
        );
        assertThrows(
                NullPointerException.class,
                () -> new AisleTemperatureToRackTemperatureInputSource(aisleSource, null)
        );
    }

    private static SimulationTick tick() {
        return new SimulationTick(1L, Duration.ofMinutes(1), Duration.ofMinutes(1));
    }
}

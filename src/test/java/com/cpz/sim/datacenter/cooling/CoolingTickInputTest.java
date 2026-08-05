package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.ServerLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class CoolingTickInputTest {

    private static final ServerLocation SERVER_LOCATION =
            new ServerLocation("C01", "R01", "S01");

    private static final ServerHeatLoad SERVER_HEAT_LOAD =
            new ServerHeatLoad(SERVER_LOCATION, 450.0);

    @Test
    void shouldCreateCoolingTickInput() {
        CoolingTickInput input = new CoolingTickInput(
                12L,
                List.of(SERVER_HEAT_LOAD)
        );

        assertEquals(12L, input.tickIndex());
        assertEquals(
                List.of(SERVER_HEAT_LOAD),
                input.serverHeatLoads()
        );
    }

    @Test
    void shouldAllowZeroTickIndex() {
        CoolingTickInput input = new CoolingTickInput(
                0L,
                List.of(SERVER_HEAT_LOAD)
        );

        assertEquals(0L, input.tickIndex());
    }

    @Test
    void shouldAllowEmptyServerHeatLoads() {
        CoolingTickInput input = new CoolingTickInput(
                12L,
                List.of()
        );

        assertEquals(List.of(), input.serverHeatLoads());
    }

    @Test
    void shouldRejectNegativeTickIndex() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new CoolingTickInput(
                        -1L,
                        List.of(SERVER_HEAT_LOAD)
                )
        );

        assertEquals(
                "tickIndex must be greater than or equal to zero",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullServerHeatLoads() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new CoolingTickInput(12L, null)
        );

        assertEquals(
                "serverHeatLoads must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullElementInServerHeatLoads() {
        List<ServerHeatLoad> heatLoads = new ArrayList<>();
        heatLoads.add(SERVER_HEAT_LOAD);
        heatLoads.add(null);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new CoolingTickInput(12L, heatLoads)
        );

        assertEquals(
                "serverHeatLoads must not contain null elements",
                exception.getMessage()
        );
    }

    @Test
    void shouldCreateDefensiveCopyOfServerHeatLoads() {
        List<ServerHeatLoad> originalList = new ArrayList<>();
        originalList.add(SERVER_HEAT_LOAD);

        CoolingTickInput input = new CoolingTickInput(
                12L,
                originalList
        );

        originalList.clear();

        assertEquals(
                List.of(SERVER_HEAT_LOAD),
                input.serverHeatLoads()
        );
    }

    @Test
    void shouldExposeUnmodifiableServerHeatLoads() {
        CoolingTickInput input = new CoolingTickInput(
                12L,
                List.of(SERVER_HEAT_LOAD)
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> input.serverHeatLoads().add(
                        new ServerHeatLoad(
                                new ServerLocation("C01", "R01", "S02"),
                                500.0
                        )
                )
        );
    }
}
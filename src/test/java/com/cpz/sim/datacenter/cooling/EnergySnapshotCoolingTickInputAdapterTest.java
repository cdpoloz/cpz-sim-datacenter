package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerEnergySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class EnergySnapshotCoolingTickInputAdapterTest {

    private final EnergySnapshotCoolingTickInputAdapter adapter =
            new EnergySnapshotCoolingTickInputAdapter();

    @Test
    void shouldAdaptEnergySnapshotToCoolingTickInput() {
        ServerEnergySnapshot firstServer = serverSnapshot(
                "SERVER-01",
                "C01",
                "R01",
                "S01",
                HardwareStatus.OK,
                0.75,
                450.0f
        );

        ServerEnergySnapshot secondServer = serverSnapshot(
                "SERVER-02",
                "C02",
                "R02",
                "S03",
                HardwareStatus.ALERT,
                0.90,
                520.0f
        );

        EnergyConsumptionSnapshot energySnapshot =
                new EnergyConsumptionSnapshot(
                        12L,
                        720.0,
                        970.0,
                        194.0,
                        List.of(firstServer, secondServer)
                );

        CoolingTickInput result = adapter.adapt(energySnapshot);

        assertEquals(12L, result.tickIndex());
        assertEquals(
                List.of(
                        new ServerHeatLoad(
                                new ServerLocation("C01", "R01", "S01"),
                                450.0
                        ),
                        new ServerHeatLoad(
                                new ServerLocation("C02", "R02", "S03"),
                                520.0
                        )
                ),
                result.serverHeatLoads()
        );
    }

    @Test
    void shouldPreserveZeroPowerAsZeroHeatLoad() {
        ServerEnergySnapshot offlineServer = serverSnapshot(
                "SERVER-01",
                "C01",
                "R01",
                "S01",
                HardwareStatus.OFFLINE,
                0.0,
                0.0f
        );

        EnergyConsumptionSnapshot energySnapshot =
                new EnergyConsumptionSnapshot(
                        0L,
                        0.0,
                        0.0,
                        0.0,
                        List.of(offlineServer)
                );

        CoolingTickInput result = adapter.adapt(energySnapshot);

        assertEquals(
                List.of(
                        new ServerHeatLoad(
                                new ServerLocation("C01", "R01", "S01"),
                                0.0
                        )
                ),
                result.serverHeatLoads()
        );
    }

    @Test
    void shouldAdaptSnapshotWithoutServers() {
        EnergyConsumptionSnapshot energySnapshot =
                new EnergyConsumptionSnapshot(
                        5L,
                        300.0,
                        0.0,
                        0.0,
                        List.of()
                );

        CoolingTickInput result = adapter.adapt(energySnapshot);

        assertEquals(5L, result.tickIndex());
        assertEquals(List.of(), result.serverHeatLoads());
    }

    @Test
    void shouldRejectNullEnergySnapshot() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> adapter.adapt(null)
        );

        assertEquals(
                "energySnapshot must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDuplicateServerLocations() {
        ServerEnergySnapshot firstServer = serverSnapshot(
                "SERVER-01",
                "C01",
                "R01",
                "S01",
                HardwareStatus.OK,
                0.50,
                350.0f
        );

        ServerEnergySnapshot duplicateLocation = serverSnapshot(
                "SERVER-02",
                "C01",
                "R01",
                "S01",
                HardwareStatus.ALERT,
                0.80,
                480.0f
        );

        EnergyConsumptionSnapshot energySnapshot =
                new EnergyConsumptionSnapshot(
                        8L,
                        480.0,
                        830.0,
                        110.0,
                        List.of(firstServer, duplicateLocation)
                );

        ServerLocation duplicateServerLocation =
                new ServerLocation("C01", "R01", "S01");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.adapt(energySnapshot)
        );

        assertEquals(
                "energySnapshot must not contain duplicate server locations: "
                        + duplicateServerLocation,
                exception.getMessage()
        );
    }

    private static ServerEnergySnapshot serverSnapshot(
            String serverCode,
            String column,
            String rackCode,
            String slot,
            HardwareStatus status,
            double utilization,
            float currentPowerWatts
    ) {
        return new ServerEnergySnapshot(
                serverCode,
                column,
                new RackCode(rackCode),
                slot,
                status,
                utilization,
                200.0f,
                600.0f,
                currentPowerWatts
        );
    }
}
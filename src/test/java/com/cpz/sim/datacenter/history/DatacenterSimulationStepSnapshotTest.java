package com.cpz.sim.datacenter.history;

import com.cpz.sim.datacenter.cooling.CoolingUnitType;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.snapshot.ColumnOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingUnitSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshot;
import com.cpz.sim.datacenter.snapshot.HealthSnapshot;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerEnergySnapshot;
import com.cpz.sim.datacenter.snapshot.ServerHealthSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerTemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatacenterSimulationStepSnapshotTest {

    private static final RackCode RACK_CODE = new RackCode("R01");
    private static final RackLocation RACK_LOCATION = new RackLocation("C01", RACK_CODE);

    @Test
    void shouldCreateCompleteStepSnapshot() {
        DatacenterSimulationStepSnapshot snapshot =
                new DatacenterSimulationStepSnapshot(
                        energySnapshot(10L, 600.0),
                        temperatureSnapshot(10L, 600.0),
                        healthSnapshot(10L, 600.0),
                        Optional.of(coolingSnapshot(10L)),
                        operationalSnapshot(10L, 600.0)
                );

        assertEquals(10L, snapshot.tickIndex());
        assertEquals(600.0, snapshot.elapsedSeconds());
        assertTrue(snapshot.hasCoolingSnapshot());
    }

    @Test
    void shouldAllowStepSnapshotWithoutCoolingData() {
        DatacenterSimulationStepSnapshot snapshot =
                new DatacenterSimulationStepSnapshot(
                        energySnapshot(10L, 600.0),
                        temperatureSnapshot(10L, 600.0),
                        healthSnapshot(10L, 600.0),
                        Optional.empty(),
                        operationalSnapshot(10L, 600.0)
                );

        assertFalse(snapshot.hasCoolingSnapshot());
    }

    @Test
    void shouldRejectMismatchedTickIndex() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new DatacenterSimulationStepSnapshot(
                                energySnapshot(10L, 600.0),
                                temperatureSnapshot(11L, 600.0),
                                healthSnapshot(10L, 600.0),
                                Optional.empty(),
                                operationalSnapshot(10L, 600.0)
                        )
                );

        assertEquals("All step snapshots must have the same tickIndex", exception.getMessage());
    }

    @Test
    void shouldRejectMismatchedElapsedSeconds() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new DatacenterSimulationStepSnapshot(
                                energySnapshot(10L, 600.0),
                                temperatureSnapshot(10L, 601.0),
                                healthSnapshot(10L, 600.0),
                                Optional.empty(),
                                operationalSnapshot(10L, 600.0)
                        )
                );

        assertEquals("All elapsed step snapshots must have the same elapsedSeconds", exception.getMessage());
    }

    @Test
    void shouldRejectMismatchedCoolingTickIndex() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new DatacenterSimulationStepSnapshot(
                                energySnapshot(10L, 600.0),
                                temperatureSnapshot(10L, 600.0),
                                healthSnapshot(10L, 600.0),
                                Optional.of(coolingSnapshot(11L)),
                                operationalSnapshot(10L, 600.0)
                        )
                );

        assertEquals("Cooling snapshot must have the same tickIndex", exception.getMessage());
    }

    static DatacenterSimulationStepSnapshot stepSnapshot(long tickIndex) {
        double elapsedSeconds = tickIndex * 60.0;
        return new DatacenterSimulationStepSnapshot(
                energySnapshot(tickIndex, elapsedSeconds),
                temperatureSnapshot(tickIndex, elapsedSeconds),
                healthSnapshot(tickIndex, elapsedSeconds),
                Optional.of(coolingSnapshot(tickIndex)),
                operationalSnapshot(tickIndex, elapsedSeconds)
        );
    }

    private static EnergyConsumptionSnapshot energySnapshot(long tickIndex, double elapsedSeconds) {
        return new EnergyConsumptionSnapshot(
                tickIndex,
                elapsedSeconds,
                300.0,
                50.0,
                List.of(
                        new ServerEnergySnapshot(
                                "srv-01",
                                "C01",
                                RACK_CODE,
                                "S01",
                                HardwareStatus.OK,
                                0.50,
                                100.0f,
                                500.0f,
                                300.0f
                        )
                )
        );
    }

    private static TemperatureSnapshot temperatureSnapshot(long tickIndex, double elapsedSeconds) {
        return new TemperatureSnapshot(
                tickIndex,
                elapsedSeconds,
                24.0,
                List.of(
                        new ServerTemperatureSnapshot(
                                "srv-01",
                                "C01",
                                RACK_CODE,
                                "S01",
                                HardwareStatus.OK,
                                0.50,
                                300.0,
                                42.0
                        )
                )
        );
    }

    private static HealthSnapshot healthSnapshot(long tickIndex, double elapsedSeconds) {
        return new HealthSnapshot(
                tickIndex,
                elapsedSeconds,
                List.of(
                        new ServerHealthSnapshot(
                                "srv-01",
                                "C01",
                                RACK_CODE,
                                "S01",
                                HardwareStatus.OK,
                                Set.of(ServerAlertReason.HIGH_TEMPERATURE),
                                0.50,
                                42.0
                        )
                )
        );
    }

    private static CoolingSnapshot coolingSnapshot(long tickIndex) {
        return new CoolingSnapshot(
                tickIndex,
                List.of(new CoolingUnitSnapshot("SUPPLY-01", CoolingUnitType.SUPPLY, true, 2.0, 1000.0)),
                List.of(new CoolingZoneSnapshot("ZONE-01", 300.0, 1000.0, 300.0, 0.0, 2.0, 2.0, 18.0, 28.0, 0.05))
        );
    }

    private static DatacenterOperationalSnapshot operationalSnapshot(long tickIndex, double elapsedSeconds) {
        RackOperationalSnapshot rackSnapshot =
                new RackOperationalSnapshot(
                        RACK_LOCATION,
                        1,
                        1,
                        100.0,
                        500.0,
                        300.0,
                        42.0,
                        42.0,
                        0.50
                );
        ColumnOperationalSnapshot columnSnapshot =
                new ColumnOperationalSnapshot(
                        "C01",
                        1,
                        1,
                        100.0,
                        500.0,
                        300.0,
                        42.0,
                        0.50
                );
        return new DatacenterOperationalSnapshot(
                tickIndex,
                elapsedSeconds,
                Map.of(RACK_LOCATION, rackSnapshot),
                Map.of("C01", columnSnapshot),
                24.0,
                Optional.of(RACK_LOCATION),
                42.0,
                0.50,
                100.0,
                500.0,
                300.0,
                Double.NaN,
                Double.NaN,
                Double.NaN
        );
    }
}

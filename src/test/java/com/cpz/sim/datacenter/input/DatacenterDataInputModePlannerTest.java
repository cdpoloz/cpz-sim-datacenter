package com.cpz.sim.datacenter.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatacenterDataInputModePlannerTest {

    @Test
    void shouldDescribeUtilizationDrivenPlan() {
        DatacenterDataInputModePlan plan =
                DatacenterDataInputModePlanner.planFor(DatacenterDataInputMode.UTILIZATION_DRIVEN);

        assertEquals(DatacenterDataInputMode.UTILIZATION_DRIVEN, plan.mode());
        assertTrue(plan.updatesUtilization());
        assertTrue(plan.updatesPowerFromUtilization());
        assertFalse(plan.acceptsPowerInput());
        assertTrue(plan.updatesTemperatureFromPower());
        assertFalse(plan.acceptsTemperatureInput());
    }

    @Test
    void shouldDescribePowerDrivenPlan() {
        DatacenterDataInputModePlan plan =
                DatacenterDataInputModePlanner.planFor(DatacenterDataInputMode.POWER_DRIVEN);

        assertEquals(DatacenterDataInputMode.POWER_DRIVEN, plan.mode());
        assertFalse(plan.updatesUtilization());
        assertFalse(plan.updatesPowerFromUtilization());
        assertTrue(plan.acceptsPowerInput());
        assertTrue(plan.updatesTemperatureFromPower());
        assertFalse(plan.acceptsTemperatureInput());
    }

    @Test
    void shouldDescribeTemperatureDrivenPlan() {
        DatacenterDataInputModePlan plan =
                DatacenterDataInputModePlanner.planFor(DatacenterDataInputMode.TEMPERATURE_DRIVEN);

        assertEquals(DatacenterDataInputMode.TEMPERATURE_DRIVEN, plan.mode());
        assertFalse(plan.updatesUtilization());
        assertFalse(plan.updatesPowerFromUtilization());
        assertFalse(plan.acceptsPowerInput());
        assertFalse(plan.updatesTemperatureFromPower());
        assertTrue(plan.acceptsTemperatureInput());
    }

    @Test
    void shouldRejectNullMode() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> DatacenterDataInputModePlanner.planFor(null)
        );

        assertEquals("mode must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectContradictoryPowerPlan() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterDataInputModePlan(
                        DatacenterDataInputMode.UTILIZATION_DRIVEN,
                        true,
                        true,
                        true,
                        true,
                        false
                )
        );

        assertEquals(
                "Power cannot be both derived from utilization and supplied directly",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectContradictoryTemperaturePlan() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatacenterDataInputModePlan(
                        DatacenterDataInputMode.TEMPERATURE_DRIVEN,
                        false,
                        false,
                        false,
                        true,
                        true
                )
        );

        assertEquals(
                "Temperature cannot be both derived from power and supplied directly",
                exception.getMessage()
        );
    }
}

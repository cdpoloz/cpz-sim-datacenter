package com.cpz.sim.datacenter.config.validation;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.config.definition.TemperatureSystemOptionsDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class DatacenterConfigValidatorTest {

    private final DatacenterConfigValidator validator = new DatacenterConfigValidator();

    private static ServerModelDefinition model() {
        return new ServerModelDefinition(
                "SRV-DEMO-001",
                "CPZ",
                "Demo Server",
                100.0f,
                300.0f
        );
    }

    private static RackDefinition rack(String code) {
        return new RackDefinition(code, "A01", "R01", 42);
    }

    private static RackDefinition rackWithSlots(String code, List<String> slots) {
        return new RackDefinition(code, "A01", "R01", slots);
    }

    private static ServerDefinition server(String rackCode, String slot, float workloadFactor) {
        return new ServerDefinition(
                rackCode,
                slot,
                "SRV-DEMO-001",
                "OK",
                workloadFactor
        );
    }

    private static ServerDefinition server(String column, String rackCode, String slot, float workloadFactor) {
        return new ServerDefinition(
                column,
                rackCode,
                slot,
                "SRV-DEMO-001",
                "OK",
                workloadFactor
        );
    }

    private static DatacenterDefinition definition(
            DatacenterLayoutDefinition layout,
            List<ServerDefinition> servers
    ) {
        return definition(layout, servers, null);
    }

    private static DatacenterDefinition definition(
            DatacenterLayoutDefinition layout,
            List<ServerDefinition> servers,
            TemperatureSystemOptionsDefinition temperature
    ) {
        return new DatacenterDefinition(
                "Demo Datacenter",
                layout,
                List.of(model()),
                servers,
                temperature
        );
    }

    private static DatacenterLayoutDefinition layout(List<RackDefinition> racks) {
        return new DatacenterLayoutDefinition(racks);
    }

    @Test
    void shouldRejectNullDefinition() {
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(null));
    }

    @Test
    void shouldRejectNullLayout() {
        DatacenterDefinition definition = definition(null, List.of(server("RACK-A01-R01", "U01", 1.0f)));
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectNullRacksList() {
        DatacenterDefinition definition = definition(layout(null), List.of());
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectNullRackElement() {
        List<RackDefinition> racks = new ArrayList<>();
        racks.add(rack("RACK-A01-R01"));
        racks.add(null);
        DatacenterDefinition definition = definition(layout(racks), List.of());
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectDuplicatedRackLocation() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"), rack("RACK-A01-R01"))),
                List.of()
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldAllowSameRackCodeInDifferentColumns() {
        DatacenterDefinition definition = definition(
                layout(List.of(
                        new RackDefinition("R01", "C01", "R01", List.of("S01")),
                        new RackDefinition("R01", "C02", "R01", List.of("S01"))
                )),
                List.of(
                        server("C01", "R01", "S01", 1.0f),
                        server("C02", "R01", "S01", 1.0f)
                )
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectLegacyServerWithoutColumnWhenRackCodeIsAmbiguous() {
        DatacenterDefinition definition = definition(
                layout(List.of(
                        new RackDefinition("R01", "C01", "R01", List.of("S01")),
                        new RackDefinition("R01", "C02", "R01", List.of("S01"))
                )),
                List.of(server("R01", "S01", 1.0f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldAcceptLegacyServerWithoutColumnWhenRackCodeIsUnique() {
        DatacenterDefinition definition = definition(
                layout(List.of(new RackDefinition("R01", "C01", "R01", List.of("S01")))),
                List.of(server("R01", "S01", 1.0f))
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectServerWithUnknownColumn() {
        DatacenterDefinition definition = definition(
                layout(List.of(new RackDefinition("R01", "C01", "R01", List.of("S01")))),
                List.of(server("C03", "R01", "S01", 1.0f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectServerWithUnknownRackInKnownColumn() {
        DatacenterDefinition definition = definition(
                layout(List.of(new RackDefinition("R01", "C01", "R01", List.of("S01")))),
                List.of(server("C01", "R02", "S01", 1.0f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectBlankServerColumnWhenPresent() {
        DatacenterDefinition definition = definition(
                layout(List.of(new RackDefinition("R01", "C01", "R01", List.of("S01")))),
                List.of(server(" ", "R01", "S01", 1.0f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectServerWithUnknownRackCode() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("UNKNOWN_RACK", "U01", 1.0f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectSlotOutsideLegacyRackRange() {
        DatacenterDefinition definition = definition(
                layout(List.of(new RackDefinition("RACK-A01-R01", "A01", "R01", 2))),
                List.of(server("RACK-A01-R01", "U03", 1.0f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldAcceptExplicitSlotsWithSxxCodes() {
        DatacenterDefinition definition = definition(
                layout(List.of(rackWithSlots(
                        "RACK-A01-R01",
                        List.of("S01", "S02", "S03", "S04", "S05", "S06", "S07", "S08", "S09", "S10", "S11", "S12")
                ))),
                List.of(server("RACK-A01-R01", "S12", 1.0f))
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldAcceptArbitraryExplicitSlotCodes() {
        DatacenterDefinition definition = definition(
                layout(List.of(rackWithSlots("RACK-A01-R01", List.of("GPU-A", "NETWORK", "SPARE")))),
                List.of(
                        server("RACK-A01-R01", "GPU-A", 1.0f),
                        server("RACK-A01-R01", "NETWORK", 1.0f),
                        server("RACK-A01-R01", "SPARE", 1.0f)
                )
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldAcceptExplicitRackWithoutInstalledServers() {
        DatacenterDefinition definition = definition(
                layout(List.of(rackWithSlots("RACK-A01-R01", List.of("S01", "S02")))),
                List.of()
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectEmptyExplicitSlotsList() {
        DatacenterDefinition definition = definition(
                layout(List.of(rackWithSlots("RACK-A01-R01", List.of()))),
                List.of()
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectNullExplicitSlotCode() {
        List<String> slots = new ArrayList<>();
        slots.add("S01");
        slots.add(null);
        DatacenterDefinition definition = definition(
                layout(List.of(rackWithSlots("RACK-A01-R01", slots))),
                List.of()
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectBlankExplicitSlotCode() {
        DatacenterDefinition definition = definition(
                layout(List.of(rackWithSlots("RACK-A01-R01", List.of("S01", " ")))),
                List.of()
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectDuplicatedExplicitSlotCodeInsideRack() {
        DatacenterDefinition definition = definition(
                layout(List.of(rackWithSlots("RACK-A01-R01", List.of("S01", "S03", "S03")))),
                List.of()
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldAllowSameSlotCodeInDifferentRacks() {
        DatacenterDefinition definition = definition(
                layout(List.of(
                        rackWithSlots("RACK-A01-R01", List.of("S01")),
                        rackWithSlots("RACK-A01-R02", List.of("S01"))
                )),
                List.of(
                        server("RACK-A01-R01", "S01", 1.0f),
                        server("RACK-A01-R02", "S01", 1.0f)
                )
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectRackWithSlotCountAndSlots() {
        DatacenterDefinition definition = definition(
                layout(List.of(new RackDefinition("RACK-A01-R01", "A01", "R01", 42, List.of("S01")))),
                List.of()
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectRackWithoutSlotCountOrSlots() {
        DatacenterDefinition definition = definition(
                layout(List.of(new RackDefinition("RACK-A01-R01", "A01", "R01", null, null, false, false))),
                List.of()
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectNonPositiveSlotCount() {
        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(definition(
                        layout(List.of(new RackDefinition("RACK-A01-R01", "A01", "R01", 0))),
                        List.of()
                ))
        );
        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(definition(
                        layout(List.of(new RackDefinition("RACK-A01-R01", "A01", "R01", -1))),
                        List.of()
                ))
        );
    }

    @Test
    void shouldRejectServerReferencingUndeclaredExplicitSlot() {
        DatacenterDefinition definition = definition(
                layout(List.of(rackWithSlots("RACK-A01-R01", List.of("S01", "S02")))),
                List.of(server("RACK-A01-R01", "S03", 1.0f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldCompareExplicitSlotCodesCaseSensitively() {
        DatacenterDefinition definition = definition(
                layout(List.of(rackWithSlots("RACK-A01-R01", List.of("GPU-A")))),
                List.of(server("RACK-A01-R01", "gpu-a", 1.0f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldKeepLegacySlotCountCompatibility() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "U42", 1.0f))
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectTwoServersInSameRackSlot() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(
                        server("RACK-A01-R01", "U01", 1.0f),
                        server("RACK-A01-R01", "U01", 1.0f)
                )
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldAllowSameRackSlotTextInDifferentColumns() {
        DatacenterDefinition definition = definition(
                layout(List.of(
                        new RackDefinition("R01", "C01", "R01", List.of("S01")),
                        new RackDefinition("R01", "C02", "R01", List.of("S01"))
                )),
                List.of(
                        server("C01", "R01", "S01", 1.0f),
                        server("C02", "R01", "S01", 1.0f)
                )
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectDuplicateServerLocationWithColumn() {
        DatacenterDefinition definition = definition(
                layout(List.of(new RackDefinition("R01", "C01", "R01", List.of("S01")))),
                List.of(
                        server("C01", "R01", "S01", 1.0f),
                        server("C01", "R01", "S01", 1.0f)
                )
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldAcceptEmptyRack() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"), new RackDefinition("RACK-A01-R02", "A01", "R02", 42))),
                List.of(server("RACK-A01-R01", "U01", 1.0f))
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldAcceptWorkloadFactorGreaterThanOne() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "U01", 1.5f))
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectNegativeWorkloadFactor() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "U01", -0.1f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectNonFiniteWorkloadFactor() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "U01", Float.NaN))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectServerModelWithMaxPowerEqualToIdlePower() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(new ServerModelDefinition("SRV-DEMO-001", "CPZ", "Demo Server", 100.0f, 100.0f)),
                List.of(),
                null
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldAcceptMissingTemperatureBlock() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "U01", 1.0f)),
                null
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldAcceptValidTemperatureBlock() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "U01", 1.0f)),
                new TemperatureSystemOptionsDefinition(24.0, 30.0, 5000.0, 8.0)
        );
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectNonFiniteAmbientTemperature() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "U01", 1.0f)),
                new TemperatureSystemOptionsDefinition(Double.NaN, 30.0, 5000.0, 8.0)
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectNonFiniteDefaultInitialTemperature() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "U01", 1.0f)),
                new TemperatureSystemOptionsDefinition(24.0, Double.POSITIVE_INFINITY, 5000.0, 8.0)
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectZeroThermalCapacityInTemperatureBlock() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "U01", 1.0f)),
                new TemperatureSystemOptionsDefinition(24.0, 30.0, 0.0, 8.0)
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectNegativeHeatDissipationInTemperatureBlock() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "U01", 1.0f)),
                new TemperatureSystemOptionsDefinition(24.0, 30.0, 5000.0, -1.0)
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }
}

package com.cpz.sim.datacenter.config.validation;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.config.definition.TemperatureSystemOptionsDefinition;
import com.cpz.sim.datacenter.config.definition.CoolingConfigDefinition;
import com.cpz.sim.datacenter.config.definition.CoolingSystemOptionsDefinition;
import com.cpz.sim.datacenter.config.definition.CoolingZoneConfigDefinition;
import com.cpz.sim.datacenter.config.definition.CoolingZoneInfluenceConfigDefinition;
import com.cpz.sim.datacenter.config.definition.ExhaustCoolingUnitConfigDefinition;
import com.cpz.sim.datacenter.config.definition.SupplyCoolingUnitConfigDefinition;
import com.cpz.sim.datacenter.model.ServerRole;
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
                ServerRole.GENERAL_PURPOSE,
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
                ServerRole.GENERAL_PURPOSE,
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
    void shouldAcceptValidServerModelThermalProperties() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(new ServerModelDefinition(
                        "SRV-DEMO-001",
                        "CPZ",
                        "Demo Server",
                        100.0f,
                        300.0f,
                        7500.0,
                        12.5
                )),
                List.of()
        );

        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectIncompleteServerModelThermalProperties() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(new ServerModelDefinition(
                        "SRV-DEMO-001",
                        "CPZ",
                        "Demo Server",
                        100.0f,
                        300.0f,
                        7500.0,
                        null
                )),
                List.of()
        );

        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectNonPositiveServerModelThermalProperties() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(new ServerModelDefinition(
                        "SRV-DEMO-001",
                        "CPZ",
                        "Demo Server",
                        100.0f,
                        300.0f,
                        0.0,
                        -1.0
                )),
                List.of()
        );

        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectNonFiniteServerModelThermalProperties() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(new ServerModelDefinition(
                        "SRV-DEMO-001",
                        "CPZ",
                        "Demo Server",
                        100.0f,
                        300.0f,
                        Double.NaN,
                        Double.POSITIVE_INFINITY
                )),
                List.of()
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

    private static CoolingZoneInfluenceConfigDefinition coolingInfluence() {
        return new CoolingZoneInfluenceConfigDefinition(
                "ZONE-A01-R01",
                1.0
        );
    }

    private static CoolingZoneConfigDefinition coolingZone() {
        return new CoolingZoneConfigDefinition(
                "ZONE-A01-R01",
                List.of("A01"),
                List.of("RACK-A01-R01")
        );
    }

    private static SupplyCoolingUnitConfigDefinition supplyUnit() {
        return new SupplyCoolingUnitConfigDefinition(
                "SUPPLY-01",
                8.0,
                100_000.0,
                18.0,
                List.of(coolingInfluence()),
                false
        );
    }

    private static ExhaustCoolingUnitConfigDefinition exhaustUnit() {
        return new ExhaustCoolingUnitConfigDefinition(
                "EXHAUST-01",
                8.0,
                List.of(coolingInfluence()),
                false
        );
    }

    private static CoolingSystemOptionsDefinition coolingOptions() {
        return new CoolingSystemOptionsDefinition(
                1.204,
                1005.0,
                24.0,
                0.95
        );
    }

    private static CoolingConfigDefinition cooling() {
        return new CoolingConfigDefinition(
                List.of(coolingZone()),
                List.of(supplyUnit()),
                List.of(exhaustUnit()),
                coolingOptions()
        );
    }

    private static DatacenterDefinition definitionWithCooling(
            CoolingConfigDefinition cooling
    ) {
        return new DatacenterDefinition(
                "Demo Datacenter",
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(model()),
                List.of(server("RACK-A01-R01", "U01", 1.0f)),
                null,
                null,
                cooling
        );
    }

    @Test
    void shouldAcceptMissingCoolingBlock() {
        DatacenterDefinition definition =
                definitionWithCooling(null);

        assertDoesNotThrow(
                () -> validator.validate(definition)
        );
    }

    @Test
    void shouldAcceptStructurallyValidCoolingBlock() {
        DatacenterDefinition definition =
                definitionWithCooling(cooling());

        assertDoesNotThrow(
                () -> validator.validate(definition)
        );
    }

    @Test
    void shouldRejectNullCoolingZonesList() {
        CoolingConfigDefinition cooling =
                new CoolingConfigDefinition(
                        null,
                        List.of(supplyUnit()),
                        List.of(exhaustUnit()),
                        coolingOptions()
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(cooling)
                )
        );
    }

    @Test
    void shouldRejectEmptyCoolingZonesList() {
        CoolingConfigDefinition cooling =
                new CoolingConfigDefinition(
                        List.of(),
                        List.of(supplyUnit()),
                        List.of(exhaustUnit()),
                        coolingOptions()
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(cooling)
                )
        );
    }

    @Test
    void shouldRejectNullCoolingSupplyUnitsList() {
        CoolingConfigDefinition cooling =
                new CoolingConfigDefinition(
                        List.of(coolingZone()),
                        null,
                        List.of(exhaustUnit()),
                        coolingOptions()
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(cooling)
                )
        );
    }

    @Test
    void shouldRejectNullCoolingExhaustUnitsList() {
        CoolingConfigDefinition cooling =
                new CoolingConfigDefinition(
                        List.of(coolingZone()),
                        List.of(supplyUnit()),
                        null,
                        coolingOptions()
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(cooling)
                )
        );
    }

    @Test
    void shouldRejectNullCoolingOptions() {
        CoolingConfigDefinition cooling =
                new CoolingConfigDefinition(
                        List.of(coolingZone()),
                        List.of(supplyUnit()),
                        List.of(exhaustUnit()),
                        null
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(cooling)
                )
        );
    }

    @Test
    void shouldRejectCoolingBlockWithoutUnits() {
        CoolingConfigDefinition cooling =
                new CoolingConfigDefinition(
                        List.of(coolingZone()),
                        List.of(),
                        List.of(),
                        coolingOptions()
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(cooling)
                )
        );
    }

    private static CoolingConfigDefinition coolingWithZones(
            List<CoolingZoneConfigDefinition> zones
    ) {
        return new CoolingConfigDefinition(
                zones,
                List.of(supplyUnit()),
                List.of(exhaustUnit()),
                coolingOptions()
        );
    }

    @Test
    void shouldRejectNullCoolingZone() {
        CoolingConfigDefinition cooling = coolingWithZones(
                java.util.Collections.singletonList(null)
        );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(definitionWithCooling(cooling))
        );
    }

    @Test
    void shouldRejectBlankCoolingZoneCode() {
        CoolingZoneConfigDefinition zone =
                new CoolingZoneConfigDefinition(
                        " ",
                        List.of("A01"),
                        List.of("RACK-A01-R01")
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithZones(List.of(zone))
                        )
                )
        );
    }

    @Test
    void shouldRejectDuplicatedCoolingZoneCode() {
        CoolingZoneConfigDefinition first =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        List.of("A01"),
                        List.of("RACK-A01-R01")
                );

        CoolingZoneConfigDefinition second =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        List.of("A01"),
                        List.of("RACK-A01-R01")
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithZones(List.of(first, second))
                        )
                )
        );
    }

    @Test
    void shouldRejectNullCoolingZoneColumns() {
        CoolingZoneConfigDefinition zone =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        null,
                        List.of("RACK-A01-R01")
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithZones(List.of(zone))
                        )
                )
        );
    }

    @Test
    void shouldRejectEmptyCoolingZoneColumns() {
        CoolingZoneConfigDefinition zone =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        List.of(),
                        List.of("RACK-A01-R01")
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithZones(List.of(zone))
                        )
                )
        );
    }

    @Test
    void shouldRejectNullCoolingZoneRackCodes() {
        CoolingZoneConfigDefinition zone =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        List.of("A01"),
                        null
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithZones(List.of(zone))
                        )
                )
        );
    }

    @Test
    void shouldRejectEmptyCoolingZoneRackCodes() {
        CoolingZoneConfigDefinition zone =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        List.of("A01"),
                        List.of()
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithZones(List.of(zone))
                        )
                )
        );
    }

    @Test
    void shouldRejectDuplicatedCoolingZoneColumns() {
        CoolingZoneConfigDefinition zone =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        List.of("A01", "A01"),
                        List.of("RACK-A01-R01")
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithZones(List.of(zone))
                        )
                )
        );
    }

    @Test
    void shouldRejectDuplicatedCoolingZoneRackCodes() {
        CoolingZoneConfigDefinition zone =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        List.of("A01"),
                        List.of("RACK-A01-R01", "RACK-A01-R01")
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithZones(List.of(zone))
                        )
                )
        );
    }

    @Test
    void shouldRejectCoolingZoneWithUnknownColumn() {
        CoolingZoneConfigDefinition zone =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        List.of("UNKNOWN"),
                        List.of("RACK-A01-R01")
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithZones(List.of(zone))
                        )
                )
        );
    }

    @Test
    void shouldRejectCoolingZoneWithUnknownRackInColumn() {
        CoolingZoneConfigDefinition zone =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        List.of("A01"),
                        List.of("UNKNOWN-RACK")
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithZones(List.of(zone))
                        )
                )
        );
    }

    private static DatacenterDefinition definitionWithCooling(
            CoolingConfigDefinition cooling,
            List<ServerDefinition> servers
    ) {
        return new DatacenterDefinition(
                "Demo Datacenter",
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(model()),
                servers,
                null,
                null,
                cooling
        );
    }

    @Test
    void shouldRejectCoolingZoneWithoutInstalledServers() {
        DatacenterDefinition definition =
                definitionWithCooling(
                        cooling(),
                        List.of()
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(definition)
        );
    }

    @Test
    void shouldRejectServerBelongingToMultipleCoolingZones() {
        CoolingZoneConfigDefinition firstZone =
                new CoolingZoneConfigDefinition(
                        "ZONE-01",
                        List.of("A01"),
                        List.of("RACK-A01-R01")
                );

        CoolingZoneConfigDefinition secondZone =
                new CoolingZoneConfigDefinition(
                        "ZONE-02",
                        List.of("A01"),
                        List.of("RACK-A01-R01")
                );

        CoolingConfigDefinition cooling =
                new CoolingConfigDefinition(
                        List.of(firstZone, secondZone),
                        List.of(supplyUnit()),
                        List.of(exhaustUnit()),
                        coolingOptions()
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(cooling)
                )
        );
    }

    @Test
    void shouldResolveLegacyServerForCoolingZone() {
        assertDoesNotThrow(
                () -> validator.validate(
                        definitionWithCooling(cooling())
                )
        );
    }

    private static CoolingConfigDefinition coolingWithOptions(
            CoolingSystemOptionsDefinition options
    ) {
        return new CoolingConfigDefinition(
                List.of(coolingZone()),
                List.of(supplyUnit()),
                List.of(exhaustUnit()),
                options
        );
    }

    @Test
    void shouldRejectInvalidCoolingAirDensity() {
        double[] invalidValues = {
                0.0,
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            CoolingSystemOptionsDefinition options =
                    new CoolingSystemOptionsDefinition(
                            invalidValue,
                            1005.0,
                            24.0,
                            0.95
                    );

            assertThrows(
                    DatacenterConfigValidationException.class,
                    () -> validator.validate(
                            definitionWithCooling(
                                    coolingWithOptions(options)
                            )
                    ),
                    "Expected rejection for air density: " + invalidValue
            );
        }
    }

    @Test
    void shouldRejectInvalidCoolingAirSpecificHeat() {
        double[] invalidValues = {
                0.0,
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            CoolingSystemOptionsDefinition options =
                    new CoolingSystemOptionsDefinition(
                            1.204,
                            invalidValue,
                            24.0,
                            0.95
                    );

            assertThrows(
                    DatacenterConfigValidationException.class,
                    () -> validator.validate(
                            definitionWithCooling(
                                    coolingWithOptions(options)
                            )
                    ),
                    "Expected rejection for air specific heat: " + invalidValue
            );
        }
    }

    @Test
    void shouldRejectNonFiniteCoolingInitialInletTemperature() {
        double[] invalidValues = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            CoolingSystemOptionsDefinition options =
                    new CoolingSystemOptionsDefinition(
                            1.204,
                            1005.0,
                            invalidValue,
                            0.95
                    );

            assertThrows(
                    DatacenterConfigValidationException.class,
                    () -> validator.validate(
                            definitionWithCooling(
                                    coolingWithOptions(options)
                            )
                    ),
                    "Expected rejection for inlet temperature: " + invalidValue
            );
        }
    }

    @Test
    void shouldRejectInvalidMaximumRecirculationFraction() {
        double[] invalidValues = {
                -0.01,
                1.01,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            CoolingSystemOptionsDefinition options =
                    new CoolingSystemOptionsDefinition(
                            1.204,
                            1005.0,
                            24.0,
                            invalidValue
                    );

            assertThrows(
                    DatacenterConfigValidationException.class,
                    () -> validator.validate(
                            definitionWithCooling(
                                    coolingWithOptions(options)
                            )
                    ),
                    "Expected rejection for recirculation fraction: "
                            + invalidValue
            );
        }
    }

    @Test
    void shouldAcceptMaximumRecirculationFractionBoundaries() {
        double[] validValues = {
                0.0,
                1.0
        };

        for (double validValue : validValues) {
            CoolingSystemOptionsDefinition options =
                    new CoolingSystemOptionsDefinition(
                            1.204,
                            1005.0,
                            24.0,
                            validValue
                    );

            assertDoesNotThrow(
                    () -> validator.validate(
                            definitionWithCooling(
                                    coolingWithOptions(options)
                            )
                    ),
                    "Expected acceptance for recirculation fraction: "
                            + validValue
            );
        }
    }

    private static CoolingConfigDefinition coolingWithSupplyUnits(
            List<SupplyCoolingUnitConfigDefinition> supplyUnits
    ) {
        return new CoolingConfigDefinition(
                List.of(coolingZone()),
                supplyUnits,
                List.of(exhaustUnit()),
                coolingOptions()
        );
    }

    @Test
    void shouldRejectNullCoolingSupplyUnit() {
        CoolingConfigDefinition cooling =
                coolingWithSupplyUnits(
                        java.util.Collections.singletonList(null)
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(cooling)
                )
        );
    }

    @Test
    void shouldRejectBlankCoolingSupplyUnitCode() {
        SupplyCoolingUnitConfigDefinition unit =
                new SupplyCoolingUnitConfigDefinition(
                        " ",
                        8.0,
                        100_000.0,
                        18.0,
                        List.of(coolingInfluence()),
                        false
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithSupplyUnits(List.of(unit))
                        )
                )
        );
    }

    @Test
    void shouldRejectDuplicatedCoolingSupplyUnitCode() {
        SupplyCoolingUnitConfigDefinition first =
                new SupplyCoolingUnitConfigDefinition(
                        "SUPPLY-01",
                        8.0,
                        100_000.0,
                        18.0,
                        List.of(coolingInfluence()),
                        false
                );

        SupplyCoolingUnitConfigDefinition second =
                new SupplyCoolingUnitConfigDefinition(
                        "SUPPLY-01",
                        6.0,
                        80_000.0,
                        19.0,
                        List.of(coolingInfluence()),
                        true
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithSupplyUnits(
                                        List.of(first, second)
                                )
                        )
                )
        );
    }

    @Test
    void shouldRejectInvalidCoolingSupplyUnitAirflow() {
        double[] invalidValues = {
                0.0,
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            SupplyCoolingUnitConfigDefinition unit =
                    new SupplyCoolingUnitConfigDefinition(
                            "SUPPLY-01",
                            invalidValue,
                            100_000.0,
                            18.0,
                            List.of(coolingInfluence()),
                            false
                    );

            assertThrows(
                    DatacenterConfigValidationException.class,
                    () -> validator.validate(
                            definitionWithCooling(
                                    coolingWithSupplyUnits(
                                            List.of(unit)
                                    )
                            )
                    ),
                    "Expected rejection for supply airflow: "
                            + invalidValue
            );
        }
    }

    @Test
    void shouldRejectInvalidCoolingSupplyUnitCapacity() {
        double[] invalidValues = {
                0.0,
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            SupplyCoolingUnitConfigDefinition unit =
                    new SupplyCoolingUnitConfigDefinition(
                            "SUPPLY-01",
                            8.0,
                            invalidValue,
                            18.0,
                            List.of(coolingInfluence()),
                            false
                    );

            assertThrows(
                    DatacenterConfigValidationException.class,
                    () -> validator.validate(
                            definitionWithCooling(
                                    coolingWithSupplyUnits(
                                            List.of(unit)
                                    )
                            )
                    ),
                    "Expected rejection for supply capacity: "
                            + invalidValue
            );
        }
    }

    @Test
    void shouldRejectNonFiniteCoolingSupplyAirTemperature() {
        double[] invalidValues = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            SupplyCoolingUnitConfigDefinition unit =
                    new SupplyCoolingUnitConfigDefinition(
                            "SUPPLY-01",
                            8.0,
                            100_000.0,
                            invalidValue,
                            List.of(coolingInfluence()),
                            false
                    );

            assertThrows(
                    DatacenterConfigValidationException.class,
                    () -> validator.validate(
                            definitionWithCooling(
                                    coolingWithSupplyUnits(
                                            List.of(unit)
                                    )
                            )
                    ),
                    "Expected rejection for supply-air temperature: "
                            + invalidValue
            );
        }
    }

    private static SupplyCoolingUnitConfigDefinition supplyUnitWithInfluences(
            List<CoolingZoneInfluenceConfigDefinition> influences
    ) {
        return new SupplyCoolingUnitConfigDefinition(
                "SUPPLY-01",
                8.0,
                100_000.0,
                18.0,
                influences,
                false
        );
    }

    @Test
    void shouldRejectNullCoolingSupplyUnitInfluences() {
        SupplyCoolingUnitConfigDefinition unit =
                supplyUnitWithInfluences(null);

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithSupplyUnits(List.of(unit))
                        )
                )
        );
    }

    @Test
    void shouldRejectEmptyCoolingSupplyUnitInfluences() {
        SupplyCoolingUnitConfigDefinition unit =
                supplyUnitWithInfluences(List.of());

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithSupplyUnits(List.of(unit))
                        )
                )
        );
    }

    @Test
    void shouldRejectNullCoolingSupplyUnitInfluence() {
        SupplyCoolingUnitConfigDefinition unit =
                supplyUnitWithInfluences(
                        java.util.Collections.singletonList(null)
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithSupplyUnits(List.of(unit))
                        )
                )
        );
    }

    @Test
    void shouldRejectBlankCoolingSupplyInfluenceZoneCode() {
        CoolingZoneInfluenceConfigDefinition influence =
                new CoolingZoneInfluenceConfigDefinition(
                        " ",
                        1.0
                );

        SupplyCoolingUnitConfigDefinition unit =
                supplyUnitWithInfluences(List.of(influence));

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithSupplyUnits(List.of(unit))
                        )
                )
        );
    }

    @Test
    void shouldRejectUnknownCoolingSupplyInfluenceZone() {
        CoolingZoneInfluenceConfigDefinition influence =
                new CoolingZoneInfluenceConfigDefinition(
                        "UNKNOWN-ZONE",
                        1.0
                );

        SupplyCoolingUnitConfigDefinition unit =
                supplyUnitWithInfluences(List.of(influence));

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithSupplyUnits(List.of(unit))
                        )
                )
        );
    }

    @Test
    void shouldRejectDuplicatedCoolingSupplyInfluenceZone() {
        String existingZoneCode = coolingZone().code();

        CoolingZoneInfluenceConfigDefinition first =
                new CoolingZoneInfluenceConfigDefinition(
                        existingZoneCode,
                        0.5
                );

        CoolingZoneInfluenceConfigDefinition second =
                new CoolingZoneInfluenceConfigDefinition(
                        existingZoneCode,
                        0.5
                );

        SupplyCoolingUnitConfigDefinition unit =
                supplyUnitWithInfluences(
                        List.of(first, second)
                );

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithSupplyUnits(List.of(unit))
                        )
                )
        );
    }

    @Test
    void shouldRejectInvalidCoolingSupplyInfluenceWeight() {
        double[] invalidValues = {
                0.0,
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            CoolingZoneInfluenceConfigDefinition influence =
                    new CoolingZoneInfluenceConfigDefinition(
                            "ZONE-01",
                            invalidValue
                    );

            SupplyCoolingUnitConfigDefinition unit =
                    supplyUnitWithInfluences(List.of(influence));

            assertThrows(
                    DatacenterConfigValidationException.class,
                    () -> validator.validate(
                            definitionWithCooling(
                                    coolingWithSupplyUnits(List.of(unit))
                            )
                    ),
                    "Expected rejection for influence weight: "
                            + invalidValue
            );
        }
    }

    @Test
    void shouldRejectCoolingSupplyInfluenceWeightsNotSummingToOne() {
        CoolingZoneInfluenceConfigDefinition influence =
                new CoolingZoneInfluenceConfigDefinition(
                        "ZONE-01",
                        0.75
                );

        SupplyCoolingUnitConfigDefinition unit =
                supplyUnitWithInfluences(List.of(influence));

        assertThrows(
                DatacenterConfigValidationException.class,
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithSupplyUnits(List.of(unit))
                        )
                )
        );
    }

    @Test
    void shouldAcceptValidCoolingSupplyInfluence() {
        CoolingZoneInfluenceConfigDefinition influence =
                new CoolingZoneInfluenceConfigDefinition(
                        coolingZone().code(),
                        1.0
                );

        SupplyCoolingUnitConfigDefinition unit =
                supplyUnitWithInfluences(List.of(influence));

        assertDoesNotThrow(
                () -> validator.validate(
                        definitionWithCooling(
                                coolingWithSupplyUnits(List.of(unit))
                        )
                )
        );
    }
}

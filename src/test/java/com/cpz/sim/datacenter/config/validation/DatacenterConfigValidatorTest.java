package com.cpz.sim.datacenter.config.validation;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
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

    private static ServerDefinition server(String rackCode, String slot, float workloadFactor) {
        return new ServerDefinition(
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
        return new DatacenterDefinition(
                "Demo Datacenter",
                layout,
                List.of(model()),
                servers
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
    void shouldRejectDuplicatedRackCode() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"), rack("RACK-A01-R01"))),
                List.of()
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
    void shouldRejectSlotOutsideRackRange() {
        DatacenterDefinition definition = definition(
                layout(List.of(new RackDefinition("RACK-A01-R01", "A01", "R01", 2))),
                List.of(server("RACK-A01-R01", "U03", 1.0f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectInvalidSlotFormat() {
        DatacenterDefinition definition = definition(
                layout(List.of(rack("RACK-A01-R01"))),
                List.of(server("RACK-A01-R01", "S01", 1.0f))
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
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
                List.of()
        );
        assertThrows(DatacenterConfigValidationException.class, () -> validator.validate(definition));
    }
}

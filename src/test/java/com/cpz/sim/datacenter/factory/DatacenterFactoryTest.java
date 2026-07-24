package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidationException;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.workload.ServerWorkloadFactorProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class DatacenterFactoryTest {

    private static DatacenterLayoutDefinition validLayout() {
        return new DatacenterLayoutDefinition(
                List.of(
                        new RackDefinition("RACK-A01-R01", "A01", "R01", 42),
                        new RackDefinition("RACK-A01-R02", "A01", "R02", 42)
                )
        );
    }

    private static DatacenterDefinition validDefinition() {
        return new DatacenterDefinition(
                "Demo Datacenter",
                validLayout(),
                List.of(
                        new ServerModelDefinition(
                                "SRV-DEMO-001",
                                "CPZ",
                                "Demo Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U01",
                                "SRV-DEMO-001",
                                "OK",
                                ServerRole.GENERAL_PURPOSE
                        ),
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U02",
                                "SRV-DEMO-001",
                                "OK",
                                ServerRole.GENERAL_PURPOSE
                        )
                )
        );
    }

    @Test
    void shouldCreateDatacenterFromValidDefinition() {
        DatacenterDefinition definition = validDefinition();
        DatacenterFactory factory = new DatacenterFactory();
        Datacenter datacenter = factory.create(definition);
        assertEquals(2, datacenter.getRackCount());
        assertEquals(2, datacenter.getServerCount());
    }

    @Test
    void shouldCreateRackWithExplicitSlotCodesFromDefinition() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                new DatacenterLayoutDefinition(
                        List.of(new RackDefinition("RACK-A01-R01", "A01", "R01", List.of("S03", "GPU-A", "NETWORK")))
                ),
                List.of(
                        new ServerModelDefinition(
                                "SRV-DEMO-001",
                                "CPZ",
                                "Demo Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "GPU-A",
                                "SRV-DEMO-001",
                                "OK",
                                ServerRole.GENERAL_PURPOSE
                        )
                )
        );
        Datacenter datacenter = new DatacenterFactory().create(definition);
        assertEquals(1, datacenter.getRackCount());
        assertEquals(3, datacenter.getRacks().getFirst().getSlotCount());
        assertEquals(List.of("S03", "GPU-A", "NETWORK"), datacenter.getRacks().getFirst().getSlotCodes());
        assertEquals("A01-RACK-A01-R01-GPU-A", datacenter.getServers().getFirst().getCode());
    }

    @Test
    void shouldCreateServersWithSameRackCodeAndSlotInDifferentColumns() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                new DatacenterLayoutDefinition(
                        List.of(
                                new RackDefinition("R01", "C01", "R01", List.of("S01", "S02")),
                                new RackDefinition("R01", "C02", "R01", List.of("S01", "S02"))
                        )
                ),
                List.of(
                        new ServerModelDefinition(
                                "SRV-DEMO-001",
                                "CPZ",
                                "Demo Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "C01",
                                "R01",
                                "S01",
                                "SRV-DEMO-001",
                                "OK",
                                ServerRole.GENERAL_PURPOSE,
                                0.5f
                        ),
                        new ServerDefinition(
                                "C02",
                                "R01",
                                "S01",
                                "SRV-DEMO-001",
                                "OK",
                                ServerRole.GENERAL_PURPOSE,
                                1.5f
                        )
                )
        );
        Datacenter datacenter = new DatacenterFactory().create(definition);
        Server first = datacenter.getServer("C01", "R01", "S01").orElseThrow();
        Server second = datacenter.getServer("C02", "R01", "S01").orElseThrow();
        assertEquals("C01-R01-S01", first.getCode());
        assertEquals("C02-R01-S01", second.getCode());
        ServerWorkloadFactorProvider factors = new WorkloadFactorProviderFactory().create(definition);
        assertEquals(0.5f, factors.getFactor(first));
        assertEquals(1.5f, factors.getFactor(second));
    }

    @Test
    void shouldGenerateLegacySlotCodesFromSlotCount() {
        Datacenter datacenter = new DatacenterFactory().create(validDefinition());
        assertEquals(42, datacenter.getRacks().getFirst().getSlotCount());
        assertEquals("U01", datacenter.getRacks().getFirst().getSlotCodes().getFirst());
        assertEquals("U42", datacenter.getRacks().getFirst().getSlotCodes().getLast());
    }

    @Test
    void shouldUseServerModelPowerValues() {
        DatacenterDefinition definition = validDefinition();
        DatacenterFactory factory = new DatacenterFactory();
        Datacenter datacenter = factory.create(definition);
        Server firstServer = datacenter.getServers().getFirst();
        firstServer.setUtilization(0.5);
        firstServer.updatePowerConsumption();
        assertEquals(200.0f, firstServer.getCurrentPowerWatts());
    }

    @Test
    void shouldApplyInitialHardwareStatus() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                validLayout(),
                List.of(
                        new ServerModelDefinition(
                                "SRV-DEMO-001",
                                "CPZ",
                                "Demo Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U01",
                                "SRV-DEMO-001",
                                "OFFLINE",
                                ServerRole.GENERAL_PURPOSE
                        )
                )
        );
        DatacenterFactory factory = new DatacenterFactory();
        Datacenter datacenter = factory.create(definition);
        Server server = datacenter.getServers().getFirst();
        assertEquals(HardwareStatus.OFFLINE, server.getStatus());
    }

    @Test
    void shouldPreserveExplicitRoleAndDefaultOmittedRole() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Role Datacenter",
                validLayout(),
                List.of(
                        new ServerModelDefinition(
                                "SRV-DEMO-001",
                                "CPZ",
                                "Demo Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U01",
                                "SRV-DEMO-001",
                                "OK",
                                ServerRole.AI
                        ),
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U02",
                                "SRV-DEMO-001",
                                "OK",
                                null
                        )
                )
        );

        Datacenter datacenter = new DatacenterFactory().create(definition);
        Server explicitServer = datacenter.getServers().getFirst();
        Server defaultedServer = datacenter.getServers().get(1);

        assertEquals(ServerRole.AI, explicitServer.getRole());
        assertEquals(ServerRole.GENERAL_PURPOSE, defaultedServer.getRole());
    }

    @Test
    void shouldRejectUnknownServerModel() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                validLayout(),
                List.of(
                        new ServerModelDefinition(
                                "SRV-DEMO-001",
                                "CPZ",
                                "Demo Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U01",
                                "UNKNOWN",
                                "OK",
                                ServerRole.GENERAL_PURPOSE
                        )
                )
        );
        DatacenterFactory factory = new DatacenterFactory();
        assertThrows(DatacenterConfigValidationException.class, () -> factory.create(definition));
    }

    @Test
    void shouldRejectDuplicatedServerLocations() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                validLayout(),
                List.of(
                        new ServerModelDefinition(
                                "SRV-DEMO-001",
                                "CPZ",
                                "Demo Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U01",
                                "SRV-DEMO-001",
                                "OK",
                                ServerRole.GENERAL_PURPOSE
                        ),
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U01",
                                "SRV-DEMO-001",
                                "OK",
                                ServerRole.GENERAL_PURPOSE
                        )
                )
        );
        DatacenterFactory factory = new DatacenterFactory();
        assertThrows(DatacenterConfigValidationException.class, () -> factory.create(definition));
    }

    @Test
    void shouldRejectUnknownRackCode() {
        DatacenterDefinition definition = new DatacenterDefinition(
                "Demo Datacenter",
                validLayout(),
                List.of(
                        new ServerModelDefinition(
                                "SRV-DEMO-001",
                                "CPZ",
                                "Demo Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "UNKNOWN_RACK",
                                "U01",
                                "SRV-DEMO-001",
                                "OK",
                                ServerRole.GENERAL_PURPOSE
                        )
                )
        );
        DatacenterFactory factory = new DatacenterFactory();
        assertThrows(DatacenterConfigValidationException.class, () -> factory.create(definition));
    }

    @Test
    void shouldRejectNullValidator() {
        assertThrows(NullPointerException.class, () -> new DatacenterFactory(null));
    }

}

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
                                "OK"
                        ),
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U02",
                                "SRV-DEMO-001",
                                "OK"
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
    void shouldUseServerModelPowerValues() {
        DatacenterDefinition definition = validDefinition();
        DatacenterFactory factory = new DatacenterFactory();
        Datacenter datacenter = factory.create(definition);
        Server firstServer = datacenter.getServers().getFirst();
        firstServer.setUtilization(0.5f);
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
                                "OFFLINE"
                        )
                )
        );
        DatacenterFactory factory = new DatacenterFactory();
        Datacenter datacenter = factory.create(definition);
        Server server = datacenter.getServers().getFirst();
        assertEquals(HardwareStatus.OFFLINE, server.getStatus());
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
                                "OK"
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
                                "OK"
                        ),
                        new ServerDefinition(
                                "RACK-A01-R01",
                                "U01",
                                "SRV-DEMO-001",
                                "OK"
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
                                "OK"
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

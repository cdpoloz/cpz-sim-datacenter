package com.cpz.sim.datacenter.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class DatacenterTest {

    private Server serverA, serverB, serverC;
    private Datacenter datacenter;

    private static Server createServer(
            ServerConfig config,
            Column column,
            Row row,
            Slot slot
    ) {
        return new Server(
                new ServerLocation(column, row, slot),
                config,
                HardwareStatus.OK
        );
    }

    @BeforeEach
    void setUp() {
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                300.0f
        );

        serverA = createServer(config, Column.A01, Row.R01, Slot.S01);
        serverB = createServer(config, Column.A01, Row.R02, Slot.S01);
        serverC = createServer(config, Column.A02, Row.R01, Slot.S01);

        serverA.setUtilization(0.5f);  // 200 W
        serverB.setUtilization(1.0f);  // 300 W
        serverC.setUtilization(0.25f); // 150 W

        serverA.updatePowerConsumption();
        serverB.updatePowerConsumption();
        serverC.updatePowerConsumption();

        datacenter = new Datacenter(List.of(serverA, serverB, serverC));
    }

    @Test
    void shouldExposeServersAndServerCount() {
        assertEquals(3, datacenter.getServerCount());
        assertEquals(List.of(serverA, serverB, serverC), datacenter.getServers());
    }

    @Test
    void shouldCalculateTotalItPower() {
        assertEquals(650.0f, datacenter.getTotalItPowerWatts());
    }

    @Test
    void shouldCalculateItPowerByColumn() {
        assertEquals(500.0f, datacenter.getItPowerWatts(Column.A01));
        assertEquals(150.0f, datacenter.getItPowerWatts(Column.A02));
        assertEquals(0.0f, datacenter.getItPowerWatts(Column.A03));
    }

    @Test
    void shouldCalculateItPowerByRow() {
        assertEquals(350.0f, datacenter.getItPowerWatts(Row.R01));
        assertEquals(300.0f, datacenter.getItPowerWatts(Row.R02));
        assertEquals(0.0f, datacenter.getItPowerWatts(Row.R03));
    }

    @Test
    void shouldReflectChangesInServerPower() {
        serverA.setUtilization(1.0f);
        serverA.updatePowerConsumption();
        assertEquals(750.0f, datacenter.getTotalItPowerWatts());
        assertEquals(600.0f, datacenter.getItPowerWatts(Column.A01));
    }

    @Test
    void shouldRejectDuplicateServerLocations() {
        Server duplicate = new Server(serverA.getLocation(), serverA.getConfig(), HardwareStatus.OK);
        assertThrows(IllegalArgumentException.class, () -> new Datacenter(List.of(serverA, duplicate)));
    }

    @Test
    void shouldRejectNullServerList() {
        assertThrows(NullPointerException.class, () -> new Datacenter(null));
    }

    @Test
    void shouldRejectNullServerElement() {
        List<Server> servers = new ArrayList<>();
        servers.add(serverA);
        servers.add(null);
        assertThrows(IllegalArgumentException.class, () -> new Datacenter(servers));
    }

    @Test
    void shouldExposeAnUnmodifiableServerList() {
        assertThrows(UnsupportedOperationException.class, () -> datacenter.getServers().add(serverA));
    }

}

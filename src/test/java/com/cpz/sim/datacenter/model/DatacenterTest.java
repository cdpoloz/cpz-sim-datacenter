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
    private Rack rackA, rackB, rackC, emptyRack;
    private Datacenter datacenter;

    private static Server createServer(
            ServerConfig config,
            RackCode rackCode,
            String column,
            String slot
    ) {
        return new Server(
                new ServerLocation(column, rackCode, slot),
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

        rackA = new Rack(new RackCode("RACK-A01-R01"), new RackLocation("A01", "R01"), 42);
        rackB = new Rack(new RackCode("RACK-A01-R02"), new RackLocation("A01", "R02"), 42);
        rackC = new Rack(new RackCode("RACK-A02-R01"), new RackLocation("A02", "R01"), 42);
        emptyRack = new Rack(new RackCode("RACK-A02-R02"), new RackLocation("A02", "R02"), 42);

        serverA = createServer(config, rackA.getCode(), rackA.getColumn(), "U01");
        serverB = createServer(config, rackB.getCode(), rackB.getColumn(), "U01");
        serverC = createServer(config, rackC.getCode(), rackC.getColumn(), "U01");

        serverA.setUtilization(0.5f);  // 200 W
        serverB.setUtilization(1.0f);  // 300 W
        serverC.setUtilization(0.25f); // 150 W

        serverA.updatePowerConsumption();
        serverB.updatePowerConsumption();
        serverC.updatePowerConsumption();

        datacenter = new Datacenter(List.of(rackA, rackB, rackC, emptyRack), List.of(serverA, serverB, serverC));
    }

    @Test
    void shouldExposeRacksServersAndCounts() {
        assertEquals(4, datacenter.getRackCount());
        assertEquals(List.of(rackA, rackB, rackC, emptyRack), datacenter.getRacks());
        assertEquals(3, datacenter.getServerCount());
        assertEquals(List.of(serverA, serverB, serverC), datacenter.getServers());
    }

    @Test
    void shouldCalculateTotalItPower() {
        assertEquals(650.0f, datacenter.getTotalItPowerWatts());
    }

    @Test
    void shouldCalculateItPowerByColumn() {
        assertEquals(500.0f, datacenter.getItPowerWattsByColumn("A01"));
        assertEquals(150.0f, datacenter.getItPowerWattsByColumn("A02"));
        assertEquals(0.0f, datacenter.getItPowerWattsByColumn("A03"));
    }

    @Test
    void shouldCalculateItPowerByRow() {
        assertEquals(350.0f, datacenter.getItPowerWattsByRow("R01"));
        assertEquals(300.0f, datacenter.getItPowerWattsByRow("R02"));
        assertEquals(0.0f, datacenter.getItPowerWattsByRow("R03"));
    }

    @Test
    void shouldCalculateItPowerByRackCode() {
        assertEquals(200.0f, datacenter.getItPowerWattsByRackCode(rackA.getCode()));
        assertEquals(0.0f, datacenter.getItPowerWattsByRackCode(emptyRack.getCode()));
    }

    @Test
    void shouldReflectChangesInServerPower() {
        serverA.setUtilization(1.0f);
        serverA.updatePowerConsumption();
        assertEquals(750.0f, datacenter.getTotalItPowerWatts());
        assertEquals(600.0f, datacenter.getItPowerWattsByColumn("A01"));
    }

    @Test
    void shouldRejectDuplicateServerLocations() {
        Server duplicate = new Server(serverA.getLocation(), serverA.getConfig(), HardwareStatus.OK);
        assertThrows(
                IllegalArgumentException.class,
                () -> new Datacenter(List.of(rackA), List.of(serverA, duplicate))
        );
    }

    @Test
    void shouldRejectServerOutsideRackSlotRange() {
        Rack smallRack = new Rack(new RackCode("RACK-SMALL"), new RackLocation("A99", "R99"), 1);
        Server server = createServer(serverA.getConfig(), smallRack.getCode(), smallRack.getColumn(), "U02");
        assertThrows(
                IllegalArgumentException.class,
                () -> new Datacenter(List.of(smallRack), List.of(server))
        );
    }

    @Test
    void shouldAcceptServersInExplicitOpaqueSlots() {
        RackCode rackCode = new RackCode("RACK-EXPLICIT");
        Rack explicitRack = new Rack(rackCode, new RackLocation("A99", "R99"), List.of("GPU-A", "NETWORK", "SPARE"));
        Server server = createServer(serverA.getConfig(), rackCode, explicitRack.getColumn(), "NETWORK");
        Datacenter explicitDatacenter = new Datacenter(List.of(explicitRack), List.of(server));
        assertEquals("A99-RACK-EXPLICIT-NETWORK", explicitDatacenter.getServers().getFirst().getCode());
    }

    @Test
    void shouldRejectServerInUndeclaredExplicitSlot() {
        RackCode rackCode = new RackCode("RACK-EXPLICIT");
        Rack explicitRack = new Rack(rackCode, new RackLocation("A99", "R99"), List.of("GPU-A"));
        Server server = createServer(serverA.getConfig(), rackCode, explicitRack.getColumn(), "gpu-a");
        assertThrows(
                IllegalArgumentException.class,
                () -> new Datacenter(List.of(explicitRack), List.of(server))
        );
    }

    @Test
    void shouldAllowSameRackCodeAndSlotInDifferentColumns() {
        RackCode rackCode = new RackCode("R01");
        Rack firstRack = new Rack(rackCode, "C01", "R01", List.of("S01", "S02"));
        Rack secondRack = new Rack(rackCode, "C02", "R01", List.of("S01", "S02"));
        Server firstServer = createServer(serverA.getConfig(), rackCode, "C01", "S01");
        Server secondServer = createServer(serverA.getConfig(), rackCode, "C02", "S01");
        Datacenter datacenter = new Datacenter(List.of(firstRack, secondRack), List.of(firstServer, secondServer));
        assertEquals("C01-R01-S01", firstServer.getCode());
        assertEquals("C02-R01-S01", secondServer.getCode());
        assertEquals(List.of(firstServer), datacenter.getServers("C01", "R01"));
        assertEquals(List.of(secondServer), datacenter.getServers("C02", "R01"));
        assertEquals(firstServer, datacenter.getServer("C01", "R01", "S01").orElseThrow());
        assertEquals(secondServer, datacenter.getServer("C02", "R01", "S01").orElseThrow());
    }

    @Test
    void shouldRejectDuplicatedRackLocation() {
        Rack firstRack = new Rack(new RackCode("R01"), "C01", "R01", List.of("S01"));
        Rack secondRack = new Rack(new RackCode("R01"), "C01", "R02", List.of("S01"));
        assertThrows(IllegalArgumentException.class, () -> new Datacenter(List.of(firstRack, secondRack), List.of()));
    }

    @Test
    void shouldExposeRackAndServerLookups() {
        assertEquals(rackA, datacenter.findRack("A01", "RACK-A01-R01").orElseThrow());
        assertEquals(List.of(serverA), datacenter.getServers("A01", "RACK-A01-R01"));
        assertEquals(List.of(), datacenter.getServers(emptyRack.getLocation()));
        assertEquals(serverA, datacenter.getServer("A01", "RACK-A01-R01", "U01").orElseThrow());
        assertEquals(List.of(), datacenter.getServers("A02", "RACK-A02-R02"));
        assertEquals(true, datacenter.getServer("A02", "RACK-A02-R02", "U01").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> datacenter.getServers("A01", "RACK-A01-R01").add(serverA));
    }

    @Test
    void shouldRejectInvalidLookupLocations() {
        assertThrows(IllegalArgumentException.class, () -> datacenter.getServers("A99", "RACK-A99-R01"));
        assertThrows(IllegalArgumentException.class, () -> datacenter.getServer("A01", "RACK-A01-R01", "U99"));
    }

    @Test
    void shouldRejectDuplicateRacks() {
        Rack duplicate = new Rack(rackA.getLocation(), rackA.getRow(), rackA.getSlotCount());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Datacenter(List.of(rackA, duplicate), List.of(serverA))
        );
    }

    @Test
    void shouldAcceptRacksWithoutServers() {
        Datacenter datacenterWithOnlyEmptyRacks = new Datacenter(List.of(rackA, rackB), List.of());
        assertEquals(2, datacenterWithOnlyEmptyRacks.getRackCount());
        assertEquals(0, datacenterWithOnlyEmptyRacks.getServerCount());
        assertEquals(0.0f, datacenterWithOnlyEmptyRacks.getTotalItPowerWatts());
    }

    @Test
    void shouldRejectNullServerList() {
        assertThrows(NullPointerException.class, () -> new Datacenter(List.of(rackA), null));
    }

    @Test
    void shouldRejectNullRackList() {
        assertThrows(NullPointerException.class, () -> new Datacenter(null, List.of(serverA)));
    }

    @Test
    void shouldRejectNullServerElement() {
        List<Server> servers = new ArrayList<>();
        servers.add(serverA);
        servers.add(null);
        assertThrows(IllegalArgumentException.class, () -> new Datacenter(List.of(rackA), servers));
    }

    @Test
    void shouldRejectNullRackElement() {
        List<Rack> racks = new ArrayList<>();
        racks.add(rackA);
        racks.add(null);
        assertThrows(IllegalArgumentException.class, () -> new Datacenter(racks, List.of(serverA)));
    }

    @Test
    void shouldExposeUnmodifiableLists() {
        assertThrows(UnsupportedOperationException.class, () -> datacenter.getRacks().add(rackA));
        assertThrows(UnsupportedOperationException.class, () -> datacenter.getServers().add(serverA));
    }

}

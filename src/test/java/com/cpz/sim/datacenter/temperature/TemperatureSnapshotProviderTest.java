package com.cpz.sim.datacenter.temperature;

import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.ServerTemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshotProvider;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
/**
 * @author CPZ
 */
class TemperatureSnapshotProviderTest {

    private static final double EPSILON = 0.000001;
    private static final RackCode RACK_CODE = new RackCode("RACK-A01-R01");
    private static final String SERVER_CODE = "A01-RACK-A01-R01-U01";

    @Test
    void rejectsNullDatacenter() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                TemperatureSystemOptions.defaults(),
                new SimpleServerTemperatureModel()
        );
        assertThrows(
                NullPointerException.class,
                () -> new TemperatureSnapshotProvider(
                        null,
                        system,
                        TemperatureSystemOptions.defaults()
                )
        );
    }

    @Test
    void rejectsNullTemperatureSystem() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        assertThrows(
                NullPointerException.class,
                () -> new TemperatureSnapshotProvider(
                        datacenter,
                        null,
                        TemperatureSystemOptions.defaults()
                )
        );
    }

    @Test
    void rejectsNullOptions() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                TemperatureSystemOptions.defaults(),
                new SimpleServerTemperatureModel()
        );
        assertThrows(
                NullPointerException.class,
                () -> new TemperatureSnapshotProvider(
                        datacenter,
                        system,
                        null
                )
        );
    }

    @Test
    void rejectsNullTick() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        TemperatureSystemOptions options = TemperatureSystemOptions.defaults();
        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                options,
                new SimpleServerTemperatureModel()
        );
        TemperatureSnapshotProvider provider = new TemperatureSnapshotProvider(
                datacenter,
                system,
                options
        );
        assertThrows(NullPointerException.class, () -> provider.snapshot(null));
    }

    @Test
    void createsTemperatureSnapshotForServer() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OK, 0.75f);
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                25.0,
                5000.0,
                8.0
        );
        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                options,
                new SimpleServerTemperatureModel()
        );
        SimulationTick tick = tickAtSeconds(1, 60, 60);
        system.update(tick);
        TemperatureSnapshotProvider provider = new TemperatureSnapshotProvider(
                datacenter,
                system,
                options
        );
        TemperatureSnapshot snapshot = provider.snapshot(tick);
        assertEquals(1, snapshot.tickIndex());
        assertEquals(60.0, snapshot.elapsedSeconds(), EPSILON);
        assertEquals(25.0, snapshot.ambientTemperatureCelsius(), EPSILON);
        assertEquals(1, snapshot.serverCount());
        assertEquals(29.8, snapshot.averageTemperatureCelsius(), EPSILON);
        assertEquals(29.8, snapshot.maxTemperatureCelsius(), EPSILON);
        ServerTemperatureSnapshot serverSnapshot = snapshot.servers().getFirst();
        assertEquals(SERVER_CODE, serverSnapshot.serverCode());
        assertEquals("A01", serverSnapshot.column());
        assertEquals(RACK_CODE, serverSnapshot.rackCode());
        assertEquals("U01", serverSnapshot.slot());
        assertEquals(HardwareStatus.OK, serverSnapshot.status());
        assertEquals(0.75f, serverSnapshot.utilization(), EPSILON);
        assertEquals(400.0, serverSnapshot.currentPowerWatts(), EPSILON);
        assertEquals(29.8, serverSnapshot.temperatureCelsius(), EPSILON);
    }

    @Test
    void createsTemperatureSnapshotForOfflineServer() {
        Datacenter datacenter = createDatacenterWithOneServer(HardwareStatus.OFFLINE, 1.0f);
        TemperatureSystemOptions options = new TemperatureSystemOptions(
                25.0,
                40.0,
                5000.0,
                8.0
        );
        TemperatureSystem system = new TemperatureSystem(
                datacenter,
                options,
                new SimpleServerTemperatureModel()
        );
        SimulationTick tick = tickAtSeconds(1, 60, 60);
        system.update(tick);
        TemperatureSnapshotProvider provider = new TemperatureSnapshotProvider(
                datacenter,
                system,
                options
        );
        TemperatureSnapshot snapshot = provider.snapshot(tick);
        ServerTemperatureSnapshot serverSnapshot = snapshot.servers().getFirst();
        assertEquals(SERVER_CODE, serverSnapshot.serverCode());
        assertEquals(HardwareStatus.OFFLINE, serverSnapshot.status());
        assertEquals(0.0, serverSnapshot.currentPowerWatts(), EPSILON);
        assertEquals(38.56, serverSnapshot.temperatureCelsius(), EPSILON);
        assertTrue(serverSnapshot.temperatureCelsius() < 40.0);
        assertTrue(serverSnapshot.temperatureCelsius() > 25.0);
    }

    @Test
    void distinguishesSameRackCodeAndSlotInDifferentColumns() {
        RackCode rackCode = new RackCode("R01");
        Rack firstRack = new Rack(rackCode, "C01", "R01", List.of("S01"));
        Rack secondRack = new Rack(rackCode, "C02", "R01", List.of("S01"));
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );
        Server firstServer = new Server(new ServerLocation("C01", rackCode, "S01"), config, HardwareStatus.OK);
        Server secondServer = new Server(new ServerLocation("C02", rackCode, "S01"), config, HardwareStatus.OK);
        Datacenter datacenter = new Datacenter(List.of(firstRack, secondRack), List.of(firstServer, secondServer));
        TemperatureSystemOptions options = TemperatureSystemOptions.defaults();
        TemperatureSystem system = new TemperatureSystem(datacenter, options, new SimpleServerTemperatureModel());
        TemperatureSnapshot snapshot = new TemperatureSnapshotProvider(datacenter, system, options)
                .snapshot(tickAtSeconds(1, 0, 1));
        assertEquals("C01-R01-S01", snapshot.servers().getFirst().serverCode());
        assertEquals("C01", snapshot.servers().getFirst().column());
        assertEquals(new RackCode("R01"), snapshot.servers().getFirst().rackCode());
        assertEquals("C02-R01-S01", snapshot.servers().get(1).serverCode());
        assertEquals("C02", snapshot.servers().get(1).column());
        assertEquals(new RackCode("R01"), snapshot.servers().get(1).rackCode());
    }

    private static Datacenter createDatacenterWithOneServer(
            HardwareStatus status,
            float utilization
    ) {
        Rack rack = new Rack(RACK_CODE, new RackLocation("A01", "R01"), 42);
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );
        Server server = new Server(
                new ServerLocation("A01", RACK_CODE, "U01"),
                config,
                status
        );
        server.setUtilization(utilization);
        server.updatePowerConsumption();
        return new Datacenter(List.of(rack), List.of(server));
    }

    private static SimulationTick tickAtSeconds(long index, long elapsedSeconds, long deltaSeconds) {
        return new SimulationTick(
                index,
                Duration.ofSeconds(elapsedSeconds),
                Duration.ofSeconds(deltaSeconds)
        );
    }
}

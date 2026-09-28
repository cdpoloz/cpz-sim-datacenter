package com.cpz.sim.datacenter.input;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RackPowerToServerPowerInputSourceTest {

    private static final RackCode RACK_CODE = new RackCode("RACK-A01-R01");
    private static final RackLocation RACK_LOCATION = new RackLocation("A01", RACK_CODE);

    @Test
    void shouldDistributeRackPowerAcrossOnlineServersUsingDynamicPowerWeight() {
        Datacenter datacenter = datacenter(
                server("U01", config("small", 100.0f, 300.0f), HardwareStatus.OK),
                server("U02", config("large", 100.0f, 500.0f), HardwareStatus.OK)
        );
        RackPowerToServerPowerInputSource source = source(datacenter, 300.0);

        assertEquals(100.0, source.currentPowerWatts(datacenter.getServers().get(0), tick()), 0.0001);
        assertEquals(200.0, source.currentPowerWatts(datacenter.getServers().get(1), tick()), 0.0001);
    }

    @Test
    void shouldIgnoreOfflineServers() {
        Datacenter datacenter = datacenter(
                server("U01", config("online", 100.0f, 300.0f), HardwareStatus.OK),
                server("U02", config("offline", 100.0f, 500.0f), HardwareStatus.OFFLINE)
        );
        RackPowerToServerPowerInputSource source = source(datacenter, 250.0);

        assertEquals(250.0, source.currentPowerWatts(datacenter.getServers().get(0), tick()), 0.0001);
        assertEquals(0.0, source.currentPowerWatts(datacenter.getServers().get(1), tick()), 0.0001);
    }

    @Test
    void shouldReturnZeroForRackWithNoOnlineServers() {
        Datacenter datacenter = datacenter(
                server("U01", config("first", 100.0f, 300.0f), HardwareStatus.OFFLINE),
                server("U02", config("second", 100.0f, 500.0f), HardwareStatus.OFFLINE)
        );
        RackPowerToServerPowerInputSource source = source(datacenter, 250.0);

        assertEquals(0.0, source.currentPowerWatts(datacenter.getServers().get(0), tick()), 0.0001);
        assertEquals(0.0, source.currentPowerWatts(datacenter.getServers().get(1), tick()), 0.0001);
    }

    @Test
    void shouldReturnZeroForEmptyRack() {
        Rack rack = new Rack(RACK_CODE, RACK_LOCATION, List.of("U01", "U02"));
        Datacenter datacenter = new Datacenter(List.of(rack), List.of());
        RackPowerToServerPowerInputSource source = source(datacenter, 250.0);
        Server externalServer =
                server("U01", config("external", 100.0f, 300.0f), HardwareStatus.OK);

        assertEquals(0.0, source.currentPowerWatts(externalServer, tick()), 0.0001);
    }

    @Test
    void shouldConserveRackPowerInNormalCase() {
        Datacenter datacenter = datacenter(
                server("U01", config("first", 100.0f, 300.0f), HardwareStatus.OK),
                server("U02", config("second", 100.0f, 400.0f), HardwareStatus.OK),
                server("U03", config("third", 100.0f, 500.0f), HardwareStatus.OK)
        );
        RackPowerToServerPowerInputSource source = source(datacenter, 450.0);

        double total = datacenter.getServers().stream()
                .mapToDouble(server -> source.currentPowerWatts(server, tick()))
                .sum();

        assertEquals(450.0, total, 0.0001);
    }

    @Test
    void shouldUseUniformFallbackWhenWeightsAreNotUseful() {
        Server first = server("U01", config("first", 100.0f, 300.0f), HardwareStatus.OK);
        Server second = server("U02", config("second", 100.0f, 500.0f), HardwareStatus.OK);
        RackPowerToServerPowerInputSource source = new RackPowerToServerPowerInputSource(
                datacenter(first, second),
                (rackLocation, tick) -> 300.0,
                server -> 0.0
        );

        assertEquals(150.0, source.currentPowerWatts(first, tick()), 0.0001);
        assertEquals(150.0, source.currentPowerWatts(second, tick()), 0.0001);
    }

    @Test
    void shouldBeUsableAsServerPowerInputSource() {
        Datacenter datacenter = datacenter(
                server("U01", config("first", 100.0f, 300.0f), HardwareStatus.OK)
        );
        ServerPowerInputSource source = source(datacenter, 200.0);

        assertInstanceOf(ServerPowerInputSource.class, source);
        assertEquals(200.0, source.currentPowerWatts(datacenter.getServers().getFirst(), tick()), 0.0001);
    }

    @Test
    void shouldRejectInvalidRackPower() {
        Datacenter datacenter = datacenter(
                server("U01", config("first", 100.0f, 300.0f), HardwareStatus.OK)
        );
        RackPowerToServerPowerInputSource source = source(datacenter, Double.NaN);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> source.currentPowerWatts(datacenter.getServers().getFirst(), tick())
        );

        assertEquals("rackPowerWatts must be finite and >= 0", exception.getMessage());
    }

    private static RackPowerToServerPowerInputSource source(Datacenter datacenter, double rackPowerWatts) {
        return new RackPowerToServerPowerInputSource(datacenter, (rackLocation, tick) -> rackPowerWatts);
    }

    private static Datacenter datacenter(Server... servers) {
        Rack rack = new Rack(RACK_CODE, RACK_LOCATION, List.of("U01", "U02", "U03"));
        return new Datacenter(List.of(rack), List.of(servers));
    }

    private static Server server(String slot, ServerConfig config, HardwareStatus status) {
        return new Server(
                new ServerLocation("A01", RACK_CODE, slot),
                config,
                status,
                ServerRole.GENERAL_PURPOSE
        );
    }

    private static ServerConfig config(String modelCode, float idlePowerWatts, float maxPowerWatts) {
        return new ServerConfig(modelCode, "Example", "Server X", idlePowerWatts, maxPowerWatts);
    }

    private static SimulationTick tick() {
        return new SimulationTick(
                1L,
                Duration.ofMinutes(1),
                Duration.ofMinutes(1)
        );
    }

}

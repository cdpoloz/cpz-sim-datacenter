package com.cpz.sim.datacenter.cooling;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerHeatLoadProviderTest {

    private static final ServerConfig SERVER_CONFIG = new ServerConfig("model-01", "Example", "Server X", 100.0f, 300.0f);

    @Test
    void shouldRejectNullDatacenter() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> new ServerHeatLoadProvider(null));
        assertEquals("datacenter must not be null", exception.getMessage());
    }

    @Test
    void shouldCreateHeatLoadsFromCurrentServerPower() {
        Rack rack = createRack();

        Server firstServer = createServer(
                rack,
                "U01",
                HardwareStatus.OK
        );
        firstServer.setUtilization(0.50);
        firstServer.updatePowerConsumption();

        Server secondServer = createServer(
                rack,
                "U02",
                HardwareStatus.OK
        );
        secondServer.setUtilization(1.0);
        secondServer.updatePowerConsumption();

        Datacenter datacenter = new Datacenter(
                List.of(rack),
                List.of(firstServer, secondServer)
        );

        ServerHeatLoadProvider provider =
                new ServerHeatLoadProvider(datacenter);

        List<ServerHeatLoad> heatLoads =
                provider.createHeatLoads();

        assertEquals(2, heatLoads.size());

        ServerHeatLoad firstHeatLoad = heatLoads.get(0);

        assertEquals(
                firstServer.getLocation(),
                firstHeatLoad.serverLocation()
        );
        assertEquals(
                200.0,
                firstHeatLoad.generatedHeatWatts()
        );

        ServerHeatLoad secondHeatLoad = heatLoads.get(1);

        assertEquals(
                secondServer.getLocation(),
                secondHeatLoad.serverLocation()
        );
        assertEquals(
                300.0,
                secondHeatLoad.generatedHeatWatts()
        );
    }

    @Test
    void shouldCreateZeroHeatLoadForOfflineServer() {
        Rack rack = createRack();

        Server offlineServer = createServer(
                rack,
                "U01",
                HardwareStatus.OFFLINE
        );

        Datacenter datacenter = new Datacenter(
                List.of(rack),
                List.of(offlineServer)
        );

        ServerHeatLoadProvider provider =
                new ServerHeatLoadProvider(datacenter);

        List<ServerHeatLoad> heatLoads =
                provider.createHeatLoads();

        assertEquals(1, heatLoads.size());
        assertEquals(
                offlineServer.getLocation(),
                heatLoads.getFirst().serverLocation()
        );
        assertEquals(
                0.0,
                heatLoads.getFirst().generatedHeatWatts()
        );
    }

    @Test
    void shouldReadUpdatedPowerOnEveryInvocation() {
        Rack rack = createRack();

        Server server = createServer(
                rack,
                "U01",
                HardwareStatus.OK
        );

        Datacenter datacenter = new Datacenter(
                List.of(rack),
                List.of(server)
        );

        ServerHeatLoadProvider provider =
                new ServerHeatLoadProvider(datacenter);

        assertEquals(
                100.0,
                provider.createHeatLoads()
                        .getFirst()
                        .generatedHeatWatts()
        );

        server.setUtilization(0.75);
        server.updatePowerConsumption();

        assertEquals(
                250.0,
                provider.createHeatLoads()
                        .getFirst()
                        .generatedHeatWatts()
        );
    }

    private static Rack createRack() {
        return new Rack(
                new RackCode("RACK-A01-R01"),
                new RackLocation("A01", "R01"),
                42
        );
    }

    private static Server createServer(
            Rack rack,
            String slot,
            HardwareStatus status
    ) {
        return new Server(
                new ServerLocation(
                        rack.getColumn(),
                        rack.getCode(),
                        slot
                ),
                SERVER_CONFIG,
                status,
                ServerRole.GENERAL_PURPOSE
        );
    }
}
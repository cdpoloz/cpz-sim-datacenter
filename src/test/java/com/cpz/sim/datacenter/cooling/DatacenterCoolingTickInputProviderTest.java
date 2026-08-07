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
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author CPZ
 */
class DatacenterCoolingTickInputProviderTest {

    private static final double EPSILON = 0.000001;

    @Test
    void rejectsNullDatacenter() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new DatacenterCoolingTickInputProvider(null)
        );

        assertEquals(
                "datacenter must not be null",
                exception.getMessage()
        );
    }

    @Test
    void rejectsNullTick() {
        DatacenterCoolingTickInputProvider provider =
                new DatacenterCoolingTickInputProvider(
                        createDatacenter()
                );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> provider.inputFor(null)
        );

        assertEquals(
                "tick must not be null",
                exception.getMessage()
        );
    }

    @Test
    void createsOneHeatLoadPerServerUsingCurrentPower() {
        Datacenter datacenter = createDatacenter();

        Server firstServer = datacenter.getServers().get(0);
        Server secondServer = datacenter.getServers().get(1);

        firstServer.setUtilization(0.25);
        secondServer.setUtilization(0.75);

        SimulationTick tick = new SimulationTick(
                7L,
                Duration.ofMinutes(35),
                Duration.ofMinutes(5)
        );

        new PowerConsumptionSystem(datacenter).update(tick);

        DatacenterCoolingTickInputProvider provider =
                new DatacenterCoolingTickInputProvider(datacenter);

        CoolingTickInput input = provider.inputFor(tick);

        assertEquals(7L, input.tickIndex());
        assertEquals(2, input.serverHeatLoads().size());

        ServerHeatLoad firstHeatLoad =
                input.serverHeatLoads().get(0);

        assertEquals(
                firstServer.getLocation(),
                firstHeatLoad.serverLocation()
        );

        assertEquals(
                200.0,
                firstHeatLoad.generatedHeatWatts(),
                EPSILON
        );

        ServerHeatLoad secondHeatLoad =
                input.serverHeatLoads().get(1);

        assertEquals(
                secondServer.getLocation(),
                secondHeatLoad.serverLocation()
        );

        assertEquals(
                400.0,
                secondHeatLoad.generatedHeatWatts(),
                EPSILON
        );
    }

    private static Datacenter createDatacenter() {
        RackCode rackCode =
                new RackCode("RACK-A01-R01");

        Rack rack = new Rack(
                rackCode,
                new RackLocation("A01", "R01"),
                42
        );

        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                500.0f
        );

        Server firstServer = new Server(
                new ServerLocation(
                        "A01",
                        rackCode,
                        "U01"
                ),
                config,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );

        Server secondServer = new Server(
                new ServerLocation(
                        "A01",
                        rackCode,
                        "U02"
                ),
                config,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );

        return new Datacenter(
                List.of(rack),
                List.of(firstServer, secondServer)
        );
    }
}
package com.cpz.sim.datacenter.integration;

import com.cpz.sim.datacenter.cooling.*;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.*;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class CoupledLargeFleetPrecisionTest {
    @ParameterizedTest
    @ValueSource(doubles = {0, 5})
    void conservesEnergyWithLargeRoomCapacityAndOneThousandLowPowerServers(double power) {
        var racks = new ArrayList<Rack>();
        var servers = new ArrayList<Server>();
        var slots = java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(i -> "S" + i).toList();
        var model = new ServerConfig("M", "CPZ", "Synthetic", 0, 300,
                new ServerThermalProperties(12000, 12));
        for (int r = 1; r <= 100; r++) {
            String code = "R" + r;
            var location = new RackLocation("C01", code);
            racks.add(new Rack(new RackCode(code), location, slots));
            for (String slot : slots) {
                var server = new Server(new ServerLocation("C01", code, slot), model,
                        HardwareStatus.OK, ServerRole.GENERAL_PURPOSE);
                server.setCurrentPowerWatts(power);
                servers.add(server);
            }
        }
        var dc = new Datacenter(racks, servers);
        var options = new TemperatureSystemOptions(80, 90, 12000, 12);
        var temperatures = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel());
        var equipment = new RackCoolingSystem(new RackCoolingConfiguration(1.204, 1005, List.of(
                new RackCoolingUnitDefinition("RC1", new RackLocation("C01", "R1"), 80, 10000, 50,
                        .3, 4000, 18, 1000, HeatRejectionDestination.ROOM, false))));
        var coupled = new CoupledRoomTemperatureSystem(dc, temperatures, options,
                new RoomAirTemperatureOptions(80, 20_000_000, 80, 0), Optional.empty(), Optional.of(equipment));
        double maximumResidual = 0;
        for (int i = 1; i <= 1440; i++) {
            coupled.update(new SimulationTick(i, Duration.ofMinutes(i), Duration.ofMinutes(1)));
            double residual = Math.abs(coupled.lastSnapshot().orElseThrow().energyBalanceResidualJoules());
            maximumResidual = Math.max(maximumResidual, residual);
            assertTrue(Double.isFinite(coupled.roomAirTemperatureCelsius()));
        }
        assertTrue(maximumResidual < 1e-4, "Large-fleet precision: " + maximumResidual);
    }
}

package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * @author CPZ
 */
public class DatacenterSimulationDemo {

    private DatacenterSimulationDemo() {
    }

    public static void main(String[] args) {
        ServerConfig config = new ServerConfig(
                "model-01",
                "Example",
                "Server X",
                100.0f,
                300.0f
        );
        Rack rack = new Rack(
                new RackCode("RACK-A01-R01"),
                new RackLocation("A01", "R01"),
                List.of("S01", "S02", "SPARE")
        );
        Server serverA = createServer(config, rack.getCode(), "S01");
        Server serverB = createServer(config, rack.getCode(), "S02");
        Datacenter datacenter = new Datacenter(List.of(rack), List.of(serverA, serverB));
        EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(30)));
        engine.register(new WorkloadSystem(datacenter, new ConstantWorkloadSource(0.5f)));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.register(energySystem);
        for (int i = 0; i < 4; i++) {
            SimulationTick tick = engine.step();
            System.out.printf(
                    Locale.US,
                    "Tick %d | Time: %s | Power: %.1f W | Energy: %.3f kWh%n",
                    tick.index(),
                    tick.elapsedTime(),
                    datacenter.getTotalItPowerWatts(),
                    energySystem.getConsumedEnergyKWh()
            );
        }
    }

    private static Server createServer(ServerConfig config, RackCode rackCode, String slot) {
        return new Server(
                new ServerLocation("A01", rackCode, slot),
                config,
                HardwareStatus.OK,
                ServerRole.GENERAL_PURPOSE
        );
    }

}

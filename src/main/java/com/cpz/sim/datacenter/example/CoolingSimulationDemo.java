package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSnapshotCoordinator;
import com.cpz.sim.datacenter.cooling.DatacenterCoolingTickInputProvider;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingUnitSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.system.CoolingSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.sim.foundation.time.SimulationTick;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Locale;

/**
 * Demonstrates the causal integration between server workload, electrical
 * power, cooling-zone behavior and server temperature.
 *
 * <p>The simulation advances manually so that cooling units can be enabled or
 * disabled between ticks and their thermal effects can be inspected.</p>
 *
 * @author CPZ
 */
public final class CoolingSimulationDemo {

    private static final Duration TICK_DURATION = Duration.ofMinutes(1);
    private static final double SERVER_UTILIZATION = 0.75;

    private CoolingSimulationDemo() {
    }

    public static void main(String[] args) throws IOException {
        Datacenter datacenter = CoolingSimulationDemoScenario.createDatacenter();
        CoolingConfiguration coolingConfiguration = CoolingSimulationDemoScenario.createCoolingConfiguration(datacenter);
        WorkloadSystem workloadSystem = new WorkloadSystem(datacenter, new ConstantWorkloadSource(SERVER_UTILIZATION));
        PowerConsumptionSystem powerSystem = new PowerConsumptionSystem(datacenter);
        CoolingSystem coolingSystem = new CoolingSystem(coolingConfiguration);
        CoolingSnapshotTemperatureReferenceProvider temperatureReferenceProvider = new CoolingSnapshotTemperatureReferenceProvider(coolingConfiguration);
        CoolingSnapshotCoordinator coolingCoordinator = new CoolingSnapshotCoordinator(new DatacenterCoolingTickInputProvider(datacenter), coolingSystem, temperatureReferenceProvider);
        TemperatureSystem temperatureSystem =
                new TemperatureSystem(
                        datacenter,
                        new TemperatureSystemOptions(
                                24.0,
                                25.0,
                                5_000.0,
                                8.0
                        ),
                        new SimpleServerTemperatureModel(),
                        temperatureReferenceProvider
                );
        SimulationEngine engine = new SimulationEngine(new SimulationClock(TICK_DURATION));
        /*
         * Cooling and temperature are executed explicitly because the cooling
         * snapshot coordinator must run between power and temperature.
         */
        engine.register(workloadSystem);
        engine.register(powerSystem);
        printHeader();
        printUnitStates(coolingSystem);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            runInteractiveLoop(reader, engine, datacenter, coolingSystem, coolingCoordinator, temperatureSystem);
        }
        System.out.println("Cooling simulation demo finished.");
    }

    private static void runInteractiveLoop(
            BufferedReader reader,
            SimulationEngine engine,
            Datacenter datacenter,
            CoolingSystem coolingSystem,
            CoolingSnapshotCoordinator coolingCoordinator,
            TemperatureSystem temperatureSystem
    ) throws IOException {
        while (true) {
            System.out.print("Command [Enter=next, S=supply, E=exhaust, Q=quit]: ");
            String line = reader.readLine();
            if (line == null) {
                System.out.println();
                return;
            }
            String command = line.trim().toUpperCase(Locale.ROOT);
            switch (command) {
                case "" -> executeNextTick(engine, datacenter, coolingCoordinator, temperatureSystem);
                case "S" -> {
                    toggleUnit(coolingSystem, CoolingSimulationDemoScenario.SUPPLY_UNIT_CODE);
                    printUnitStates(coolingSystem);
                }
                case "E" -> {
                    toggleUnit(coolingSystem, CoolingSimulationDemoScenario.EXHAUST_UNIT_CODE);
                    printUnitStates(coolingSystem);
                }
                case "Q" -> {
                    return;
                }
                default -> System.out.printf("Unknown command: %s%n", line);
            }
            System.out.println();
        }
    }

    private static void executeNextTick(SimulationEngine engine, Datacenter datacenter, CoolingSnapshotCoordinator coolingCoordinator, TemperatureSystem temperatureSystem) {
        /*
         * engine.step():
         * WorkloadSystem -> PowerConsumptionSystem
         */
        SimulationTick tick = engine.step();
        /*
         * DatacenterCoolingTickInputProvider -> CoolingSystem
         * -> CoolingSnapshotTemperatureReferenceProvider
         */
        CoolingSnapshot coolingSnapshot = coolingCoordinator.update(tick);
        /*
         * TemperatureSystem consumes the cooling snapshot generated for the
         * same simulation tick.
         */
        temperatureSystem.update(tick);
        printTick(tick, datacenter, coolingSnapshot, temperatureSystem);
    }

    private static void toggleUnit(CoolingSystem coolingSystem, String unitCode) {
        if (coolingSystem.isEnabled(unitCode)) coolingSystem.disable(unitCode);
        else coolingSystem.enable(unitCode);
        System.out.printf("%s is now %s.%n", unitCode, coolingSystem.isEnabled(unitCode) ? "enabled" : "disabled");
    }

    private static void printHeader() {
        System.out.println("Cooling simulation demo");
        System.out.println("Causal order: workload -> power -> cooling -> temperature");
        System.out.printf(
                Locale.US,
                "Tick duration: %d s | Server utilization: %.0f%%%n%n",
                TICK_DURATION.toSeconds(),
                SERVER_UTILIZATION * 100.0
        );
        System.out.println("Controls:");
        System.out.println("  Enter - execute the next simulation tick");
        System.out.println("  S     - toggle SUPPLY-01");
        System.out.println("  E     - toggle EXHAUST-01");
        System.out.println("  Q     - quit");
        System.out.println();
    }

    private static void printUnitStates(CoolingSystem coolingSystem) {
        printUnitState(coolingSystem, CoolingSimulationDemoScenario.SUPPLY_UNIT_CODE);
        printUnitState(coolingSystem, CoolingSimulationDemoScenario.EXHAUST_UNIT_CODE);
    }

    private static void printUnitState(CoolingSystem coolingSystem, String unitCode) {
        System.out.printf("  %-10s: %s%n", unitCode, coolingSystem.isEnabled(unitCode) ? "ENABLED" : "DISABLED");
    }

    private static void printTick(SimulationTick tick, Datacenter datacenter, CoolingSnapshot coolingSnapshot, TemperatureSystem temperatureSystem) {
        System.out.printf(
                Locale.US,
                "Tick %d | Time: %.0f s | IT power: %.1f W%n",
                tick.index(),
                tick.elapsedSeconds(),
                datacenter.getTotalItPowerWatts()
        );
        printCoolingUnits(coolingSnapshot);
        printCoolingZones(coolingSnapshot);
        printServers(datacenter, temperatureSystem);
    }

    private static void printCoolingUnits(CoolingSnapshot snapshot) {
        for (CoolingUnitSnapshot unit : snapshot.units()) {
            System.out.printf(
                    Locale.US,
                    "  Unit %-10s | type=%-7s | enabled=%-5s | airflow=%.2f m³/s | cooling=%.1f W%n",
                    unit.unitCode(),
                    unit.type(),
                    unit.enabled(),
                    unit.currentAirflowCubicMetersPerSecond(),
                    unit.currentCoolingPowerWatts()
            );
        }
    }

    private static void printCoolingZones(CoolingSnapshot snapshot) {
        for (CoolingZoneSnapshot zone : snapshot.zones()) {
            System.out.printf(
                    Locale.US,
                    "  Zone %-10s | heat=%.1f W | capacity=%.1f W | deficit=%.1f W | inlet=%.2f °C | exhaust=%.2f °C | recirculation=%.1f%%%n",
                    zone.zoneCode(),
                    zone.generatedHeatWatts(),
                    zone.availableCoolingCapacityWatts(),
                    zone.coolingDeficitWatts(),
                    zone.inletAirTemperatureCelsius(),
                    zone.exhaustAirTemperatureCelsius(),
                    zone.recirculationFraction() * 100.0
            );
        }
    }

    private static void printServers(Datacenter datacenter, TemperatureSystem temperatureSystem) {
        for (Server server : datacenter.getServers()) {
            System.out.printf(
                    Locale.US,
                    "  Server %-18s | role=%-15s | utilization=%.2f | power=%.1f W | temperature=%.2f °C%n",
                    server.getCode(),
                    server.getRole(),
                    server.getUtilization(),
                    server.getCurrentPowerWatts(),
                    temperatureSystem.getThermalState(server.getCode()).getTemperatureCelsius()
            );
        }
    }
}
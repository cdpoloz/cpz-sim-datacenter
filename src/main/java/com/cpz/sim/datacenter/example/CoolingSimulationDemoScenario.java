package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSystemOptions;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.ExhaustCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ServerRole;

import java.util.List;
import java.util.Set;

/**
 * Creates the fixed datacenter and cooling configuration used by
 * {@link CoolingSimulationDemo}.
 *
 * @author CPZ
 */
final class CoolingSimulationDemoScenario {

    static final String ZONE_CODE = "ZONE-01";
    static final String SUPPLY_UNIT_CODE = "SUPPLY-01";
    static final String EXHAUST_UNIT_CODE = "EXHAUST-01";
    private static final String COLUMN = "A01";
    private static final RackCode RACK_CODE = new RackCode("RACK-A01-R01");

    private CoolingSimulationDemoScenario() {
    }

    static Datacenter createDatacenter() {
        Rack rack = new Rack(RACK_CODE, new RackLocation(COLUMN, "R01"), 42);
        ServerConfig serverConfig = new ServerConfig(
                "SRV-COOLING-DEMO-001",
                "CPZ",
                "Cooling Demo Server",
                100.0f,
                500.0f
        );
        Server firstServer = createServer("U01", serverConfig, ServerRole.GENERAL_PURPOSE);
        Server secondServer = createServer("U02", serverConfig, ServerRole.AI);
        return new Datacenter(List.of(rack), List.of(firstServer, secondServer));
    }

    static CoolingConfiguration createCoolingConfiguration(Datacenter datacenter) {
        Set<ServerLocation> serverLocations =
                datacenter
                        .getServers()
                        .stream()
                        .map(Server::getLocation)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        CoolingZoneDefinition zone = new CoolingZoneDefinition(ZONE_CODE, serverLocations);
        SupplyCoolingUnitDefinition supply =
                new SupplyCoolingUnitDefinition(
                        SUPPLY_UNIT_CODE,
                        4.0,
                        12_000.0,
                        18.0,
                        List.of(new CoolingZoneInfluence(ZONE_CODE, 1.0)),
                        true
                );
        ExhaustCoolingUnitDefinition exhaust =
                new ExhaustCoolingUnitDefinition(
                        EXHAUST_UNIT_CODE,
                        4.0,
                        List.of(new CoolingZoneInfluence(ZONE_CODE, 1.0)),
                        true
                );
        return new CoolingConfiguration(List.of(zone), List.of(supply, exhaust), CoolingSystemOptions.defaults());
    }

    private static Server createServer(String slot, ServerConfig config, ServerRole role) {
        return new Server(new ServerLocation(COLUMN, RACK_CODE, slot), config, HardwareStatus.OK, role);
    }
}
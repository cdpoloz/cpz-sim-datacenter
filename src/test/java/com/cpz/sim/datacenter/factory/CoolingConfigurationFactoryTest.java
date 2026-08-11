package com.cpz.sim.datacenter.factory;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.ServerRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import com.cpz.sim.datacenter.config.definition.CoolingConfigDefinition;
import com.cpz.sim.datacenter.config.definition.CoolingSystemOptionsDefinition;
import com.cpz.sim.datacenter.config.definition.CoolingZoneConfigDefinition;
import com.cpz.sim.datacenter.config.definition.CoolingZoneInfluenceConfigDefinition;
import com.cpz.sim.datacenter.config.definition.ExhaustCoolingUnitConfigDefinition;
import com.cpz.sim.datacenter.config.definition.SupplyCoolingUnitConfigDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.ExhaustCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author CPZ
 */
class CoolingConfigurationFactoryTest {

    private static DatacenterDefinition definitionWithoutCooling() {
        return new DatacenterDefinition(
                "Demo Datacenter",
                new DatacenterLayoutDefinition(
                        List.of(
                                new RackDefinition(
                                        "R01",
                                        "C01",
                                        "R01",
                                        List.of("S01")
                                )
                        )
                ),
                List.of(
                        new ServerModelDefinition(
                                "SRV-DEMO-001",
                                "CPZ",
                                "Demo Server",
                                100.0f,
                                300.0f
                        )
                ),
                List.of(
                        new ServerDefinition(
                                "C01",
                                "R01",
                                "S01",
                                "SRV-DEMO-001",
                                "OK",
                                ServerRole.GENERAL_PURPOSE,
                                1.0f
                        )
                )
        );
    }

    private static Datacenter createDatacenter(
            DatacenterDefinition definition
    ) {
        return new DatacenterFactory().create(definition);
    }

    @Test
    void shouldRejectNullValidator() {
        assertThrows(
                NullPointerException.class,
                () -> new CoolingConfigurationFactory(null)
        );
    }

    @Test
    void shouldRejectNullDefinition() {
        DatacenterDefinition definition =
                definitionWithoutCooling();

        Datacenter datacenter =
                createDatacenter(definition);

        CoolingConfigurationFactory factory =
                new CoolingConfigurationFactory();

        assertThrows(
                NullPointerException.class,
                () -> factory.create(null, datacenter)
        );
    }

    @Test
    void shouldRejectNullDatacenter() {
        DatacenterDefinition definition =
                definitionWithoutCooling();

        CoolingConfigurationFactory factory =
                new CoolingConfigurationFactory();

        assertThrows(
                NullPointerException.class,
                () -> factory.create(definition, null)
        );
    }

    @Test
    void shouldReturnEmptyWhenCoolingIsNotConfigured() {
        DatacenterDefinition definition =
                definitionWithoutCooling();

        Datacenter datacenter =
                createDatacenter(definition);

        CoolingConfigurationFactory factory =
                new CoolingConfigurationFactory();

        Optional<CoolingConfiguration> result =
                factory.create(definition, datacenter);

        assertTrue(result.isEmpty());
    }

    private static CoolingConfigDefinition minimalCoolingDefinition() {
        CoolingZoneInfluenceConfigDefinition influence =
                new CoolingZoneInfluenceConfigDefinition(
                        "ZONE-01",
                        1.0
                );

        SupplyCoolingUnitConfigDefinition supply =
                new SupplyCoolingUnitConfigDefinition(
                        "SUPPLY-01",
                        8.0,
                        100_000.0,
                        18.0,
                        List.of(influence),
                        true
                );

        ExhaustCoolingUnitConfigDefinition exhaust =
                new ExhaustCoolingUnitConfigDefinition(
                        "EXHAUST-01",
                        6.0,
                        List.of(influence),
                        false
                );

        return new CoolingConfigDefinition(
                List.of(
                        new CoolingZoneConfigDefinition(
                                "ZONE-01",
                                List.of("C01"),
                                List.of("R01")
                        )
                ),
                List.of(supply),
                List.of(exhaust),
                new CoolingSystemOptionsDefinition(
                        1.2,
                        1_000.0,
                        22.0,
                        0.8
                )
        );
    }

    private static DatacenterDefinition definitionWithCooling() {
        DatacenterDefinition base = definitionWithoutCooling();

        return new DatacenterDefinition(
                base.name(),
                base.layout(),
                base.serverModels(),
                base.servers(),
                base.temperature(),
                base.health(),
                minimalCoolingDefinition()
        );
    }

    @Test
    void shouldCreateMinimalCoolingConfiguration() {
        DatacenterDefinition definition =
                definitionWithCooling();

        Datacenter datacenter =
                createDatacenter(definition);

        CoolingConfigurationFactory factory =
                new CoolingConfigurationFactory();

        CoolingConfiguration configuration =
                factory.create(definition, datacenter)
                        .orElseThrow();

        assertEquals(1, configuration.zones().size());
        assertEquals(2, configuration.units().size());

        assertEquals(
                "ZONE-01",
                configuration.zones().getFirst().code()
        );

        assertEquals(
                1,
                configuration.zones()
                        .getFirst()
                        .serverLocations()
                        .size()
        );

        SupplyCoolingUnitDefinition supply =
                assertInstanceOf(
                        SupplyCoolingUnitDefinition.class,
                        configuration.units().get(0)
                );

        assertEquals("SUPPLY-01", supply.code());
        assertEquals(
                8.0,
                supply.ratedAirflowCubicMetersPerSecond()
        );
        assertEquals(
                100_000.0,
                supply.ratedCoolingCapacityWatts()
        );
        assertEquals(
                18.0,
                supply.supplyAirTemperatureCelsius()
        );
        assertTrue(supply.initiallyEnabled());

        assertEquals(
                List.of(
                        new CoolingZoneInfluence(
                                "ZONE-01",
                                1.0
                        )
                ),
                supply.influences()
        );

        ExhaustCoolingUnitDefinition exhaust =
                assertInstanceOf(
                        ExhaustCoolingUnitDefinition.class,
                        configuration.units().get(1)
                );

        assertEquals("EXHAUST-01", exhaust.code());
        assertEquals(
                6.0,
                exhaust.ratedAirflowCubicMetersPerSecond()
        );
        assertFalse(exhaust.initiallyEnabled());

        assertEquals(
                List.of(
                        new CoolingZoneInfluence(
                                "ZONE-01",
                                1.0
                        )
                ),
                exhaust.influences()
        );

        assertEquals(
                1.2,
                configuration.options()
                        .airDensityKilogramsPerCubicMeter()
        );
        assertEquals(
                1_000.0,
                configuration.options()
                        .airSpecificHeatJoulesPerKilogramKelvin()
        );
        assertEquals(
                22.0,
                configuration.options()
                        .initialInletAirTemperatureCelsius()
        );
        assertEquals(
                0.8,
                configuration.options()
                        .maximumRecirculationFraction()
        );
    }
}
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
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.NoiseSource;
import com.cpz.utils.noise.PerlinNoise;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseRackPowerInputSourceTest {

    private static final RackCode FIRST_RACK_CODE = new RackCode("RACK-A01-R01");
    private static final RackCode SECOND_RACK_CODE = new RackCode("RACK-A01-R02");
    private static final RackLocation FIRST_RACK_LOCATION = new RackLocation("A01", FIRST_RACK_CODE);
    private static final RackLocation SECOND_RACK_LOCATION = new RackLocation("A01", SECOND_RACK_CODE);

    @Test
    void shouldReturnZeroForRackWithNoOnlineServers() {
        Datacenter datacenter = datacenter(
                server(FIRST_RACK_CODE, "U01", 100.0f, 300.0f, HardwareStatus.OFFLINE, ServerRole.AI)
        );
        NoiseRackPowerInputSource source = source(datacenter, constantNoise(0.5f));

        assertEquals(0.0, source.currentPowerWatts(FIRST_RACK_LOCATION, tick()), 0.0001);
    }

    @Test
    void shouldReturnRackPowerBetweenAggregatedIdleAndMaxPower() {
        Datacenter datacenter = datacenter(
                server(FIRST_RACK_CODE, "U01", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.GENERAL_PURPOSE),
                server(FIRST_RACK_CODE, "U02", 120.0f, 420.0f, HardwareStatus.OK, ServerRole.GENERAL_PURPOSE)
        );
        NoiseRackPowerInputSource source = source(datacenter, constantNoise(0.5f));

        double powerWatts = source.currentPowerWatts(FIRST_RACK_LOCATION, tick());

        assertTrue(powerWatts >= 220.0);
        assertTrue(powerWatts <= 720.0);
    }

    @Test
    void shouldProduceSameSequenceWithSameSeed() {
        Datacenter datacenter = datacenter(
                server(FIRST_RACK_CODE, "U01", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.DATABASE)
        );
        NoiseRackPowerInputSource sourceA = source(datacenter, fractalNoise(1234L));
        NoiseRackPowerInputSource sourceB = source(datacenter, fractalNoise(1234L));

        for (int i = 0; i < 10; i++) {
            SimulationTick tick = tickAtSeconds(i + 1, i * 60L);
            assertEquals(
                    sourceA.currentPowerWatts(FIRST_RACK_LOCATION, tick),
                    sourceB.currentPowerWatts(FIRST_RACK_LOCATION, tick),
                    0.0001
            );
        }
    }

    @Test
    void shouldAllowDifferentRacksToReturnDifferentValues() {
        Datacenter datacenter = datacenter(
                server(FIRST_RACK_CODE, "U01", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.GENERAL_PURPOSE),
                server(SECOND_RACK_CODE, "U01", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.GENERAL_PURPOSE)
        );
        NoiseRackPowerInputSource source = source(datacenter, position -> position / 1_000.0f);

        assertNotEquals(
                source.currentPowerWatts(FIRST_RACK_LOCATION, tick()),
                source.currentPowerWatts(SECOND_RACK_LOCATION, tick())
        );
    }

    @Test
    void shouldIgnoreOfflineServersWhenAggregatingPowerRange() {
        Datacenter datacenter = datacenter(
                server(FIRST_RACK_CODE, "U01", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.GENERAL_PURPOSE),
                server(FIRST_RACK_CODE, "U02", 1_000.0f, 2_000.0f, HardwareStatus.OFFLINE, ServerRole.AI)
        );
        NoiseRackPowerInputSource source = source(datacenter, constantNoise(1.0f));

        assertEquals(260.0, source.currentPowerWatts(FIRST_RACK_LOCATION, tick()), 0.0001);
    }

    @Test
    void shouldUseHigherActivityRangeForAiGpuRackThanManagementRack() {
        Datacenter aiDatacenter = datacenter(
                server(FIRST_RACK_CODE, "U01", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.AI),
                server(FIRST_RACK_CODE, "U02", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.GPU)
        );
        Datacenter managementDatacenter = datacenter(
                server(FIRST_RACK_CODE, "U01", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.MANAGEMENT),
                server(FIRST_RACK_CODE, "U02", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.MANAGEMENT)
        );
        NoiseRackPowerInputSource aiSource = source(aiDatacenter, constantNoise(0.0f));
        NoiseRackPowerInputSource managementSource = source(managementDatacenter, constantNoise(0.0f));

        assertTrue(
                aiSource.currentPowerWatts(FIRST_RACK_LOCATION, tick())
                        > managementSource.currentPowerWatts(FIRST_RACK_LOCATION, tick())
        );
    }

    @Test
    void shouldCalculateDominantRoleFromOnlineServersOnly() {
        Datacenter datacenter = datacenter(
                server(FIRST_RACK_CODE, "U01", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.MANAGEMENT),
                server(FIRST_RACK_CODE, "U02", 100.0f, 300.0f, HardwareStatus.OFFLINE, ServerRole.AI),
                server(FIRST_RACK_CODE, "U03", 100.0f, 300.0f, HardwareStatus.OFFLINE, ServerRole.AI)
        );
        NoiseRackPowerInputSource source = source(datacenter, constantNoise(0.0f));

        assertEquals(130.0, source.currentPowerWatts(FIRST_RACK_LOCATION, tick()), 0.0001);
    }

    @Test
    void shouldResolveDominantRoleTieDeterministicallyByHigherMinimumActivity() {
        Datacenter datacenter = datacenter(
                server(FIRST_RACK_CODE, "U01", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.GENERAL_PURPOSE),
                server(FIRST_RACK_CODE, "U02", 100.0f, 300.0f, HardwareStatus.OK, ServerRole.DATABASE)
        );
        NoiseRackPowerInputSource source = source(datacenter, constantNoise(0.0f));

        assertEquals(380.0, source.currentPowerWatts(FIRST_RACK_LOCATION, tick()), 0.0001);
    }

    private static NoiseRackPowerInputSource source(Datacenter datacenter, NoiseSource noiseSource) {
        return new NoiseRackPowerInputSource(datacenter, noiseSource, 0.001);
    }

    private static Datacenter datacenter(Server... servers) {
        Rack firstRack = new Rack(FIRST_RACK_CODE, FIRST_RACK_LOCATION, List.of("U01", "U02", "U03"));
        Rack secondRack = new Rack(SECOND_RACK_CODE, SECOND_RACK_LOCATION, List.of("U01", "U02", "U03"));
        return new Datacenter(List.of(firstRack, secondRack), List.of(servers));
    }

    private static Server server(
            RackCode rackCode,
            String slot,
            float idlePowerWatts,
            float maxPowerWatts,
            HardwareStatus status,
            ServerRole role
    ) {
        return new Server(
                new ServerLocation("A01", rackCode, slot),
                new ServerConfig(
                        "model-" + rackCode.value() + "-" + slot,
                        "Example",
                        "Server X",
                        idlePowerWatts,
                        maxPowerWatts
                ),
                status,
                role
        );
    }

    private static NoiseSource constantNoise(float value) {
        return position -> value;
    }

    private static NoiseSource fractalNoise(long seed) {
        return new FractalNoise(
                new PerlinNoise(seed),
                5,
                1.0f,
                2.0f,
                0.5f
        );
    }

    private static SimulationTick tick() {
        return tickAtSeconds(1L, 60L);
    }

    private static SimulationTick tickAtSeconds(long index, long elapsedSeconds) {
        return new SimulationTick(index, Duration.ofSeconds(elapsedSeconds), Duration.ofSeconds(60));
    }
}

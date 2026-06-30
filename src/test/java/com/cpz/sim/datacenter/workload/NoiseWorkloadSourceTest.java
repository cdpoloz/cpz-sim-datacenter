package com.cpz.sim.datacenter.workload;

import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.foundation.time.SimulationTick;
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.NoiseSource;
import com.cpz.utils.noise.PerlinNoise;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author CPZ
 */
class NoiseWorkloadSourceTest {

    private static NoiseSource constantNoise(float value) {
        return position -> value;
    }

    private static Server serverAt(String rackCode, String slot) {
        ServerConfig config = new ServerConfig(
                "TEST-SERVER",
                "CPZ",
                "Test Server",
                100.0f,
                300.0f
        );
        return new Server(new ServerLocation(new RackCode(rackCode), slot), config, HardwareStatus.OK);
    }

    private static SimulationTick tickAtSeconds(long index, long elapsedSeconds) {
        return new SimulationTick(index, Duration.ofSeconds(elapsedSeconds), Duration.ofSeconds(60));
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

    @Test
    void shouldRejectNullNoiseSource() {
        assertThrows(
                NullPointerException.class,
                () -> new NoiseWorkloadSource(null, 0.001, 0.0f, 1.0f)
        );
    }

    @Test
    void shouldRejectInvalidSpeed() {
        NoiseSource noiseSource = constantNoise(0.5f);
        assertThrows(
                IllegalArgumentException.class,
                () -> new NoiseWorkloadSource(noiseSource, -0.001, 0.0f, 1.0f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new NoiseWorkloadSource(noiseSource, Double.NaN, 0.0f, 1.0f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new NoiseWorkloadSource(noiseSource, Double.POSITIVE_INFINITY, 0.0f, 1.0f)
        );
    }

    @Test
    void shouldRejectInvalidMinimumUtilization() {
        NoiseSource noiseSource = constantNoise(0.5f);
        assertThrows(
                IllegalArgumentException.class,
                () -> new NoiseWorkloadSource(noiseSource, 0.001, -0.1f, 1.0f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new NoiseWorkloadSource(noiseSource, 0.001, 1.1f, 1.0f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new NoiseWorkloadSource(noiseSource, 0.001, Float.NaN, 1.0f)
        );
    }

    @Test
    void shouldRejectInvalidMaximumUtilization() {
        NoiseSource noiseSource = constantNoise(0.5f);
        assertThrows(
                IllegalArgumentException.class,
                () -> new NoiseWorkloadSource(noiseSource, 0.001, 0.0f, -0.1f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new NoiseWorkloadSource(noiseSource, 0.001, 0.0f, 1.1f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new NoiseWorkloadSource(noiseSource, 0.001, 0.0f, Float.NaN)
        );
    }

    @Test
    void shouldRejectMinimumUtilizationGreaterThanMaximumUtilization() {
        NoiseSource noiseSource = constantNoise(0.5f);
        assertThrows(
                IllegalArgumentException.class,
                () -> new NoiseWorkloadSource(noiseSource, 0.001, 0.8f, 0.2f)
        );
    }

    @Test
    void shouldReturnUtilizationWithinConfiguredRange() {
        NoiseSource noiseSource = constantNoise(0.5f);
        NoiseWorkloadSource workloadSource = new NoiseWorkloadSource(
                noiseSource,
                0.001,
                0.2f,
                0.8f
        );
        float utilization = workloadSource.getUtilization(
                serverAt("RACK-A01-R01", "U01"),
                tickAtSeconds(1, 60)
        );
        assertEquals(0.5f, utilization, 0.000001f);
    }

    @Test
    void shouldClampNoiseValuesBelowZero() {
        NoiseSource noiseSource = constantNoise(-10.0f);
        NoiseWorkloadSource workloadSource = new NoiseWorkloadSource(
                noiseSource,
                0.001,
                0.2f,
                0.8f
        );
        float utilization = workloadSource.getUtilization(
                serverAt("RACK-A01-R01", "U01"),
                tickAtSeconds(1, 60)
        );
        assertEquals(0.2f, utilization, 0.000001f);
    }

    @Test
    void shouldClampNoiseValuesAboveOne() {
        NoiseSource noiseSource = constantNoise(10.0f);
        NoiseWorkloadSource workloadSource = new NoiseWorkloadSource(
                noiseSource,
                0.001,
                0.2f,
                0.8f
        );
        float utilization = workloadSource.getUtilization(
                serverAt("RACK-A01-R01", "U01"),
                tickAtSeconds(1, 60)
        );
        assertEquals(0.8f, utilization, 0.000001f);
    }

    @Test
    void shouldSendDifferentNoisePositionsForDifferentServers() {
        List<Float> receivedPositions = new ArrayList<>();
        NoiseSource recordingNoiseSource = position -> {
            receivedPositions.add(position);
            return 0.5f;
        };
        NoiseWorkloadSource workloadSource = new NoiseWorkloadSource(
                recordingNoiseSource,
                0.001,
                0.0f,
                1.0f
        );
        SimulationTick tick = tickAtSeconds(1, 60);
        workloadSource.getUtilization(
                serverAt("RACK-A01-R01", "U01"),
                tick
        );
        workloadSource.getUtilization(
                serverAt("RACK-A01-R01", "U02"),
                tick
        );
        assertEquals(2, receivedPositions.size());
        assertNotEquals(receivedPositions.get(0), receivedPositions.get(1));
    }

    @Test
    void shouldProduceSameSequenceWithSameSeed() {
        Server server = serverAt("RACK-A01-R01", "U01");
        NoiseWorkloadSource sourceA = new NoiseWorkloadSource(
                fractalNoise(1234L),
                0.001,
                0.2f,
                0.9f
        );
        NoiseWorkloadSource sourceB = new NoiseWorkloadSource(
                fractalNoise(1234L),
                0.001,
                0.2f,
                0.9f
        );
        for (int i = 0; i < 10; i++) {
            SimulationTick tick = tickAtSeconds(i + 1, i * 60L);
            float utilizationA = sourceA.getUtilization(server, tick);
            float utilizationB = sourceB.getUtilization(server, tick);
            assertEquals(utilizationA, utilizationB, 0.000001f);
        }
    }

    @Test
    void shouldProduceDifferentSequenceWithDifferentSeeds() {
        Server server = serverAt("RACK-A01-R01", "U01");
        NoiseWorkloadSource sourceA = new NoiseWorkloadSource(
                fractalNoise(1234L),
                0.001,
                0.2f,
                0.9f
        );
        NoiseWorkloadSource sourceB = new NoiseWorkloadSource(
                fractalNoise(5678L),
                0.001,
                0.2f,
                0.9f
        );
        boolean foundDifference = false;
        for (int i = 0; i < 10; i++) {
            SimulationTick tick = tickAtSeconds(i + 1, i * 60L);
            float utilizationA = sourceA.getUtilization(server, tick);
            float utilizationB = sourceB.getUtilization(server, tick);
            if (Math.abs(utilizationA - utilizationB) > 0.000001f) {
                foundDifference = true;
                break;
            }
        }
        assertTrue(foundDifference);
    }

    @Test
    void shouldRejectNullServer() {
        NoiseWorkloadSource workloadSource = new NoiseWorkloadSource(
                constantNoise(0.5f),
                0.001,
                0.0f,
                1.0f
        );
        assertThrows(
                NullPointerException.class,
                () -> workloadSource.getUtilization(null, tickAtSeconds(1, 60))
        );
    }

    @Test
    void shouldRejectNullTick() {
        NoiseWorkloadSource workloadSource = new NoiseWorkloadSource(
                constantNoise(0.5f),
                0.001,
                0.0f,
                1.0f
        );
        Server server = serverAt("RACK-A01-R01", "U01");
        assertThrows(
                NullPointerException.class,
                () -> workloadSource.getUtilization(server, null)
        );
    }
}

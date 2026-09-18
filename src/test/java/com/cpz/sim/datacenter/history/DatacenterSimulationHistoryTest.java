package com.cpz.sim.datacenter.history;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatacenterSimulationHistoryTest {

    @Test
    void shouldRecordSnapshotsInTickOrder() {
        DatacenterSimulationHistory history = new DatacenterSimulationHistory();
        DatacenterSimulationStepSnapshot first = DatacenterSimulationStepSnapshotTest.stepSnapshot(1L);
        DatacenterSimulationStepSnapshot second = DatacenterSimulationStepSnapshotTest.stepSnapshot(2L);

        history.record(first);
        history.record(second);

        assertEquals(2, history.size());
        assertEquals(List.of(first, second), history.snapshots());
        assertEquals(second, history.latest().orElseThrow());
        assertEquals(first, history.find(1L).orElseThrow());
    }

    @Test
    void shouldReturnLatestSnapshotsInChronologicalOrder() {
        DatacenterSimulationHistory history = new DatacenterSimulationHistory();
        DatacenterSimulationStepSnapshot first = DatacenterSimulationStepSnapshotTest.stepSnapshot(1L);
        DatacenterSimulationStepSnapshot second = DatacenterSimulationStepSnapshotTest.stepSnapshot(2L);
        DatacenterSimulationStepSnapshot third = DatacenterSimulationStepSnapshotTest.stepSnapshot(3L);
        history.record(first);
        history.record(second);
        history.record(third);

        assertEquals(List.of(second, third), history.latest(2));
        assertEquals(List.of(first, second, third), history.latest(10));
        assertEquals(List.of(), history.latest(0));
    }

    @Test
    void shouldReturnSnapshotsBetweenInclusiveTickIndexes() {
        DatacenterSimulationHistory history = new DatacenterSimulationHistory();
        DatacenterSimulationStepSnapshot first = DatacenterSimulationStepSnapshotTest.stepSnapshot(1L);
        DatacenterSimulationStepSnapshot second = DatacenterSimulationStepSnapshotTest.stepSnapshot(2L);
        DatacenterSimulationStepSnapshot third = DatacenterSimulationStepSnapshotTest.stepSnapshot(3L);
        history.record(first);
        history.record(second);
        history.record(third);

        assertEquals(List.of(second, third), history.between(2L, 3L));
    }

    @Test
    void shouldRejectDuplicateTickIndex() {
        DatacenterSimulationHistory history = new DatacenterSimulationHistory();
        history.record(DatacenterSimulationStepSnapshotTest.stepSnapshot(1L));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> history.record(DatacenterSimulationStepSnapshotTest.stepSnapshot(1L))
                );

        assertEquals("A snapshot for tickIndex already exists: 1", exception.getMessage());
    }

    @Test
    void shouldRejectOlderTickIndex() {
        DatacenterSimulationHistory history = new DatacenterSimulationHistory();
        history.record(DatacenterSimulationStepSnapshotTest.stepSnapshot(2L));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> history.record(DatacenterSimulationStepSnapshotTest.stepSnapshot(1L))
                );

        assertEquals("snapshot tickIndex must be greater than the latest recorded tickIndex", exception.getMessage());
    }

    @Test
    void shouldClearRecordedSnapshots() {
        DatacenterSimulationHistory history = new DatacenterSimulationHistory();
        history.record(DatacenterSimulationStepSnapshotTest.stepSnapshot(1L));

        history.clear();

        assertTrue(history.isEmpty());
        assertTrue(history.latest().isEmpty());
    }

    @Test
    void shouldRejectInvalidWindows() {
        DatacenterSimulationHistory history = new DatacenterSimulationHistory();

        assertThrows(IllegalArgumentException.class, () -> history.latest(-1));
        assertThrows(IllegalArgumentException.class, () -> history.between(-1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> history.between(2L, 1L));
    }
}

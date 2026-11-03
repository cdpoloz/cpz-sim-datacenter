package com.cpz.sim.datacenter.dataset;

import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.example.SyntheticDatasetGenerator;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.input.*;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.foundation.engine.SimulationEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.cpz.sim.datacenter.dataset.DatasetReplayException.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetReplaySessionIntegrationTest {
    @TempDir static Path temporary;
    static final Path CONFIGURATION_PATH = Path.of("data/config/synthetic-dataset-pilot.json");
    static final Instant START = SyntheticDatasetGenerator.START;
    static final Duration STEP = Duration.ofMinutes(15);
    static Path workbook;
    static DatacenterDefinition configuration;
    static CustomerDataset dataset;

    @BeforeAll static void generate() throws Exception {
        workbook = temporary.resolve("session.xlsx");
        SyntheticDatasetGenerator.generate(Path.of("docs/templates/datacenter-customer-dataset-template.xlsx"), workbook);
        configuration = new JsonDatacenterConfigLoader().load(CONFIGURATION_PATH);
        dataset = new XlsxCustomerDatasetLoader().load(workbook);
    }

    private DatasetReplayPlan plan(DatacenterDataInputMode mode) {
        return new DatasetReplayPlan(mode, START, 3, STEP, STEP);
    }
    private DatasetReplaySession prepare(CustomerDataset observations, DatasetReplayPlan plan) {
        return DatasetReplaySession.prepare(configuration, observations, "synthetic-dataset-pilot", "1", plan);
    }
    private DatasetReplaySession session() { return prepare(dataset, plan(DatacenterDataInputMode.UTILIZATION_DRIVEN)); }
    private CustomerDataset rows(List<DatasetRow> rows) {
        var sheets = new LinkedHashMap<>(dataset.sheets()); sheets.put("ServerSamples", rows);
        return new CustomerDataset(dataset.metadata(), sheets);
    }
    private CustomerDataset changedFirst(String field, Object value) {
        var rows = new ArrayList<>(dataset.rows("ServerSamples"));
        var first = rows.getFirst(); var values = new LinkedHashMap<>(first.values());
        if (value == null) values.remove(field); else values.put(field, value);
        rows.set(0, new DatasetRow(first.sheet(), first.excelRow(), values));
        return rows(rows);
    }

    @ParameterizedTest @EnumSource(value = DatacenterDataInputMode.class, names = {"UTILIZATION_DRIVEN", "POWER_DRIVEN"})
    void realPipelineConservesAllOriginalsAndEmitsExactTimes(DatacenterDataInputMode mode) {
        var session = prepare(dataset, plan(mode));
        assertEquals(DatasetReplaySession.State.PREPARED, session.state());
        assertEquals(Duration.ZERO, session.engineElapsed());
        assertEquals(0, session.completedPoints());
        assertTrue(session.lastFrame().isEmpty());
        assertEquals(20, session.inventory().racks().size());
        assertEquals(200, session.inventory().servers().size());
        for (int k = 1; k <= 3; k++) {
            var frame = session.advance().orElseThrow();
            assertEquals(k, frame.pointNumber());
            assertEquals(START.plus(STEP.multipliedBy(k - 1)), frame.replayUtc());
            assertEquals(STEP.multipliedBy(k), frame.engineElapsed());
            assertEquals(STEP.multipliedBy(k - 1), frame.replayOffset());
            assertEquals(200, frame.servers().size());
            for (var result : frame.servers()) {
                var original = result.original();
                assertEquals("SYN-ROOM", result.roomCode());
                assertEquals(result.location().column(), original.text("column"));
                assertEquals(result.location().rackCode().value(), original.text("rackCode"));
                assertEquals(result.location().slot(), original.text("slot"));
                assertEquals(frame.replayUtc().toString(), original.text("timestampUtc"));
                assertNotNull(original.text("sourceEntityId"));
                assertEquals("VALID", original.text("quality"));
                assertEquals("ASSUMED", original.text("provenance"));
                assertTrue(dataset.rows("ServerSamples").contains(original));
                assertEquals(original.number("utilizationFraction") + (mode == DatacenterDataInputMode.POWER_DRIVEN ? .05 : 0),
                        result.resultUtilizationFraction(), 1e-6);
                assertEquals(original.number("electricalPowerWatts") - (mode == DatacenterDataInputMode.UTILIZATION_DRIVEN ? 10 : 0),
                        result.resultPowerWatts(), .001);
            }
        }
        assertEquals(DatasetReplaySession.State.COMPLETED, session.state());
        var last = session.lastFrame();
        assertTrue(session.advance().isEmpty());
        assertTrue(session.advance().isEmpty());
        assertEquals(last, session.lastFrame());
        assertEquals(STEP.multipliedBy(3), session.engineElapsed());
        assertEquals(3, session.completedPoints());
    }

    @Test void laterStartUsesFreshEngineAndDatasetRelativeSourceTime() {
        var session = prepare(dataset, new DatasetReplayPlan(DatacenterDataInputMode.POWER_DRIVEN,
                START.plus(STEP), 2, STEP, STEP));
        var first = session.advance().orElseThrow();
        assertEquals(START.plus(STEP), first.replayUtc());
        assertEquals(STEP, first.engineElapsed());
        assertEquals(Duration.ZERO, first.replayOffset());
        assertEquals(.4, first.servers().getFirst().original().number("utilizationFraction"));
        assertEquals(.45, first.servers().getFirst().resultUtilizationFraction(), 1e-6);
        assertEquals(190, first.servers().getFirst().resultPowerWatts());
        assertEquals(START.plus(STEP.multipliedBy(2)), session.advance().orElseThrow().replayUtc());
    }

    @Test void unexpiredHoldSelectsOriginalAndExactExpiryIsRejectedDuringPreparation() {
        var shortStep = Duration.ofMinutes(5);
        var hold = Duration.ofMinutes(10);
        var session = prepare(dataset, new DatasetReplayPlan(DatacenterDataInputMode.UTILIZATION_DRIVEN,
                START, 2, shortStep, hold));
        var first = session.advance().orElseThrow();
        var held = session.advance().orElseThrow();
        assertEquals(START.plus(shortStep), held.replayUtc());
        assertEquals(first.servers().getFirst().original(), held.servers().getFirst().original());
        var failure = assertThrows(DatasetReplayException.class, () -> prepare(dataset,
                new DatasetReplayPlan(DatacenterDataInputMode.UTILIZATION_DRIVEN, START, 3, shortStep, hold)));
        assertEquals(AVAILABILITY, failure.kind());
        assertEquals(START.plus(hold), failure.replayUtc().orElseThrow());
        assertTrue(failure.serverLocation().isPresent());
    }

    @Test void rejectsBeforeOriginAndCoverageEndEvenWithLongHold() {
        for (Instant time : List.of(START.minusSeconds(1), START.plusSeconds(2700))) {
            var failure = assertThrows(DatasetReplayException.class, () -> prepare(dataset,
                    new DatasetReplayPlan(DatacenterDataInputMode.POWER_DRIVEN, time, 1, STEP, Duration.ofHours(1))));
            assertEquals(AVAILABILITY, failure.kind());
            assertEquals(time, failure.replayUtc().orElseThrow());
        }
    }

    private CustomerDataset gapInSecondServer() {
        return rows(dataset.rows("ServerSamples").stream().filter(row -> !("C01".equals(row.text("column"))
                && "SYN-R01".equals(row.text("rackCode")) && "U02".equals(row.text("slot"))
                && START.plus(STEP).toString().equals(row.text("timestampUtc")))).toList());
    }

    @Test void preparationChecksGapInEveryServerWithoutAnySelection() {
        var failure = assertThrows(DatasetReplayException.class, () -> prepare(gapInSecondServer(), plan(DatacenterDataInputMode.POWER_DRIVEN)));
        assertEquals(AVAILABILITY, failure.kind());
        assertEquals(new ServerLocation("C01", "SYN-R01", "U02"), failure.serverLocation().orElseThrow());
        assertEquals(START.plus(STEP), failure.replayUtc().orElseThrow());
    }

    @Test void diagnosticsKeepSeverityCoordinatesAndOptionalMetricRemainsAbsent() {
        var suspect = changedFirst("quality", "SUSPECT");
        var failure = assertThrows(DatasetReplayException.class, () -> prepare(suspect, plan(DatacenterDataInputMode.POWER_DRIVEN)));
        assertEquals(DATASET_DIAGNOSTICS, failure.kind());
        assertEquals(new CustomerDatasetValidator().validate(suspect,
                new DatasetBinding("synthetic-dataset-pilot", "1", "SYN-ROOM", new DatacenterFactory().create(configuration))), failure.diagnostics());
        var issue = failure.diagnostics().getFirst();
        assertEquals(DatasetIssue.Severity.WARNING, issue.severity());
        assertEquals("ServerSamples", issue.sheet()); assertEquals(7, issue.excelRow()); assertEquals("quality", issue.field());
        assertThrows(UnsupportedOperationException.class, () -> failure.diagnostics().clear());
        var missing = changedFirst("utilizationFraction", null);
        var required = assertThrows(DatasetReplayException.class, () -> prepare(missing, plan(DatacenterDataInputMode.UTILIZATION_DRIVEN)));
        assertEquals(DATASET_DIAGNOSTICS, required.kind());
        assertEquals("utilizationFraction", required.diagnostics().getFirst().field());
        var frame = prepare(missing, plan(DatacenterDataInputMode.POWER_DRIVEN)).advance().orElseThrow();
        assertNull(frame.servers().getFirst().original().number("utilizationFraction"));
        assertEquals(.25, frame.servers().getFirst().resultUtilizationFraction());
    }

    @Test void bindingMismatchAndIntervalMeaningAreStructured() {
        var mismatch = assertThrows(DatasetReplayException.class, () -> DatasetReplaySession.prepare(configuration, dataset,
                "wrong", "2", plan(DatacenterDataInputMode.POWER_DRIVEN)));
        assertEquals(DATASET_DIAGNOSTICS, mismatch.kind());
        assertTrue(mismatch.diagnostics().stream().anyMatch(i -> i.field().equals("configurationId")));
        assertTrue(mismatch.diagnostics().stream().anyMatch(i -> i.field().equals("configurationVersion")));
        var mean = changedFirst("sampleKind", "INTERVAL_MEAN");
        var series = new ArrayList<>(mean.rows("ServerSamples"));
        var first = series.getFirst(); var values = new LinkedHashMap<>(first.values()); values.put("intervalSeconds", 900.0);
        series.set(0, new DatasetRow(first.sheet(), first.excelRow(), values));
        var unsupported = assertThrows(DatasetReplayException.class, () -> prepare(rows(series), plan(DatacenterDataInputMode.POWER_DRIVEN)));
        assertEquals(DATASET_CONTENT, unsupported.kind());
    }

    @Test void framesAndInventoryAreImmutableAndRestartReproducesSequence() {
        var session = session();
        var inventory = session.inventory();
        var first = session.advance().orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> first.servers().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.servers().getFirst().original().values().clear());
        assertThrows(UnsupportedOperationException.class, () -> inventory.racks().clear());
        assertThrows(UnsupportedOperationException.class, () -> inventory.servers().clear());
        assertThrows(UnsupportedOperationException.class, () -> inventory.racks().getFirst().slots().clear());
        var firstCopy = first.servers().getFirst();
        var second = session.advance().orElseThrow();
        assertEquals(.2, firstCopy.original().number("utilizationFraction"));
        assertEquals(140, firstCopy.resultPowerWatts());
        assertEquals(180, second.servers().getFirst().resultPowerWatts());
        session.restart();
        assertEquals(DatasetReplaySession.State.PREPARED, session.state());
        assertEquals(Duration.ZERO, session.engineElapsed());
        assertEquals(0, session.completedPoints()); assertTrue(session.lastFrame().isEmpty()); assertTrue(session.failure().isEmpty());
        assertEquals(first, session.advance().orElseThrow());
        assertEquals(second, session.advance().orElseThrow());
        assertEquals(firstCopy, first.servers().getFirst());
        assertEquals(inventory, session.inventory());
    }

    @Test void pathEntryPreservesFilesAndRestartDoesNotReadThem() throws Exception {
        Path config = temporary.resolve("captured.json"); Path source = temporary.resolve("captured.xlsx");
        Files.copy(CONFIGURATION_PATH, config); Files.copy(workbook, source);
        byte[] configBefore = Files.readAllBytes(config); byte[] sourceBefore = Files.readAllBytes(source);
        var session = DatasetReplaySession.load(config, source, "synthetic-dataset-pilot", "1", plan(DatacenterDataInputMode.POWER_DRIVEN));
        var first = session.advance().orElseThrow();
        assertArrayEquals(configBefore, Files.readAllBytes(config)); assertArrayEquals(sourceBefore, Files.readAllBytes(source));
        Files.delete(config); Files.delete(source);
        session.restart(); assertEquals(first, session.advance().orElseThrow());
    }

    @Test void readArgumentAndConfigurationFailuresAreDistinct() throws Exception {
        var invalidPlan = assertThrows(DatasetReplayException.class, () -> new DatasetReplayPlan(DatacenterDataInputMode.POWER_DRIVEN,
                START, 1001, STEP, STEP));
        assertEquals(INVALID_ARGUMENT, invalidPlan.kind());
        for (Duration invalid : List.of(Duration.ZERO, Duration.ofSeconds(-1)))
            assertEquals(INVALID_ARGUMENT, assertThrows(DatasetReplayException.class, () -> new DatasetReplayPlan(
                    DatacenterDataInputMode.POWER_DRIVEN, START, 1, STEP, invalid)).kind());
        assertEquals(INVALID_ARGUMENT, assertThrows(DatasetReplayException.class, () -> plan(DatacenterDataInputMode.TEMPERATURE_DRIVEN)).kind());
        assertEquals(INVALID_ARGUMENT, assertThrows(DatasetReplayException.class, () -> new DatasetReplayPlan(
                DatacenterDataInputMode.POWER_DRIVEN, Instant.MIN, 1, STEP, STEP)).kind());
        var plan = plan(DatacenterDataInputMode.POWER_DRIVEN);
        assertEquals(CONFIGURATION_READ, assertThrows(DatasetReplayException.class, () -> DatasetReplaySession.load(
                temporary.resolve("absent.json"), workbook, "id", "1", plan)).kind());
        assertEquals(DATASET_READ, assertThrows(DatasetReplayException.class, () -> DatasetReplaySession.load(
                CONFIGURATION_PATH, temporary.resolve("absent.xlsx"), "id", "1", plan)).kind());
        Path invalid = temporary.resolve("formula.xlsx");
        try (var input = Files.newInputStream(workbook); var copy = new org.apache.poi.xssf.usermodel.XSSFWorkbook(input)) {
            copy.getSheet("ServerSamples").getRow(6).getCell(6).setCellFormula("1/4");
            try (var output = Files.newOutputStream(invalid)) { copy.write(output); }
        }
        byte[] before = Files.readAllBytes(invalid);
        assertEquals(DATASET_READ, assertThrows(DatasetReplayException.class, () -> DatasetReplaySession.load(CONFIGURATION_PATH,
                invalid, "synthetic-dataset-pilot", "1", plan)).kind());
        assertArrayEquals(before, Files.readAllBytes(invalid));
        var badConfig = new DatacenterDefinition("", configuration.layout(), configuration.serverModels(), configuration.servers());
        assertEquals(DatasetReplayException.Kind.CONFIGURATION, assertThrows(DatasetReplayException.class, () -> DatasetReplaySession.prepare(
                badConfig, dataset, "id", "1", plan)).kind());
    }

    @Test void rejectsUnsupportedCoolingCouplingHealthAndRackPower() {
        var cooling = new JsonDatacenterConfigLoader().load(Path.of("data/config/rack-cooling-comparison.json"));
        assertEquals(UNSUPPORTED_CONFIGURATION, assertThrows(DatasetReplayException.class, () -> DatasetReplaySession.prepare(
                cooling, dataset, "id", "1", plan(DatacenterDataInputMode.POWER_DRIVEN))).kind());
        var health = new DatacenterDefinition(configuration.name(), configuration.layout(), configuration.serverModels(), configuration.servers(),
                configuration.temperature(), new HealthSystemOptionsDefinition(new HealthThresholdDefinition(.9, .8),
                new HealthThresholdDefinition(80, 75)));
        assertEquals(UNSUPPORTED_CONFIGURATION, assertThrows(DatasetReplayException.class, () -> DatasetReplaySession.prepare(
                health, dataset, "id", "1", plan(DatacenterDataInputMode.POWER_DRIVEN))).kind());
        var rack = new DatacenterDefinition(configuration.name(), configuration.layout(), configuration.serverModels(), configuration.servers(),
                configuration.temperature(), null, null, DatacenterDataInputMode.POWER_DRIVEN, PowerInputGranularity.RACK);
        assertEquals(UNSUPPORTED_CONFIGURATION, assertThrows(DatasetReplayException.class, () -> DatasetReplaySession.prepare(
                rack, dataset, "id", "1", plan(DatacenterDataInputMode.POWER_DRIVEN))).kind());
    }

    @Test void capturedConfigurationCollectionsAreIndependentOfCallerChanges() {
        var models = new ArrayList<>(configuration.serverModels()); var installed = new ArrayList<>(configuration.servers());
        var racks = new ArrayList<>(configuration.layout().racks());
        var mutable = new DatacenterDefinition(configuration.name(), new DatacenterLayoutDefinition(configuration.layout().room(), racks),
                models, installed, configuration.temperature());
        var session = DatasetReplaySession.prepare(mutable, dataset, "synthetic-dataset-pilot", "1", plan(DatacenterDataInputMode.POWER_DRIVEN));
        var first = session.advance().orElseThrow();
        models.clear(); installed.clear(); racks.clear();
        session.restart(); assertEquals(first, session.advance().orElseThrow());
    }

    // Fault injection exercises guarantees that immutable, fully preflighted public inputs cannot normally violate.
    private Object runtimeField(DatasetReplaySession session, String name) throws Exception {
        var field = DatasetReplaySession.class.getDeclaredField(name); field.setAccessible(true); return field.get(session);
    }

    @Test void perAdvancePrecheckRejectsWithoutMutatingAnyRuntimeState() throws Exception {
        var session = session(); var first = session.advance().orElseThrow();
        var replayField = DatasetReplaySession.class.getDeclaredField("replay"); replayField.setAccessible(true);
        replayField.set(session, new ServerDatasetReplay(gapInSecondServer(), new DatasetBinding("synthetic-dataset-pilot", "1", "SYN-ROOM",
                new DatacenterFactory().create(configuration)), STEP));
        var datacenter = (Datacenter) runtimeField(session, "datacenter");
        var powers = datacenter.getServers().stream().map(Server::getCurrentPowerWatts).toList();
        var utilizations = datacenter.getServers().stream().map(Server::getUtilization).toList();
        assertEquals(AVAILABILITY, assertThrows(DatasetReplayException.class, session::advance).kind());
        assertEquals(STEP, session.engineElapsed()); assertEquals(1, session.completedPoints());
        assertEquals(first, session.lastFrame().orElseThrow()); assertEquals(DatasetReplaySession.State.ACTIVE, session.state());
        assertEquals(powers, datacenter.getServers().stream().map(Server::getCurrentPowerWatts).toList());
        assertEquals(utilizations, datacenter.getServers().stream().map(Server::getUtilization).toList());
    }

    @Test void unexpectedSystemFailureBlocksFurtherStepsWithoutPublishingAFrame() throws Exception {
        var session = session(); var first = session.advance().orElseThrow();
        var engine = (SimulationEngine) runtimeField(session, "engine");
        engine.register(tick -> { throw new IllegalStateException("Injected system failure"); });
        var failure = assertThrows(DatasetReplayException.class, session::advance);
        assertEquals(EXECUTION, failure.kind()); assertEquals("Injected system failure", failure.getCause().getMessage());
        assertEquals(DatasetReplaySession.State.FAILED, session.state()); assertEquals(failure, session.failure().orElseThrow());
        assertEquals(first, session.lastFrame().orElseThrow()); assertEquals(1, session.completedPoints());
        assertEquals(STEP.multipliedBy(2), session.engineElapsed(), "No rollback is promised");
        assertEquals(SESSION_STATE, assertThrows(DatasetReplayException.class, session::advance).kind());
        assertEquals(STEP.multipliedBy(2), session.engineElapsed());
        session.restart(); assertEquals(first, session.advance().orElseThrow());
    }
}

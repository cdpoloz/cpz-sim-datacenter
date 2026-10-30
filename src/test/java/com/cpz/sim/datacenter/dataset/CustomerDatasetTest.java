package com.cpz.sim.datacenter.dataset;

import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerInputSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.foundation.time.SimulationTick;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDatasetTest {
    @TempDir Path temporary;
    static final Instant START = Instant.parse("2026-06-01T00:00:00Z");
    static Map<String, Object> metadata() {
        return Map.of("schemaVersion", "0.1-proposed", "datasetId", "test", "partId", "one",
                "configurationId", "config", "configurationVersion", "v1", "roomCode", "ROOM");
    }
    static DatasetRow sample(int row, Instant time, double utilization, double power) {
        return new DatasetRow("ServerSamples", row, Map.ofEntries(
                Map.entry("timestampUtc", time.toString()), Map.entry("roomCode", "ROOM"),
                Map.entry("column", "C01"), Map.entry("rackCode", "R01"), Map.entry("slot", "S01"),
                Map.entry("utilizationFraction", utilization), Map.entry("electricalPowerWatts", power),
                Map.entry("quality", "VALID"), Map.entry("provenance", "MEASURED"), Map.entry("sampleKind", "INSTANTANEOUS")));
    }
    static DatasetRow with(DatasetRow row, String key, Object value) {
        Map<String, Object> values = new LinkedHashMap<>(row.values());
        if (value == null) values.remove(key); else values.put(key, value);
        return new DatasetRow(row.sheet(), row.excelRow(), values);
    }
    static CustomerDataset dataset(DatasetRow... rows) {
        return new CustomerDataset(metadata(), Map.of("ServerSamples", List.of(rows)));
    }
    static DatasetBinding binding() {
        var rack = new Rack(new RackLocation("C01", new RackCode("R01")), "row", List.of("S01"));
        var server = new Server(new ServerLocation("C01", "R01", "S01"),
                new ServerConfig("model", "test", "test", 100, 300), HardwareStatus.OK, ServerRole.GENERAL_PURPOSE);
        return new DatasetBinding("config", "v1", "ROOM", new Datacenter(List.of(rack), List.of(server)));
    }
    static List<DatasetIssue> validate(DatasetRow... rows) {
        return new CustomerDatasetValidator().validate(dataset(rows), binding());
    }
    static boolean error(List<DatasetIssue> issues, String field) {
        return issues.stream().anyMatch(i -> i.severity() == DatasetIssue.Severity.ERROR && i.field().equals(field));
    }
    @Test void acceptsIndependentMetricsAndKeepsSourceImmutable() {
        var row = sample(7, START, .25, 180);
        assertTrue(validate(row).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> row.values().put("utilizationFraction", .5));
        assertEquals(.25, row.number("utilizationFraction"));
        assertEquals(180, row.number("electricalPowerWatts"));
    }
    @Test void diagnosesMissingMetadataAndIdentitiesWithoutFillingThem() {
        var row = with(sample(7, START, 0, 100), "column", null);
        var source = new CustomerDataset(Map.of("schemaVersion", "0.1-proposed"), Map.of("ServerSamples", List.of(row)));
        var issues = new CustomerDatasetValidator().validate(source, null);
        assertTrue(error(issues, "configurationId")); assertTrue(error(issues, "column"));
        assertNull(source.metadata().get("roomCode")); assertNull(row.text("column"));
    }
    @Test void rejectsDuplicateKeysAndUnknownInstalledServers() {
        var row = sample(7, START, .2, 150);
        assertTrue(error(validate(row, row), "timestampUtc"));
        assertTrue(error(validate(with(row, "slot", "unknown")), "slot"));
        assertTrue(error(validate(with(row, "column", "C02")), "rackCode"));
    }
    @Test void checksNativeTypesRangesAndUtc() {
        var row = sample(7, START, .2, 150);
        for (Object value : List.of("0.2", Double.NaN, Double.POSITIVE_INFINITY, -1., 1.1))
            assertTrue(error(validate(with(row, "utilizationFraction", value)), "utilizationFraction"));
        for (Object value : List.of("2026-06-01T00:00:00", "2026-06-01T00:00:00+04:00", 46174.0))
            assertTrue(error(validate(with(row, "timestampUtc", value)), "timestampUtc"));
        assertTrue(error(validate(with(row, "electricalPowerWatts", 301.)), "electricalPowerWatts"));
        assertTrue(error(validate(with(row, "electricalPowerWatts", -1.)), "electricalPowerWatts"));
    }
    @Test void validatesIntervalsMissingRowsAndOfflineConsistency() {
        var row = sample(7, START, 0, 100);
        assertTrue(error(validate(with(row, "sampleKind", "INTERVAL_MEAN")), "intervalSeconds"));
        assertTrue(error(validate(with(row, "intervalSeconds", 900.)), "intervalSeconds"));
        var mean = with(with(row, "sampleKind", "INTERVAL_MEAN"), "intervalSeconds", 900.);
        assertFalse(error(validate(mean), "intervalSeconds"));
        assertTrue(error(validate(mean, sample(8, START.plusSeconds(60), 0, 100)), "timestampUtc"));
        assertTrue(error(validate(with(row, "quality", "MISSING")), "quality"));
        assertTrue(error(validate(with(row, "status", "OFFLINE")), "status"));
        assertFalse(error(validate(with(with(row, "electricalPowerWatts", 0.), "status", "OFFLINE")), "status"));
    }
    @Test void enforcesBindingVersion() {
        var meta = new LinkedHashMap<>(metadata()); meta.put("configurationVersion", "other");
        var source = new CustomerDataset(meta, Map.of("ServerSamples", List.of(sample(7, START, .25, 180))));
        assertTrue(error(new CustomerDatasetValidator().validate(source, binding()), "configurationVersion"));
    }
    @Test void rejectsMappingConflictsWithoutRewritingSampleIdentity() {
        var row = with(sample(7, START, .25, 180), "sourceEntityId", "original");
        var mapping = new DatasetRow("EntityMapping", 7, Map.of("entityType", "SERVER", "sourceEntityId", "original",
                "roomCode", "ROOM", "column", "C02", "rackCode", "R01", "slot", "S01"));
        var source = new CustomerDataset(metadata(), Map.of("ServerSamples", List.of(row), "EntityMapping", List.of(mapping)));
        var issues = new CustomerDatasetValidator().validate(source, binding());
        assertTrue(issues.stream().anyMatch(i -> i.sheet().equals("ServerSamples") && i.field().equals("column")));
        assertEquals("C01", row.text("column"));
    }
    @Test void validatesAggregateBoundariesAndCoolingBooleanTypes() {
        var server = sample(7, START, .25, 180);var values = new LinkedHashMap<>(server.values());values.remove("slot");
        var rack = new DatasetRow("RackSamples", 7, values);
        var aggregate = new CustomerDataset(metadata(), Map.of("RackSamples", List.of(rack)));
        var issues = new CustomerDatasetValidator().validate(aggregate, binding());
        assertTrue(error(issues, "utilizationBasis"));assertTrue(error(issues, "electricalScope"));
        values = new LinkedHashMap<>();
        for (String key : List.of("timestampUtc", "roomCode", "quality", "provenance", "sampleKind")) values.put(key, server.values().get(key));
        values.put("unitType", "SUPPLY");values.put("unitCode", "supply");values.put("isEnabled", true);
        var cooling = new DatasetRow("CoolingSamples", 7, values);
        var bound = binding();var coolingBinding = new DatasetBinding("config", "v1", "ROOM", bound.datacenter(), Set.of("SUPPLY:supply"));
        assertTrue(new CustomerDatasetValidator().validate(new CustomerDataset(metadata(), Map.of("CoolingSamples", List.of(cooling))), coolingBinding).isEmpty());
        var bad = with(cooling, "isEnabled", "TRUE");
        assertTrue(error(new CustomerDatasetValidator().validate(new CustomerDataset(metadata(), Map.of("CoolingSamples", List.of(bad))), coolingBinding), "isEnabled"));
    }
    @Test void boundedReplayHasNoWrapZeroFillOrStatefulReset() {
        var source = dataset(sample(7, START, .25, 180), sample(8, START.plusSeconds(900), .5, 250));
        var bound = binding(); var server = bound.datacenter().getServers().getFirst();
        var replay = new ServerDatasetReplay(source, bound, Duration.ofSeconds(900));
        var workload = replay.workloadSource(); var power = replay.powerSource();
        assertEquals(.25, workload.getUtilization(server, tick(899)));
        assertEquals(180, power.currentPowerWatts(server, tick(899)));
        assertEquals(.5, workload.getUtilization(server, tick(900)));
        assertThrows(IllegalStateException.class, () -> workload.getUtilization(server, tick(1800)));
        assertThrows(IllegalStateException.class, () -> replay.observationAt(server.getLocation(), START.minusNanos(1)));
        workload.reset(); assertEquals(.25, workload.getUtilization(server, tick(0)));
        assertEquals(180, replay.observationAt(server.getLocation(), START).number("electricalPowerWatts"));
        var meta = new LinkedHashMap<>(metadata());meta.put("coverageEndExclusiveUtc", START.plusSeconds(1000).toString());
        var bounded = new ServerDatasetReplay(new CustomerDataset(meta, source.sheets()), bound, Duration.ofSeconds(900));
        assertThrows(IllegalStateException.class, () -> bounded.workloadSource().getUtilization(server, tick(1000)));
    }
    @Test void rejectsUnapprovedOrUnknownSemanticsBeforeReplay() {
        var row = sample(7, START, .25, 180);
        for (String field : List.of("sampleKind", "provenance"))
            assertThrows(IllegalArgumentException.class, () -> new ServerDatasetReplay(dataset(with(row, field, "UNSPECIFIED")), binding(), Duration.ofMinutes(15)));
        assertThrows(IllegalArgumentException.class, () -> new ServerDatasetReplay(dataset(with(row, "quality", "UNASSESSED")), binding(), Duration.ofMinutes(15)));
        assertThrows(IllegalArgumentException.class, () -> new ServerDatasetReplay(dataset(row), binding(), Duration.ZERO));
        var replay = new ServerDatasetReplay(dataset(with(row, "electricalPowerWatts", null)), binding(), Duration.ofMinutes(15));
        assertThrows(IllegalArgumentException.class, replay::powerSource);
        var mean = with(with(row, "sampleKind", "INTERVAL_MEAN"), "intervalSeconds", 900.);
        assertThrows(IllegalArgumentException.class, () -> new ServerDatasetReplay(dataset(mean), binding(), Duration.ofMinutes(15)));
    }
    @Test void integratesWithExistingModesWithoutOverwritingSourceMeasurements() {
        var source = dataset(sample(7, START, .25, 180)); var bound = binding();
        var replay = new ServerDatasetReplay(source, bound, Duration.ofMinutes(15));
        var datacenter = bound.datacenter();var server = datacenter.getServers().getFirst();
        new WorkloadSystem(datacenter, replay.workloadSource()).update(tick(0));
        new PowerConsumptionSystem(datacenter).update(tick(0));
        assertEquals(150, server.getCurrentPowerWatts());
        new PowerInputSystem(datacenter, replay.powerSource()).update(tick(0));
        assertEquals(180, server.getCurrentPowerWatts()); assertEquals(.4, server.getUtilization(), .00001);
        assertEquals(.25, source.rows("ServerSamples").getFirst().number("utilizationFraction"));
        assertEquals(180, source.rows("ServerSamples").getFirst().number("electricalPowerWatts"));
    }
    @Test void loadsNativeCellsAndBlankValuesWithSourceLocations() throws Exception {
        Path file = writeWorkbook(false, false);
        var imported = new XlsxCustomerDatasetLoader().load(file);
        var row = imported.rows("ServerSamples").getFirst();
        assertEquals(7, row.excelRow());assertEquals(.25, row.number("utilizationFraction"));
        assertEquals(180, row.number("electricalPowerWatts"));assertEquals(START.toString(), row.text("timestampUtc"));
        assertFalse(row.values().containsKey("temperatureCelsius"));
        assertTrue(new CustomerDatasetValidator().validate(imported, binding()).isEmpty());
    }
    @Test void rejectsUnknownHeadersAndFormulaCells() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new XlsxCustomerDatasetLoader().load(writeWorkbook(true, false)));
        assertThrows(IllegalArgumentException.class, () -> new XlsxCustomerDatasetLoader().load(writeWorkbook(false, true)));
    }
    private Path writeWorkbook(boolean wrongHeader, boolean formula) throws Exception {
        Path path = temporary.resolve(UUID.randomUUID() + ".xlsx");
        try (var wb = new XSSFWorkbook()) {
            Sheet meta = wb.createSheet("Dataset");Row header = meta.createRow(5);
            List<String> columns = List.of("field", "value", "requirement", "description");
            for (int i = 0; i < columns.size(); i++) header.createCell(i).setCellValue(columns.get(i));
            int i = 6;
            for (String key : DatasetSchema.METADATA) { Row row = meta.createRow(i++);row.createCell(0).setCellValue(key);if (metadata().containsKey(key)) row.createCell(1).setCellValue(metadata().get(key).toString()); }
            for (var spec : DatasetSchema.HEADERS.entrySet()) {
                Sheet sheet = wb.createSheet(spec.getKey());Row h = sheet.createRow(5);
                for (int j = 0; j < spec.getValue().size(); j++) h.createCell(j).setCellValue(spec.getValue().get(j));
                if (spec.getKey().equals("ServerSamples")) {
                    if (wrongHeader) h.getCell(0).setCellValue("unexpected");
                    Row row = sheet.createRow(6);var values = sample(7, START, .25, 180).values();
                    for (int j = 0; j < spec.getValue().size(); j++) {
                        Object value = values.get(spec.getValue().get(j));if (value == null) continue;
                        Cell cell = row.createCell(j); if (value instanceof Number number) cell.setCellValue(number.doubleValue());else cell.setCellValue(value.toString());
                    }
                    if (formula) row.getCell(spec.getValue().indexOf("utilizationFraction")).setCellFormula("1/4");
                }
            }
            try (var stream = Files.newOutputStream(path)) { wb.write(stream); }
        }
        return path;
    }
    private static SimulationTick tick(long seconds) { return new SimulationTick(seconds, Duration.ofSeconds(seconds), Duration.ofSeconds(1)); }
}

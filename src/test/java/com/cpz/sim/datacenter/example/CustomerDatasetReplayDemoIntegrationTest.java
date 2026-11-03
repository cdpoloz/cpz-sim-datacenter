package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.dataset.*;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CustomerDatasetReplayDemoIntegrationTest {
    @TempDir static Path directory;
    static Path workbook;
    static final Path CONFIG = Path.of("data/config/synthetic-dataset-pilot.json");
    static final Path TEMPLATE = Path.of("docs/templates/datacenter-customer-dataset-template.xlsx");

    @BeforeAll static void generate() throws IOException {
        workbook = directory.resolve("synthetic.xlsx");
        SyntheticDatasetGenerator.generate(TEMPLATE, workbook);
    }

    private String[] arguments(String mode) {
        return new String[]{workbook.toString(), CONFIG.toString(), "synthetic-dataset-pilot", "1", mode,
                "PT15M", "2026-01-01T00:00:00Z", "3", "PT15M", "C01-SYN-R01-U01,C02-SYN-R20-U10"};
    }

    private record Result(int status, String output, String error) {}
    private Result run(String[] args) {
        var output = new ByteArrayOutputStream();
        var error = new ByteArrayOutputStream();
        int status = CustomerDatasetReplayDemo.run(args, new PrintStream(output), new PrintStream(error));
        return new Result(status, output.toString(), error.toString());
    }

    private void fails(String[] args, String message) {
        var result = run(args);
        assertEquals(2, result.status(), result.toString());
        assertEquals("", result.output(), "No partial simulation output on failure");
        assertTrue(result.error().contains("REPLAY_FAILED:"), result.error());
        assertTrue(result.error().contains(message), result.error());
    }

    @Test void generatingScenarioPreservesTemplateBytes() throws IOException {
        byte[] before = Files.readAllBytes(TEMPLATE);
        SyntheticDatasetGenerator.generate(TEMPLATE, directory.resolve("template-preservation.xlsx"));
        assertArrayEquals(before, Files.readAllBytes(TEMPLATE), "Generation must not rewrite the input template");
    }

    @Test void generatedScenarioIsBoundWithoutDiagnosticsAndCannotOverwriteInputs() throws IOException {
        var dataset = new XlsxCustomerDatasetLoader().load(workbook);
        var definition = new JsonDatacenterConfigLoader().load(CONFIG);
        var datacenter = new DatacenterFactory().create(definition);
        assertEquals(20, datacenter.getRackCount());
        assertEquals(200, datacenter.getServerCount());
        assertEquals(600, dataset.rows("ServerSamples").size());
        assertEquals(200, dataset.rows("EntityMapping").size());
        assertTrue(new CustomerDatasetValidator().validate(dataset,
                new DatasetBinding("synthetic-dataset-pilot", "1", "SYN-ROOM", datacenter, Set.of())).isEmpty());
        assertTrue(dataset.rows("ServerSamples").stream().allMatch(r -> "ASSUMED".equals(r.text("provenance"))));
        assertThrows(FileAlreadyExistsException.class, () -> SyntheticDatasetGenerator.generate(TEMPLATE, workbook));
        Path second = directory.resolve("second.xlsx");
        SyntheticDatasetGenerator.generate(TEMPLATE, second);
        assertEquals(dataset, new XlsxCustomerDatasetLoader().load(second), "Deterministic data, independent of XLSX ZIP timestamps");
    }

    @ParameterizedTest @ValueSource(strings = {"UTILIZATION_DRIVEN", "POWER_DRIVEN"})
    void realPipelinePreservesBothOriginalsAndChangesBetweenInstants(String mode) throws IOException {
        byte[] before = Files.readAllBytes(workbook);
        var result = run(arguments(mode));
        assertEquals(0, result.status(), result.error());
        assertEquals("", result.error());
        assertTrue(result.output().contains("diagnostics=0"));
        assertTrue(result.output().endsWith("REPLAY_OK\n"));
        var rows = result.output().lines().filter(s -> s.startsWith(mode + ",")).map(s -> s.split(",")).toList();
        assertEquals(6, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            double original = (20 + (i / 2) * 20 + (i % 2 == 0 ? 0 : 9)) / 100.0;
            double originalPower = 100 + original * 200 + 10;
            assertEquals(original, Double.parseDouble(row[4]), 1e-9);
            assertEquals(originalPower, Double.parseDouble(row[5]), 1e-9);
            assertEquals(original + (mode.equals("POWER_DRIVEN") ? .05 : 0), Double.parseDouble(row[6]), 1e-6);
            assertEquals(originalPower - (mode.equals("UTILIZATION_DRIVEN") ? 10 : 0), Double.parseDouble(row[7]), .001);
        }
        assertArrayEquals(before, Files.readAllBytes(workbook));
    }

    @Test void coverageEndFailsEvenWithLongHoldAndBeforeFirstSampleFails() {
        var args = arguments("POWER_DRIVEN");
        args[5] = "PT1H"; args[7] = "4";
        fails(args, "beyond declared dataset coverage");
        args[6] = "2025-12-31T23:59:59Z"; args[7] = "1";
        fails(args, "No unexpired sample");
    }

    @Test void exactHoldExpiryFailsBeforeAnyOutputWhileUnexpiredHoldWorks() {
        var args = arguments("UTILIZATION_DRIVEN");
        args[5] = "PT10M"; args[8] = "PT5M";
        fails(args, "No unexpired sample");
        args[7] = "2";
        var result = run(args);
        assertEquals(0, result.status(), result.error());
        assertTrue(result.output().contains("2026-01-01T00:05:00Z,C01-SYN-R01-U01,2026-01-01T00:00:00Z,0.2,150.0"));
    }

    private Path editedWorkbook(String name, java.util.function.Consumer<Workbook> edit) throws IOException {
        Path output = directory.resolve(name);
        try (var input = Files.newInputStream(workbook); Workbook copy = new XSSFWorkbook(input)) {
            edit.accept(copy);
            try (var stream = Files.newOutputStream(output)) { copy.write(stream); }
        }
        return output;
    }

    @Test void missingMiddleSampleInUnselectedServerFailsWholeRun() throws IOException {
        Path gap = editedWorkbook("gap.xlsx", copy -> {
            Sheet sheet = copy.getSheet("ServerSamples");
            // Second server, middle instant: not among selected console servers.
            sheet.removeRow(sheet.getRow(10));
        });
        var args = arguments("POWER_DRIVEN"); args[0] = gap.toString();
        fails(args, "No unexpired sample for C01-SYN-R01-U02");
    }

    @Test void unresolvedQualityAndBlankInputAreNotRepaired() throws IOException {
        Path suspect = editedWorkbook("suspect.xlsx", copy -> copy.getSheet("ServerSamples").getRow(6).getCell(12).setCellValue("SUSPECT"));
        var args = arguments("UTILIZATION_DRIVEN"); args[0] = suspect.toString();
        fails(args, "Observation is not approved");
        Path blank = editedWorkbook("blank.xlsx", copy -> copy.getSheet("ServerSamples").getRow(6).getCell(6).setBlank());
        args[0] = blank.toString();
        fails(args, "Every server sample must supply utilizationFraction");
    }

    @Test void mismatchedBindingAndPhysicalInventoryFail() throws IOException {
        var args = arguments("POWER_DRIVEN"); args[2] = "wrong-installation";
        fails(args, "Does not match installation binding");
        args = arguments("POWER_DRIVEN"); args[3] = "wrong-version";
        fails(args, "Does not match installation binding");
        Path changed = directory.resolve("different-installation.json");
        Files.writeString(changed, Files.readString(CONFIG).replace("SYN-R01", "OTHER-R01"));
        args = arguments("POWER_DRIVEN"); args[1] = changed.toString();
        fails(args, "Unknown rack location");
    }

    @Test void invalidArgumentsHaveScriptIdentifiableFailures() {
        fails(new String[0], "Usage:");
        for (var change : List.of(new String[]{"4", "DUAL"}, new String[]{"4", "TEMPERATURE_DRIVEN"},
                new String[]{"5", "PT0S"}, new String[]{"5", "nonsense"}, new String[]{"7", "0"},
                new String[]{"7", "1001"}, new String[]{"8", "PT0S"}, new String[]{"9", "unknown-server"},
                new String[]{"6", "2026-01-01T00:00:00"})) {
            var args = arguments("POWER_DRIVEN"); args[Integer.parseInt(change[0])] = change[1];
            fails(args, "");
        }
    }
}

package com.cpz.sim.datacenter.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Reproducible fictional observations in the official workbook; never customer data. */
public final class SyntheticDatasetGenerator {
    public static final String CONFIGURATION_ID = "synthetic-dataset-pilot";
    public static final String VERSION = "1";
    public static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private SyntheticDatasetGenerator() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: template.xlsx output.xlsx (must not exist)");
        generate(Path.of(args[0]), Path.of(args[1]));
        System.out.println("Generated synthetic dataset: " + args[1] + " | 20 racks | 200 servers | 600 samples");
    }

    public static void generate(Path template, Path output) throws IOException {
        try (var input = Files.newInputStream(template); Workbook workbook = new XSSFWorkbook(input)) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("schemaVersion", "0.1-proposed");
            metadata.put("datasetId", "synthetic-20-rack-pilot");
            metadata.put("partId", "synthetic-three-instants");
            metadata.put("configurationId", CONFIGURATION_ID);
            metadata.put("configurationVersion", VERSION);
            metadata.put("roomCode", "SYN-ROOM");
            metadata.put("sourceName", "SyntheticDatasetGenerator v1");
            metadata.put("sourceTimezone", "UTC");
            metadata.put("normalizedAtUtc", START.toString());
            metadata.put("nominalSamplingIntervalSeconds", 900);
            metadata.put("coverageStartUtc", START.toString());
            metadata.put("coverageEndExclusiveUtc", START.plusSeconds(2700).toString());
            metadata.put("sourceManifest", "SyntheticDatasetGenerator v1; no customer source");
            metadata.put("normalizationNotes", "Fictional instantaneous VALID/ASSUMED observations; all identities, ratings and layout are demo assumptions; never measured.");
            Sheet sheet = workbook.getSheet("Dataset");
            for (int i = 6; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(0) == null) continue;
                Object value = metadata.get(row.getCell(0).getStringCellValue());
                if (value != null) set(row.createCell(1), value);
            }
            for (String name : List.of("EntityMapping", "ServerSamples", "RackSamples", "RoomSamples", "CoolingSamples")) {
                sheet = workbook.getSheet(name);
                for (int i = sheet.getLastRowNum(); i >= 6; i--) {
                    Row row = sheet.getRow(i);
                    if (row != null) sheet.removeRow(row);
                }
            }
            for (int rack = 1; rack <= 20; rack++) for (int slot = 1; slot <= 10; slot++) {
                Map<String, Object> identity = new LinkedHashMap<>();
                identity.put("roomCode", "SYN-ROOM");
                identity.put("column", String.format(Locale.ROOT, "C%02d", (rack - 1) / 10 + 1));
                identity.put("rackCode", String.format(Locale.ROOT, "SYN-R%02d", rack));
                identity.put("slot", String.format(Locale.ROOT, "U%02d", slot));
                identity.put("sourceEntityId", String.format(Locale.ROOT, "synthetic-server-%02d-%02d", rack, slot));
                Map<String, Object> mapping = new LinkedHashMap<>(identity);
                mapping.put("entityType", "SERVER");
                mapping.put("notes", "Synthetic deterministic crosswalk; fictional installation.");
                append(workbook.getSheet("EntityMapping"), mapping);
                for (int instant = 0; instant < 3; instant++) {
                    Map<String, Object> sample = new LinkedHashMap<>(identity);
                    double utilization = (20 + instant * 20 + slot - 1) / 100.0;
                    sample.put("timestampUtc", START.plusSeconds(instant * 900L).toString());
                    sample.put("utilizationFraction", utilization);
                    sample.put("electricalPowerWatts", 100 + 200 * utilization + 10);
                    sample.put("sampleKind", "INSTANTANEOUS");
                    sample.put("quality", "VALID");
                    sample.put("provenance", "ASSUMED");
                    sample.put("sourceRef", "SyntheticDatasetGenerator:v1");
                    sample.put("notes", "Synthetic pair: power deliberately 10 W above linear utilization model; not measured.");
                    append(workbook.getSheet("ServerSamples"), sample);
                }
            }
            Path parent = output.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            try (var stream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                workbook.write(stream);
            }
        }
    }

    private static void append(Sheet sheet, Map<String, Object> values) {
        Row row = sheet.createRow(Math.max(6, sheet.getLastRowNum() + 1));
        for (Cell header : sheet.getRow(5)) {
            Object value = values.get(header.getStringCellValue());
            if (value != null) set(row.createCell(header.getColumnIndex()), value);
        }
    }

    private static void set(Cell cell, Object value) {
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value.toString());
    }
}

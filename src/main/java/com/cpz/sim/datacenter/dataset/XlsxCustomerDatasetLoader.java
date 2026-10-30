package com.cpz.sim.datacenter.dataset;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Reads the normalized 0.1-proposed workbook, not arbitrary client spreadsheets.
 * This pilot uses POI's in-memory workbook API; partition large datasets before loading.
 */
public final class XlsxCustomerDatasetLoader {
    public CustomerDataset load(Path path) {
        Objects.requireNonNull(path, "path");
        try (Workbook workbook = new XSSFWorkbook(path.toFile())) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            Sheet metadataSheet = requiredSheet(workbook, "Dataset");
            Map<Integer, String> metadataHeaders = headers(metadataSheet, List.of("field", "value", "requirement", "description"));
            Set<String> seen = new HashSet<>();
            for (int index = 6; index <= metadataSheet.getLastRowNum(); index++) {
                Map<String, Object> row = values(metadataSheet.getRow(index), metadataHeaders);
                if (row.isEmpty()) continue;
                Object key = row.get("field");
                if (!(key instanceof String field) || !DatasetSchema.METADATA.contains(field))
                    throw invalid("Dataset", index + 1, "Unknown or missing metadata field: " + key);
                if (!seen.add(field)) throw invalid("Dataset", index + 1, "Duplicate metadata field: " + field);
                if (row.containsKey("value")) metadata.put(field, row.get("value"));
            }
            if (!seen.containsAll(DatasetSchema.METADATA))
                throw invalid("Dataset", 6, "Missing metadata field rows");
            Map<String, List<DatasetRow>> sheets = new LinkedHashMap<>();
            for (var entry : DatasetSchema.HEADERS.entrySet()) {
                Sheet sheet = requiredSheet(workbook, entry.getKey());
                Map<Integer, String> columns = headers(sheet, entry.getValue());
                List<DatasetRow> rows = new ArrayList<>();
                for (int index = 6; index <= sheet.getLastRowNum(); index++) {
                    Map<String, Object> values = values(sheet.getRow(index), columns);
                    if (!values.isEmpty()) rows.add(new DatasetRow(sheet.getSheetName(), index + 1, values));
                }
                sheets.put(entry.getKey(), rows);
            }
            Set<String> allowed = new HashSet<>(DatasetSchema.HEADERS.keySet());
            allowed.addAll(List.of("Dataset", "Guide", "Dictionary"));
            for (Sheet sheet : workbook) if (!allowed.contains(sheet.getSheetName()))
                throw invalid(sheet.getSheetName(), 1, "Unexpected worksheet; normalize the client export first");
            return new CustomerDataset(metadata, sheets);
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException exception) {
            throw new IllegalArgumentException("Cannot read dataset workbook: " + path, exception);
        }
    }

    private static Sheet requiredSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) throw invalid(name, 1, "Missing worksheet");
        return sheet;
    }

    private static Map<Integer, String> headers(Sheet sheet, List<String> expected) {
        Row header = sheet.getRow(5);
        if (header == null) throw invalid(sheet.getSheetName(), 6, "Missing header row");
        Map<Integer, String> columns = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Cell cell : header) {
            Object raw = value(cell);
            if (raw == null) continue;
            if (!(raw instanceof String text) || !expected.contains(text) || !seen.add(text))
                throw invalid(sheet.getSheetName(), 6, "Unknown or duplicated header: " + raw);
            columns.put(cell.getColumnIndex(), text);
        }
        if (!seen.equals(new HashSet<>(expected))) throw invalid(sheet.getSheetName(), 6, "Missing required column headers");
        return columns;
    }

    private static Map<String, Object> values(Row row, Map<Integer, String> headers) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (row == null) return result;
        for (Cell cell : row) {
            Object value = value(cell);
            if (value == null) continue;
            String key = headers.get(cell.getColumnIndex());
            if (key == null) throw invalid(row.getSheet().getSheetName(), row.getRowNum() + 1, "Value outside declared columns");
            result.put(key, value);
        }
        return result;
    }

    private static Object value(Cell cell) {
        return switch (cell.getCellType()) {
            case BLANK -> null;
            case STRING -> cell.getStringCellValue().isBlank() ? null : cell.getStringCellValue();
            case NUMERIC -> cell.getNumericCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            default -> throw invalid(cell.getSheet().getSheetName(), cell.getRowIndex() + 1,
                    "Formula/error cells are not accepted: " + cell.getAddress());
        };
    }

    private static IllegalArgumentException invalid(String sheet, int row, String message) {
        return new IllegalArgumentException(sheet + "!" + row + ": " + message);
    }
}

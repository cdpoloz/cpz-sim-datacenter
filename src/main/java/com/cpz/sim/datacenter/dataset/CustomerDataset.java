package com.cpz.sim.datacenter.dataset;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

/** Immutable imported observations, including fields that are not simulation inputs. */
public record CustomerDataset(Map<String, Object> metadata, Map<String, List<DatasetRow>> sheets) {
    public CustomerDataset {
        metadata = Map.copyOf(metadata);
        Map<String, List<DatasetRow>> copy = new LinkedHashMap<>();
        sheets.forEach((name, rows) -> copy.put(name, List.copyOf(rows)));
        sheets = Collections.unmodifiableMap(copy);
    }
    public List<DatasetRow> rows(String sheet) { return sheets.getOrDefault(sheet, List.of()); }
}

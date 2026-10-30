package com.cpz.sim.datacenter.dataset;

/** Stable, source-located validation diagnostic; no data is silently repaired. */
public record DatasetIssue(Severity severity, String sheet, int excelRow, String field, String message) {
    public enum Severity { ERROR, WARNING }
}

package com.cpz.sim.datacenter.dataset;

import java.util.Map;

/** One source row, preserving native text, double and boolean values. Blanks are absent. */
public record DatasetRow(String sheet, int excelRow, Map<String, Object> values) {
    public DatasetRow { values = Map.copyOf(values); }
    public String text(String field) {
        Object value = values.get(field);
        return value instanceof String text ? text : null;
    }
    public Double number(String field) {
        Object value = values.get(field);
        return value instanceof Number number ? number.doubleValue() : null;
    }
}

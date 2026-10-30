package com.cpz.sim.datacenter.dataset;
import java.util.*;
/** Field contract matching the proposed English dataset workbook. */
final class DatasetSchema {
    private DatasetSchema() {}
    static final Map<String, List<String>> HEADERS;
    static final Map<String, Map<String, Field>> FIELDS;
    record Field(String unit, boolean required, List<String> allowed) {}
    static final Set<String> METADATA = Set.of("schemaVersion", "datasetId", "partId", "configurationId", "configurationVersion", "roomCode", "sourceName", "sourceTimezone", "normalizedAtUtc", "nominalSamplingIntervalSeconds", "coverageStartUtc", "coverageEndExclusiveUtc", "sourceManifest", "normalizationNotes");
    static {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Map<String, Map<String, Field>> fields = new LinkedHashMap<>();
        headers.put("EntityMapping", List.of("entityType", "sourceEntityId", "roomCode", "column", "rackCode", "slot", "unitCode", "unitType", "notes"));
        { Map<String, Field> row = new LinkedHashMap<>();
          row.put("entityType", new Field("\u2014", true, List.of("SERVER", "RACK", "ROOM", "COOLING_UNIT")));
          row.put("sourceEntityId", new Field("\u2014", true, List.of()));
          row.put("roomCode", new Field("\u2014", true, List.of()));
          row.put("column", new Field("\u2014", false, List.of()));
          row.put("rackCode", new Field("\u2014", false, List.of()));
          row.put("slot", new Field("\u2014", false, List.of()));
          row.put("unitCode", new Field("\u2014", false, List.of()));
          row.put("unitType", new Field("\u2014", false, List.of("SUPPLY", "EXHAUST", "RACK")));
          row.put("notes", new Field("\u2014", false, List.of()));
          fields.put("EntityMapping", Collections.unmodifiableMap(row)); }
        headers.put("ServerSamples", List.of("timestampUtc", "roomCode", "column", "rackCode", "slot", "sourceEntityId", "utilizationFraction", "electricalPowerWatts", "temperatureCelsius", "status", "sampleKind", "intervalSeconds", "quality", "provenance", "sourceRef", "notes"));
        { Map<String, Field> row = new LinkedHashMap<>();
          row.put("timestampUtc", new Field("UTC ISO 8601", true, List.of()));
          row.put("roomCode", new Field("\u2014", true, List.of()));
          row.put("column", new Field("\u2014", true, List.of()));
          row.put("rackCode", new Field("\u2014", true, List.of()));
          row.put("slot", new Field("\u2014", true, List.of()));
          row.put("sourceEntityId", new Field("\u2014", false, List.of()));
          row.put("utilizationFraction", new Field("0\u20131", false, List.of()));
          row.put("electricalPowerWatts", new Field("W", false, List.of()));
          row.put("temperatureCelsius", new Field("\u00b0C", false, List.of()));
          row.put("status", new Field("\u2014", false, List.of("OK", "ALERT", "OFFLINE")));
          row.put("sampleKind", new Field("\u2014", true, List.of("INSTANTANEOUS", "INTERVAL_MEAN", "UNSPECIFIED")));
          row.put("intervalSeconds", new Field("s", false, List.of()));
          row.put("quality", new Field("\u2014", true, List.of("VALID", "SUSPECT", "INVALID", "MISSING", "UNASSESSED")));
          row.put("provenance", new Field("\u2014", true, List.of("MEASURED", "CALCULATED", "ESTIMATED", "ASSUMED", "UNSPECIFIED")));
          row.put("sourceRef", new Field("\u2014", false, List.of()));
          row.put("notes", new Field("\u2014", false, List.of()));
          fields.put("ServerSamples", Collections.unmodifiableMap(row)); }
        headers.put("RackSamples", List.of("timestampUtc", "roomCode", "column", "rackCode", "sourceEntityId", "utilizationFraction", "utilizationBasis", "electricalPowerWatts", "electricalScope", "inletAirTemperatureCelsius", "outletAirTemperatureCelsius", "sampleKind", "intervalSeconds", "quality", "provenance", "sourceRef", "notes"));
        { Map<String, Field> row = new LinkedHashMap<>();
          row.put("timestampUtc", new Field("UTC ISO 8601", true, List.of()));
          row.put("roomCode", new Field("\u2014", true, List.of()));
          row.put("column", new Field("\u2014", true, List.of()));
          row.put("rackCode", new Field("\u2014", true, List.of()));
          row.put("sourceEntityId", new Field("\u2014", false, List.of()));
          row.put("utilizationFraction", new Field("0\u20131", false, List.of()));
          row.put("utilizationBasis", new Field("\u2014", false, List.of("MEAN_SERVER", "CAPACITY_WEIGHTED", "REPORTED_OTHER", "UNSPECIFIED")));
          row.put("electricalPowerWatts", new Field("W", false, List.of()));
          row.put("electricalScope", new Field("\u2014", false, List.of("IT_ONLY", "IT_PLUS_RACK_COOLING", "OTHER", "UNSPECIFIED")));
          row.put("inletAirTemperatureCelsius", new Field("\u00b0C", false, List.of()));
          row.put("outletAirTemperatureCelsius", new Field("\u00b0C", false, List.of()));
          row.put("sampleKind", new Field("\u2014", true, List.of("INSTANTANEOUS", "INTERVAL_MEAN", "UNSPECIFIED")));
          row.put("intervalSeconds", new Field("s", false, List.of()));
          row.put("quality", new Field("\u2014", true, List.of("VALID", "SUSPECT", "INVALID", "MISSING", "UNASSESSED")));
          row.put("provenance", new Field("\u2014", true, List.of("MEASURED", "CALCULATED", "ESTIMATED", "ASSUMED", "UNSPECIFIED")));
          row.put("sourceRef", new Field("\u2014", false, List.of()));
          row.put("notes", new Field("\u2014", false, List.of()));
          fields.put("RackSamples", Collections.unmodifiableMap(row)); }
        headers.put("RoomSamples", List.of("timestampUtc", "roomCode", "sourceEntityId", "airTemperatureCelsius", "itElectricalPowerWatts", "coolingElectricalPowerWatts", "facilityElectricalPowerWatts", "powerBoundaryNotes", "sampleKind", "intervalSeconds", "quality", "provenance", "sourceRef", "notes"));
        { Map<String, Field> row = new LinkedHashMap<>();
          row.put("timestampUtc", new Field("UTC ISO 8601", true, List.of()));
          row.put("roomCode", new Field("\u2014", true, List.of()));
          row.put("sourceEntityId", new Field("\u2014", false, List.of()));
          row.put("airTemperatureCelsius", new Field("\u00b0C", false, List.of()));
          row.put("itElectricalPowerWatts", new Field("W", false, List.of()));
          row.put("coolingElectricalPowerWatts", new Field("W", false, List.of()));
          row.put("facilityElectricalPowerWatts", new Field("W", false, List.of()));
          row.put("powerBoundaryNotes", new Field("\u2014", false, List.of()));
          row.put("sampleKind", new Field("\u2014", true, List.of("INSTANTANEOUS", "INTERVAL_MEAN", "UNSPECIFIED")));
          row.put("intervalSeconds", new Field("s", false, List.of()));
          row.put("quality", new Field("\u2014", true, List.of("VALID", "SUSPECT", "INVALID", "MISSING", "UNASSESSED")));
          row.put("provenance", new Field("\u2014", true, List.of("MEASURED", "CALCULATED", "ESTIMATED", "ASSUMED", "UNSPECIFIED")));
          row.put("sourceRef", new Field("\u2014", false, List.of()));
          row.put("notes", new Field("\u2014", false, List.of()));
          fields.put("RoomSamples", Collections.unmodifiableMap(row)); }
        headers.put("CoolingSamples", List.of("timestampUtc", "roomCode", "unitType", "unitCode", "sourceEntityId", "isEnabled", "electricalPowerWatts", "airflowCubicMetersPerSecond", "supplyAirTemperatureCelsius", "returnAirTemperatureCelsius", "removedHeatWatts", "sampleKind", "intervalSeconds", "quality", "provenance", "sourceRef", "notes"));
        { Map<String, Field> row = new LinkedHashMap<>();
          row.put("timestampUtc", new Field("UTC ISO 8601", true, List.of()));
          row.put("roomCode", new Field("\u2014", true, List.of()));
          row.put("unitType", new Field("\u2014", true, List.of("SUPPLY", "EXHAUST", "RACK")));
          row.put("unitCode", new Field("\u2014", true, List.of()));
          row.put("sourceEntityId", new Field("\u2014", false, List.of()));
          row.put("isEnabled", new Field("boolean", false, List.of()));
          row.put("electricalPowerWatts", new Field("W", false, List.of()));
          row.put("airflowCubicMetersPerSecond", new Field("m\u00b3/s", false, List.of()));
          row.put("supplyAirTemperatureCelsius", new Field("\u00b0C", false, List.of()));
          row.put("returnAirTemperatureCelsius", new Field("\u00b0C", false, List.of()));
          row.put("removedHeatWatts", new Field("W thermal", false, List.of()));
          row.put("sampleKind", new Field("\u2014", true, List.of("INSTANTANEOUS", "INTERVAL_MEAN", "UNSPECIFIED")));
          row.put("intervalSeconds", new Field("s", false, List.of()));
          row.put("quality", new Field("\u2014", true, List.of("VALID", "SUSPECT", "INVALID", "MISSING", "UNASSESSED")));
          row.put("provenance", new Field("\u2014", true, List.of("MEASURED", "CALCULATED", "ESTIMATED", "ASSUMED", "UNSPECIFIED")));
          row.put("sourceRef", new Field("\u2014", false, List.of()));
          row.put("notes", new Field("\u2014", false, List.of()));
          fields.put("CoolingSamples", Collections.unmodifiableMap(row)); }
        HEADERS = Collections.unmodifiableMap(headers);
        FIELDS = Collections.unmodifiableMap(fields);
    }
}

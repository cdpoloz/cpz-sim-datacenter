package com.cpz.sim.datacenter.dataset;

import com.cpz.sim.datacenter.model.*;
import java.time.*;
import java.util.*;
import static com.cpz.sim.datacenter.dataset.DatasetIssue.Severity.*;

/** Validates without inventing identities, filling gaps or changing source values. */
public final class CustomerDatasetValidator {
    public List<DatasetIssue> validate(CustomerDataset dataset, DatasetBinding binding) {
        Objects.requireNonNull(dataset, "dataset");
        List<DatasetIssue> issues = new ArrayList<>();
        Map<String, Object> metadata = dataset.metadata();
        if (!DatasetSchema.METADATA.containsAll(metadata.keySet()))
            issues.add(new DatasetIssue(ERROR, "Dataset", 0, "", "Unknown metadata field"));
        if (!DatasetSchema.HEADERS.keySet().containsAll(dataset.sheets().keySet()))
            issues.add(new DatasetIssue(ERROR, "Dataset", 0, "", "Unknown observation worksheet"));
        for (String key : List.of("schemaVersion", "datasetId", "partId", "configurationId", "configurationVersion", "roomCode"))
            if (!(metadata.get(key) instanceof String text) || text.isBlank())
                issues.add(new DatasetIssue(ERROR, "Dataset", 0, key, "Non-blank text is required"));
        if (!"0.1-proposed".equals(metadata.get("schemaVersion")))
            issues.add(new DatasetIssue(ERROR, "Dataset", 0, "schemaVersion", "Unsupported dataset schema version"));
        for (var item : metadata.entrySet()) {
            if (item.getKey().equals("nominalSamplingIntervalSeconds")) {
                if (!(item.getValue() instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() <= 0)
                    issues.add(new DatasetIssue(ERROR, "Dataset", 0, item.getKey(), "Expected finite seconds > 0"));
            } else if (!(item.getValue() instanceof String))
                issues.add(new DatasetIssue(ERROR, "Dataset", 0, item.getKey(), "Expected text"));
            if (item.getKey().endsWith("Utc") && instant(item.getValue()) == null)
                issues.add(new DatasetIssue(ERROR, "Dataset", 0, item.getKey(), "Expected UTC ISO 8601 text ending in Z"));
        }
        Instant coverageStart = instant(metadata.get("coverageStartUtc"));
        Instant coverageEnd = instant(metadata.get("coverageEndExclusiveUtc"));
        if (coverageStart != null && coverageEnd != null && !coverageEnd.isAfter(coverageStart))
            issues.add(new DatasetIssue(ERROR, "Dataset", 0, "coverageEndExclusiveUtc", "End must be after start"));
        if (binding == null) issues.add(new DatasetIssue(WARNING, "Dataset", 0, "configurationId", "No installation binding supplied; not ready for replay"));
        else {
            for (var entry : Map.of("configurationId", binding.configurationId(),
                    "configurationVersion", binding.configurationVersion(), "roomCode", binding.roomCode()).entrySet())
                if (!entry.getValue().equals(metadata.get(entry.getKey())))
                    issues.add(new DatasetIssue(ERROR, "Dataset", 0, entry.getKey(), "Does not match installation binding"));
        }
        Map<ServerLocation, Server> servers = new HashMap<>();
        Set<RackLocation> racks = new HashSet<>();
        if (binding != null) {
            binding.datacenter().getServers().forEach(s -> servers.put(s.getLocation(), s));
            binding.datacenter().getRacks().forEach(r -> racks.add(r.getLocation()));
        }
        Map<String, DatasetRow> mappings = new HashMap<>();
        for (DatasetRow row : dataset.rows("EntityMapping"))
            if (row.text("entityType") != null && row.text("sourceEntityId") != null)
                mappings.putIfAbsent(row.text("entityType") + "/" + row.text("sourceEntityId"), row);
        for (var sheet : DatasetSchema.FIELDS.entrySet()) {
            Set<String> keys = new HashSet<>();
            Map<String, NavigableMap<Instant, DatasetRow>> intervals = new HashMap<>();
            for (DatasetRow row : dataset.rows(sheet.getKey())) {
                Map<String, Object> values = row.values();
                for (var item : sheet.getValue().entrySet()) {
                    String key = item.getKey(); var field = item.getValue(); Object value = values.get(key);
                    if (value == null) {
                        if (field.required()) error(issues, row, key, "Required value is missing");
                        continue;
                    }
                    boolean numeric = Set.of("W", "W thermal", "m³/s", "°C", "s", "0–1").contains(field.unit());
                    if (numeric) {
                        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()))
                            error(issues, row, key, "Expected a finite native number; numeric text is not accepted");
                        else if ((field.unit().equals("0–1") && (n.doubleValue() < 0 || n.doubleValue() > 1))
                                || (!field.unit().equals("°C") && n.doubleValue() < 0))
                            error(issues, row, key, "Value is outside the declared unit/range");
                    } else if (key.equals("isEnabled")) {
                        if (!(value instanceof Boolean)) error(issues, row, key, "Expected a native boolean");
                    } else if (!(value instanceof String text) || text.isBlank()) error(issues, row, key, "Expected non-blank text");
                    if (!field.allowed().isEmpty() && !field.allowed().contains(value)) error(issues, row, key, "Unknown enum value");
                }
                if (!sheet.getValue().keySet().containsAll(values.keySet())) error(issues, row, "", "Unknown observation field");
                if (row.text("roomCode") != null && !Objects.equals(row.text("roomCode"), metadata.get("roomCode")))
                    error(issues, row, "roomCode", "Does not match dataset room");
                boolean mapping = sheet.getKey().equals("EntityMapping");
                String kind = mapping ? row.text("entityType") : sheet.getKey().replace("Samples", "").toUpperCase(Locale.ROOT);
                if ("COOLING".equals(kind)) kind = "COOLING_UNIT";
                if (Set.of("SERVER", "RACK").contains(kind == null ? "" : kind)) {
                    for (String field : List.of("column", "rackCode")) if (row.text(field) == null && !sheet.getValue().get(field).required())
                        error(issues, row, field, "Canonical rack identity is required");
                    if ("SERVER".equals(kind) && row.text("slot") == null && !sheet.getValue().get("slot").required())
                        error(issues, row, "slot", "Server slot is required");
                    if (row.text("column") != null && row.text("rackCode") != null && binding != null) {
                        RackLocation rack = new RackLocation(row.text("column"), new RackCode(row.text("rackCode")));
                        if (!racks.contains(rack)) error(issues, row, "rackCode", "Unknown rack location in installation");
                        if ("SERVER".equals(kind) && row.text("slot") != null) {
                            Server server = servers.get(new ServerLocation(row.text("column"), row.text("rackCode"), row.text("slot")));
                            if (server == null) error(issues, row, "slot", "Unknown installed server");
                            else if (row.number("electricalPowerWatts") != null && row.number("electricalPowerWatts") > server.getConfig().maxPowerWatts())
                                error(issues, row, "electricalPowerWatts", "Exceeds configured maximum; source value has not been clamped");
                        }
                    }
                }
                if ("COOLING_UNIT".equals(kind)) {
                    for (String field : List.of("unitType", "unitCode")) if (row.text(field) == null) error(issues, row, field, "Cooling identity is required");
                    if (binding != null && !binding.coolingUnitKeys().contains(row.text("unitType") + ":" + row.text("unitCode")))
                        error(issues, row, "unitCode", "Unknown cooling unit in installation binding");
                }
                if (mapping) {
                    String key = row.text("entityType") + "/" + row.text("sourceEntityId");
                    if (!keys.add(key)) error(issues, row, "sourceEntityId", "Duplicate mapping identity");
                    continue;
                }
                if (row.text("sourceEntityId") != null) {
                    DatasetRow crosswalk = mappings.get(kind + "/" + row.text("sourceEntityId"));
                    if (crosswalk != null) for (String field : List.of("roomCode", "column", "rackCode", "slot", "unitType", "unitCode")) {
                        if (!Objects.equals(row.values().get(field), crosswalk.values().get(field)))
                            error(issues, row, field, "Sample identity conflicts with EntityMapping");
                    }
                }
                Instant time = instant(values.get("timestampUtc"));
                if (time == null) error(issues, row, "timestampUtc", "Expected UTC ISO 8601 text ending in Z");
                else {
                    if ((coverageStart != null && time.isBefore(coverageStart)) || (coverageEnd != null && !time.isBefore(coverageEnd)))
                        error(issues, row, "timestampUtc", "Outside declared dataset coverage");
                    String identity = identity(row);
                    if (!keys.add(identity + "/" + time)) error(issues, row, "timestampUtc", "Duplicate entity/timestamp key");
                    intervals.computeIfAbsent(identity, ignored -> new TreeMap<>()).put(time, row);
                }
                Double duration = row.number("intervalSeconds");
                if ("INTERVAL_MEAN".equals(row.text("sampleKind"))) {
                    if (duration == null || !Double.isFinite(duration) || duration <= 0 || duration > Long.MAX_VALUE / 1e9)
                        error(issues, row, "intervalSeconds", "A representable positive interval duration is required");
                } else if (values.containsKey("intervalSeconds")) error(issues, row, "intervalSeconds", "Must be blank unless sampleKind is INTERVAL_MEAN");
                if ("UNSPECIFIED".equals(row.text("sampleKind"))) warning(issues, row, "sampleKind", "Temporal meaning is unresolved; not ready for replay");
                if (!"VALID".equals(row.text("quality"))) warning(issues, row, "quality", "Observation is not approved for replay");
                if ("UNSPECIFIED".equals(row.text("provenance"))) warning(issues, row, "provenance", "Source origin is unresolved");
                boolean metric = sheet.getValue().entrySet().stream().anyMatch(e -> e.getValue().unit().equals("boolean") && values.containsKey(e.getKey())
                        || !Set.of("—", "s", "UTC ISO 8601").contains(e.getValue().unit()) && values.containsKey(e.getKey())) || values.containsKey("status");
                if ("MISSING".equals(row.text("quality")) ? metric : !metric)
                    error(issues, row, "quality", "MISSING rows must have no measurements/state; other rows need at least one");
                if (sheet.getKey().equals("RackSamples")) {
                    if (values.containsKey("utilizationFraction") && !values.containsKey("utilizationBasis")) error(issues, row, "utilizationBasis", "Required with rack utilization");
                    if (values.containsKey("electricalPowerWatts") && !values.containsKey("electricalScope")) error(issues, row, "electricalScope", "Required with rack power");
                    if (("REPORTED_OTHER".equals(row.text("utilizationBasis")) || "OTHER".equals(row.text("electricalScope"))) && row.text("notes") == null)
                        error(issues, row, "notes", "Explain nonstandard aggregate/boundary");
                }
                if (sheet.getKey().equals("RoomSamples") && values.keySet().stream().anyMatch(k -> k.endsWith("ElectricalPowerWatts")) && row.text("powerBoundaryNotes") == null)
                    error(issues, row, "powerBoundaryNotes", "Define measured power boundaries");
                if ("EXHAUST".equals(row.text("unitType")) && (values.containsKey("supplyAirTemperatureCelsius") || values.containsKey("removedHeatWatts")))
                    error(issues, row, "unitType", "EXHAUST has no supplied air or active removed-heat field");
                if ("OFFLINE".equals(row.text("status")) && ((row.number("electricalPowerWatts") != null && row.number("electricalPowerWatts") != 0)
                        || (row.number("utilizationFraction") != null && row.number("utilizationFraction") != 0)))
                    error(issues, row, "status", "OFFLINE conflicts with nonzero utilization/power");
            }
            for (var timeline : intervals.values()) {
                Instant previousEnd = null;
                for (var entry : timeline.entrySet()) {
                    DatasetRow row = entry.getValue();
                    if (previousEnd != null && entry.getKey().isBefore(previousEnd)) error(issues, row, "timestampUtc", "Overlapping entity intervals");
                    Double duration = row.number("intervalSeconds");
                    if ("INTERVAL_MEAN".equals(row.text("sampleKind")) && duration != null && Double.isFinite(duration) && duration > 0 && duration <= Long.MAX_VALUE / 1e9) {
                        try {
                            Instant end = entry.getKey().plusNanos((long) (duration * 1e9));
                            if (!end.isAfter(entry.getKey())) error(issues, row, "intervalSeconds", "Interval must be at least one nanosecond");
                            if (coverageEnd != null && end.isAfter(coverageEnd)) error(issues, row, "intervalSeconds", "Interval extends beyond declared coverage");
                            if (previousEnd == null || end.isAfter(previousEnd)) previousEnd = end;
                        }
                        catch (DateTimeException | ArithmeticException e) { error(issues, row, "intervalSeconds", "Interval end cannot be represented"); }
                    }
                }
            }
        }
        return List.copyOf(issues);
    }

    static Instant instant(Object value) {
        if (!(value instanceof String text) || !text.endsWith("Z")) return null;
        try { return Instant.parse(text); } catch (DateTimeException e) { return null; }
    }
    private static String identity(DatasetRow row) {
        // Length-prefixed components prevent collisions even with punctuation in opaque codes.
        StringBuilder key = new StringBuilder();
        for (String field : List.of("roomCode", "column", "rackCode", "slot", "unitType", "unitCode")) {
            String value = Objects.toString(row.values().get(field), ""); key.append(value.length()).append(':').append(value);
        }
        return key.toString();
    }
    private static void error(List<DatasetIssue> issues, DatasetRow row, String field, String message) {
        issues.add(new DatasetIssue(ERROR, row.sheet(), row.excelRow(), field, message));
    }
    private static void warning(List<DatasetIssue> issues, DatasetRow row, String field, String message) {
        issues.add(new DatasetIssue(WARNING, row.sheet(), row.excelRow(), field, message));
    }
}

package com.cpz.sim.datacenter.config.validation;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.HealthSystemOptionsDefinition;
import com.cpz.sim.datacenter.config.definition.HealthThresholdDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.RackSlotResolver;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.config.definition.TemperatureSystemOptionsDefinition;
import com.cpz.sim.datacenter.model.HardwareStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author CPZ
 */
public final class DatacenterConfigValidator {

    private static void validateDatacenterFields(DatacenterDefinition definition, List<String> errors) {
        if (isBlank(definition.name())) errors.add("Datacenter name cannot be null or blank");
        if (definition.layout() == null) errors.add("Datacenter layout cannot be null");
        if (definition.serverModels() == null) errors.add("Server models list cannot be null");
        if (definition.servers() == null) errors.add("Servers list cannot be null");
    }

    private record RackKey(String column, String rackCode) {
        String code() {
            return column + "/" + rackCode;
        }
    }

    private record LayoutIndex(
            Map<RackKey, RackDefinition> racksByLocation,
            Map<String, List<RackDefinition>> racksByCode,
            Set<String> columns
    ) {
    }

    private record ServerRackReference(String column, RackDefinition rack) {
    }

    private static LayoutIndex validateLayout(DatacenterLayoutDefinition layout, List<String> errors) {
        Map<RackKey, RackDefinition> racksByLocation = new HashMap<>();
        Map<String, List<RackDefinition>> racksByCode = new HashMap<>();
        Set<String> columns = new HashSet<>();
        if (layout == null) return new LayoutIndex(racksByLocation, racksByCode, columns);
        if (layout.racks() == null) {
            errors.add("Layout racks list cannot be null");
            return new LayoutIndex(racksByLocation, racksByCode, columns);
        }
        for (int i = 0; i < layout.racks().size(); i++) {
            RackDefinition rack = layout.racks().get(i);
            if (rack == null) {
                errors.add("Rack at index " + i + " cannot be null");
                continue;
            }
            String context = "Rack at index " + i;
            if (isBlank(rack.code())) {
                errors.add(context + " must have a non-blank code");
            } else {
                racksByCode.computeIfAbsent(rack.code(), ignored -> new ArrayList<>()).add(rack);
            }
            if (isBlank(rack.column())) errors.add(context + " must have a non-blank column");
            else columns.add(rack.column());
            if (!isBlank(rack.column()) && !isBlank(rack.code())) {
                RackKey rackKey = new RackKey(rack.column(), rack.code());
                if (racksByLocation.putIfAbsent(rackKey, rack) != null)
                    errors.add("Duplicated rack location: " + rackKey.code());
            }
            if (isBlank(rack.row())) errors.add(context + " must have a non-blank row");
            validateRackSlots(rack, context, errors);
        }
        return new LayoutIndex(racksByLocation, racksByCode, columns);
    }

    private static void validateRackSlots(RackDefinition rack, String context, List<String> errors) {
        if (rack.hasSlotCount() == rack.hasSlots()) {
            errors.add(context + " must define exactly one of slotCount or slots");
            return;
        }
        if (rack.hasSlotCount()) {
            if (rack.slotCount() == null || rack.slotCount() <= 0) errors.add(context + " must have slotCount > 0");
            return;
        }
        if (rack.slots() == null || rack.slots().isEmpty()) {
            errors.add(context + " must have a non-empty slots list");
            return;
        }
        Set<String> slotCodes = new HashSet<>();
        for (int i = 0; i < rack.slots().size(); i++) {
            String slotCode = rack.slots().get(i);
            if (slotCode == null) {
                errors.add(context + " has null slot code at index " + i);
                continue;
            }
            if (slotCode.isBlank()) {
                errors.add(context + " has blank slot code at index " + i);
                continue;
            }
            if (!slotCodes.add(slotCode)) {
                String rackContext = isBlank(rack.code()) ? context : "Rack " + rack.code();
                errors.add(rackContext + " contains duplicate slot code: " + slotCode);
            }
        }
    }

    private static void validateServerModels(List<ServerModelDefinition> serverModels, List<String> errors) {
        if (serverModels == null) return;
        Set<String> modelCodes = new HashSet<>();
        for (int i = 0; i < serverModels.size(); i++) {
            ServerModelDefinition model = serverModels.get(i);
            if (model == null) {
                errors.add("Server model at index " + i + " cannot be null");
                continue;
            }
            String context = "Server model at index " + i;
            if (isBlank(model.modelCode())) errors.add(context + " must have a non-blank modelCode");
            else if (!modelCodes.add(model.modelCode()))
                errors.add("Duplicated server modelCode: " + model.modelCode());
            if (isBlank(model.manufacturer())) errors.add(context + " must have a non-blank manufacturer");
            if (isBlank(model.model())) errors.add(context + " must have a non-blank model");
            if (!Float.isFinite(model.idlePowerWatts()) || model.idlePowerWatts() < 0.0f)
                errors.add(context + " must have finite idlePowerWatts >= 0");
            if (!Float.isFinite(model.maxPowerWatts()) || model.maxPowerWatts() < 0.0f)
                errors.add(context + " must have finite maxPowerWatts >= 0");
            if (Float.isFinite(model.idlePowerWatts()) && Float.isFinite(model.maxPowerWatts()) && model.maxPowerWatts() <= model.idlePowerWatts())
                errors.add(context + " must have maxPowerWatts > idlePowerWatts");
            validateServerModelThermalProperties(model, context, errors);
        }
    }

    private static void validateServerModelThermalProperties(
            ServerModelDefinition model,
            String context,
            List<String> errors
    ) {
        Double thermalCapacity = model.thermalCapacityJoulesPerCelsius();
        Double heatDissipation = model.heatDissipationWattsPerCelsius();
        if (thermalCapacity == null && heatDissipation == null) return;
        if (thermalCapacity == null || heatDissipation == null) {
            errors.add(
                    context + " must specify both thermalCapacityJoulesPerCelsius"
                            + " and heatDissipationWattsPerCelsius, or neither"
            );
            return;
        }
        if (!Double.isFinite(thermalCapacity) || thermalCapacity <= 0.0) {
            errors.add(context + " must have finite thermalCapacityJoulesPerCelsius > 0");
        }
        if (!Double.isFinite(heatDissipation) || heatDissipation <= 0.0) {
            errors.add(context + " must have finite heatDissipationWattsPerCelsius > 0");
        }
    }

    private static void validateServers(
            List<ServerDefinition> servers,
            List<ServerModelDefinition> serverModels,
            LayoutIndex layout,
            List<String> errors
    ) {
        if (servers == null) return;
        Set<String> knownModelCodes = collectKnownModelCodes(serverModels);
        Set<String> locations = new HashSet<>();
        for (int i = 0; i < servers.size(); i++) {
            ServerDefinition server = servers.get(i);
            if (server == null) {
                errors.add("Server at index " + i + " cannot be null");
                continue;
            }
            String context = "Server at index " + i;
            validateRequiredServerFields(server, context, errors);
            ServerRackReference rackReference = resolveServerRackReference(server, layout, context, errors);
            validateServerSlot(server, rackReference, context, errors);
            validateServerStatus(server, context, errors);
            validateServerModelReference(server, knownModelCodes, context, errors);
            validateWorkloadFactor(server, context, errors);
            validateDuplicatedLocation(server, rackReference, locations, context, errors);
        }
    }

    private static void validateRequiredServerFields(ServerDefinition server, String context, List<String> errors) {
        if (server.column() != null && server.column().isBlank()) errors.add(context + " must have a non-blank column");
        if (isBlank(server.rackCode())) errors.add(context + " must have a non-blank rackCode");
        if (isBlank(server.slot())) errors.add(context + " must have a non-blank slot");
        if (isBlank(server.modelCode())) errors.add(context + " must have a non-blank modelCode");
        if (isBlank(server.status())) errors.add(context + " must have a non-blank status");
    }

    private static ServerRackReference resolveServerRackReference(
            ServerDefinition server,
            LayoutIndex layout,
            String context,
            List<String> errors
    ) {
        if (isBlank(server.rackCode())) return null;
        if (!isBlank(server.column())) {
            RackDefinition rack = layout.racksByLocation().get(new RackKey(server.column(), server.rackCode()));
            if (rack != null) return new ServerRackReference(server.column(), rack);
            if (!layout.columns().contains(server.column())) {
                errors.add(context + " references unknown column: " + server.column());
            } else {
                errors.add(context + " references unknown rack " + server.rackCode() + " in column " + server.column());
            }
            return null;
        }
        List<RackDefinition> racks = layout.racksByCode().get(server.rackCode());
        if (racks == null || racks.isEmpty()) {
            errors.add(context + " references unknown rackCode: " + server.rackCode());
            return null;
        }
        if (racks.size() > 1) {
            errors.add(context + " must define column because rack code " + server.rackCode() + " exists in multiple columns");
            return null;
        }
        RackDefinition rack = racks.getFirst();
        return new ServerRackReference(rack.column(), rack);
    }

    private static void validateServerSlot(
            ServerDefinition server,
            ServerRackReference rackReference,
            String context,
            List<String> errors
    ) {
        if (isBlank(server.slot())) return;
        if (rackReference == null) return;
        RackDefinition rack = rackReference.rack();
        if (!canResolveSlots(rack)) return;
        if (!RackSlotResolver.resolveSlotCodes(rack).contains(server.slot())) {
            errors.add(context + " references unknown slot " + server.slot()
                    + " in rack " + server.rackCode() + ", column " + rackReference.column());
        }
    }

    private static boolean canResolveSlots(RackDefinition rack) {
        if (rack.hasSlotCount() == rack.hasSlots()) return false;
        if (rack.hasSlotCount()) return rack.slotCount() != null && rack.slotCount() > 0;
        if (rack.slots() == null || rack.slots().isEmpty()) return false;
        Set<String> slotCodes = new HashSet<>();
        for (String slotCode : rack.slots()) {
            if (isBlank(slotCode) || !slotCodes.add(slotCode)) return false;
        }
        return true;
    }

    private static void validateServerStatus(ServerDefinition server, String context, List<String> errors) {
        if (!isBlank(server.status()) && !isValidEnumValue(HardwareStatus.class, server.status()))
            errors.add(context + " has invalid status: " + server.status());
    }

    private static void validateServerModelReference(ServerDefinition server, Set<String> knownModelCodes, String context, List<String> errors) {
        if (isBlank(server.modelCode())) {
            return;
        }
        if (!knownModelCodes.contains(server.modelCode()))
            errors.add(context + " references unknown modelCode: " + server.modelCode());
    }

    private static void validateDuplicatedLocation(
            ServerDefinition server,
            ServerRackReference rackReference,
            Set<String> locations,
            String context,
            List<String> errors
    ) {
        if (isBlank(server.rackCode()) || isBlank(server.slot())) return;
        if (rackReference == null) return;
        String locationKey = rackReference.column() + "/" + server.rackCode() + "/" + server.slot();
        if (!locations.add(locationKey)) errors.add("Duplicate server location: " + locationKey);
    }

    private static Set<String> collectKnownModelCodes(List<ServerModelDefinition> serverModels) {
        Set<String> knownModelCodes = new HashSet<>();
        if (serverModels == null) return knownModelCodes;
        for (ServerModelDefinition model : serverModels) {
            if (model != null && !isBlank(model.modelCode())) knownModelCodes.add(model.modelCode());
        }
        return knownModelCodes;
    }

    private static <E extends Enum<E>> boolean isValidEnumValue(Class<E> enumClass, String value) {
        try {
            Enum.valueOf(enumClass, value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void validateWorkloadFactor(ServerDefinition server, String context, List<String> errors) {
        if (!Float.isFinite(server.workloadFactor()) || server.workloadFactor() < 0.0f) {
            errors.add(context + " must have finite workloadFactor >= 0");
        }
    }

    private static void validateTemperature(
            TemperatureSystemOptionsDefinition temperature,
            List<String> errors
    ) {
        if (temperature == null) return;
        String context = "Temperature config";
        if (!Double.isFinite(temperature.ambientTemperatureCelsius()))
            errors.add(context + " must have finite ambientTemperatureCelsius");
        if (!Double.isFinite(temperature.defaultInitialTemperatureCelsius()))
            errors.add(context + " must have finite defaultInitialTemperatureCelsius");
        if (!Double.isFinite(temperature.thermalCapacityJoulesPerCelsius())
                || temperature.thermalCapacityJoulesPerCelsius() <= 0.0)
            errors.add(context + " must have finite thermalCapacityJoulesPerCelsius > 0");
        if (!Double.isFinite(temperature.heatDissipationWattsPerCelsius())
                || temperature.heatDissipationWattsPerCelsius() < 0.0)
            errors.add(context + " must have finite heatDissipationWattsPerCelsius >= 0");
    }

    public void validate(DatacenterDefinition definition) {
        List<String> errors = new ArrayList<>();
        if (definition == null) throw new DatacenterConfigValidationException("Datacenter definition cannot be null");
        validateDatacenterFields(definition, errors);
        LayoutIndex layout = validateLayout(definition.layout(), errors);
        validateServerModels(definition.serverModels(), errors);
        validateServers(definition.servers(), definition.serverModels(), layout, errors);
        validateTemperature(definition.temperature(), errors);
        validateHealth(definition.health(), errors);
        if (!errors.isEmpty())
            throw new DatacenterConfigValidationException(String.join(System.lineSeparator(), errors));
    }

    private static void validateHealth(HealthSystemOptionsDefinition health, List<String> errors) {
        if (health == null) return;
        if (health.utilization() == null) errors.add("Health config utilization threshold cannot be null");
        else validateUtilizationHealthThreshold(health.utilization(), errors);
        if (health.temperatureCelsius() == null) errors.add("Health config temperatureCelsius threshold cannot be null");
        else validateTemperatureHealthThreshold(health.temperatureCelsius(), errors);
    }

    private static void validateUtilizationHealthThreshold(HealthThresholdDefinition threshold, List<String> errors) {
        String context = "Health config utilization";
        validateHealthThreshold(threshold, context, errors);
        if (Double.isFinite(threshold.alertAtOrAbove()) && (threshold.alertAtOrAbove() < 0.0 || threshold.alertAtOrAbove() > 1.0)) {
            errors.add(context + " must have alertAtOrAbove within [0, 1]");
        }
        if (Double.isFinite(threshold.clearAtOrBelow()) && (threshold.clearAtOrBelow() < 0.0 || threshold.clearAtOrBelow() > 1.0)) {
            errors.add(context + " must have clearAtOrBelow within [0, 1]");
        }
    }

    private static void validateTemperatureHealthThreshold(HealthThresholdDefinition threshold, List<String> errors) {
        validateHealthThreshold(threshold, "Health config temperatureCelsius", errors);
    }

    private static void validateHealthThreshold(HealthThresholdDefinition threshold, String context, List<String> errors) {
        boolean alertFinite = Double.isFinite(threshold.alertAtOrAbove());
        boolean clearFinite = Double.isFinite(threshold.clearAtOrBelow());
        if (!alertFinite) errors.add(context + " must have finite alertAtOrAbove");
        if (!clearFinite) errors.add(context + " must have finite clearAtOrBelow");
        if (alertFinite && clearFinite && threshold.clearAtOrBelow() >= threshold.alertAtOrAbove())
            errors.add(context + " must have clearAtOrBelow < alertAtOrAbove");
    }
}

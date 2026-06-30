package com.cpz.sim.datacenter.config.validation;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterLayoutDefinition;
import com.cpz.sim.datacenter.config.definition.RackDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.model.HardwareStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author CPZ
 */
public final class DatacenterConfigValidator {

    private static final Pattern SLOT_PATTERN = Pattern.compile("^U(\\d+)$");

    private static void validateDatacenterFields(DatacenterDefinition definition, List<String> errors) {
        if (isBlank(definition.name())) errors.add("Datacenter name cannot be null or blank");
        if (definition.layout() == null) errors.add("Datacenter layout cannot be null");
        if (definition.serverModels() == null) errors.add("Server models list cannot be null");
        if (definition.servers() == null) errors.add("Servers list cannot be null");
    }

    private static Map<String, RackDefinition> validateLayout(DatacenterLayoutDefinition layout, List<String> errors) {
        Map<String, RackDefinition> racksByCode = new HashMap<>();
        if (layout == null) return racksByCode;
        if (layout.racks() == null) {
            errors.add("Layout racks list cannot be null");
            return racksByCode;
        }
        Set<String> rackCodes = new HashSet<>();
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
                if (!rackCodes.add(rack.code())) errors.add("Duplicated rack code: " + rack.code());
                racksByCode.put(rack.code(), rack);
            }
            if (isBlank(rack.column())) errors.add(context + " must have a non-blank column");
            if (isBlank(rack.row())) errors.add(context + " must have a non-blank row");
            if (rack.slotCount() <= 0) errors.add(context + " must have slotCount > 0");
        }
        return racksByCode;
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
        }
    }

    private static void validateServers(
            List<ServerDefinition> servers,
            List<ServerModelDefinition> serverModels,
            Map<String, RackDefinition> racksByCode,
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
            validateServerRackReference(server, racksByCode, context, errors);
            validateServerSlot(server, racksByCode, context, errors);
            validateServerStatus(server, context, errors);
            validateServerModelReference(server, knownModelCodes, context, errors);
            validateWorkloadFactor(server, context, errors);
            validateDuplicatedLocation(server, locations, context, errors);
        }
    }

    private static void validateRequiredServerFields(ServerDefinition server, String context, List<String> errors) {
        if (isBlank(server.rackCode())) errors.add(context + " must have a non-blank rackCode");
        if (isBlank(server.slot())) errors.add(context + " must have a non-blank slot");
        if (isBlank(server.modelCode())) errors.add(context + " must have a non-blank modelCode");
        if (isBlank(server.status())) errors.add(context + " must have a non-blank status");
    }

    private static void validateServerRackReference(
            ServerDefinition server,
            Map<String, RackDefinition> racksByCode,
            String context,
            List<String> errors
    ) {
        if (isBlank(server.rackCode())) return;
        if (!racksByCode.containsKey(server.rackCode()))
            errors.add(context + " references unknown rackCode: " + server.rackCode());
    }

    private static void validateServerSlot(
            ServerDefinition server,
            Map<String, RackDefinition> racksByCode,
            String context,
            List<String> errors
    ) {
        if (isBlank(server.slot())) return;
        Matcher matcher = SLOT_PATTERN.matcher(server.slot());
        if (!matcher.matches()) {
            errors.add(context + " has invalid slot: " + server.slot());
            return;
        }
        if (isBlank(server.rackCode())) return;
        RackDefinition rack = racksByCode.get(server.rackCode());
        if (rack == null) return;
        int slotNumber;
        try {
            slotNumber = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            errors.add(context + " has invalid slot: " + server.slot());
            return;
        }
        if (slotNumber < 1 || slotNumber > rack.slotCount()) {
            errors.add(context + " has slot outside rack range: " + server.slot());
        }
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
            Set<String> locations,
            String context,
            List<String> errors
    ) {
        if (isBlank(server.rackCode()) || isBlank(server.slot())) return;
        String locationKey = server.rackCode() + "-" + server.slot();
        if (!locations.add(locationKey)) errors.add(context + " has duplicated location: " + locationKey);
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

    public void validate(DatacenterDefinition definition) {
        List<String> errors = new ArrayList<>();
        if (definition == null) throw new DatacenterConfigValidationException("Datacenter definition cannot be null");
        validateDatacenterFields(definition, errors);
        Map<String, RackDefinition> racksByCode = validateLayout(definition.layout(), errors);
        validateServerModels(definition.serverModels(), errors);
        validateServers(definition.servers(), definition.serverModels(), racksByCode, errors);
        if (!errors.isEmpty())
            throw new DatacenterConfigValidationException(String.join(System.lineSeparator(), errors));
    }
}

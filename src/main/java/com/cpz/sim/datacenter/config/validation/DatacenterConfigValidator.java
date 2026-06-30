package com.cpz.sim.datacenter.config.validation;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.ServerDefinition;
import com.cpz.sim.datacenter.config.definition.ServerModelDefinition;
import com.cpz.sim.datacenter.model.Column;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.Row;
import com.cpz.sim.datacenter.model.Slot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author CPZ
 */
public final class DatacenterConfigValidator {

    private static void validateDatacenterFields(DatacenterDefinition definition, List<String> errors) {
        if (isBlank(definition.name())) errors.add("Datacenter name cannot be null or blank");
        if (definition.serverModels() == null) errors.add("Server models list cannot be null");
        if (definition.servers() == null) errors.add("Servers list cannot be null");
        if (definition.serverModels() != null && definition.serverModels().isEmpty())
            errors.add("Server models list cannot be empty");
        if (definition.servers() != null && definition.servers().isEmpty()) errors.add("Servers list cannot be empty");
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
            if (Float.isFinite(model.idlePowerWatts()) && Float.isFinite(model.maxPowerWatts()) && model.maxPowerWatts() < model.idlePowerWatts())
                errors.add(context + " must have maxPowerWatts >= idlePowerWatts");
        }
    }

    private static void validateServers(List<ServerDefinition> servers, List<ServerModelDefinition> serverModels, List<String> errors) {
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
            validateServerEnums(server, context, errors);
            validateServerModelReference(server, knownModelCodes, context, errors);
            validateWorkloadFactor(server, context, errors);
            validateDuplicatedLocation(server, locations, context, errors);
        }
    }

    private static void validateRequiredServerFields(ServerDefinition server, String context, List<String> errors) {
        if (isBlank(server.column())) errors.add(context + " must have a non-blank column");
        if (isBlank(server.row())) errors.add(context + " must have a non-blank row");
        if (isBlank(server.slot())) errors.add(context + " must have a non-blank slot");
        if (isBlank(server.modelCode())) errors.add(context + " must have a non-blank modelCode");
        if (isBlank(server.status())) errors.add(context + " must have a non-blank status");
    }

    private static void validateServerEnums(ServerDefinition server, String context, List<String> errors) {
        if (!isBlank(server.column()) && !isValidEnumValue(Column.class, server.column()))
            errors.add(context + " has invalid column: " + server.column());
        if (!isBlank(server.row()) && !isValidEnumValue(Row.class, server.row()))
            errors.add(context + " has invalid row: " + server.row());
        if (!isBlank(server.slot()) && !isValidEnumValue(Slot.class, server.slot()))
            errors.add(context + " has invalid slot: " + server.slot());
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
        if (isBlank(server.column()) || isBlank(server.row()) || isBlank(server.slot())) return;
        String locationKey = server.column() + "-" + server.row() + "-" + server.slot();
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
        validateServerModels(definition.serverModels(), errors);
        validateServers(definition.servers(), definition.serverModels(), errors);
        if (!errors.isEmpty())
            throw new DatacenterConfigValidationException(String.join(System.lineSeparator(), errors));
    }
}

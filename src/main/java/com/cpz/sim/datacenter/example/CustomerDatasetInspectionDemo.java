package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.dataset.*;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import java.nio.file.Path;
import java.util.*;

/** Console inspection of source observations; never advances or changes the simulation. */
public final class CustomerDatasetInspectionDemo {
    private CustomerDatasetInspectionDemo() {}
    public static void main(String[] args) {
        if (args.length != 1 && args.length != 4)
            throw new IllegalArgumentException("Usage: workbook.xlsx [datacenter.json configurationId configurationVersion]");
        CustomerDataset dataset = new XlsxCustomerDatasetLoader().load(Path.of(args[0]));
        DatasetBinding binding = null;
        if (args.length == 4) {
            var definition = new JsonDatacenterConfigLoader().load(Path.of(args[1]));
            if (definition.layout().room() == null) throw new IllegalArgumentException("Configuration needs room metadata for dataset binding");
            Set<String> cooling = new HashSet<>();
            if (definition.cooling() != null) {
                definition.cooling().supplyUnits().forEach(u -> cooling.add("SUPPLY:" + u.code()));
                definition.cooling().exhaustUnits().forEach(u -> cooling.add("EXHAUST:" + u.code()));
            }
            if (definition.rackCooling() != null) definition.rackCooling().units().forEach(u -> cooling.add("RACK:" + u.code()));
            binding = new DatasetBinding(args[2], args[3], definition.layout().room().code(),
                    new DatacenterFactory().create(definition), cooling);
        }
        System.out.println("Dataset: " + dataset.metadata().get("datasetId") + " | part: " + dataset.metadata().get("partId"));
        dataset.sheets().forEach((name, rows) -> System.out.println(name + ": " + rows.size() + " rows"));
        var diagnostics = new CustomerDatasetValidator().validate(dataset, binding);
        System.out.printf(Locale.ROOT, "Diagnostics: %d errors, %d warnings%n",
                diagnostics.stream().filter(i -> i.severity() == DatasetIssue.Severity.ERROR).count(),
                diagnostics.stream().filter(i -> i.severity() == DatasetIssue.Severity.WARNING).count());
        diagnostics.stream().limit(20).forEach(System.out::println);
        dataset.rows("ServerSamples").stream().limit(5).forEach(row -> System.out.println("SOURCE: " + row.values()));
        System.out.println("Inspection only. Source observations are not simulated results; review diagnostics before replay.");
    }
}

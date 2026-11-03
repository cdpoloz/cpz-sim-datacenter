package com.cpz.sim.datacenter.example;

import com.cpz.sim.datacenter.dataset.*;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

/** Bounded console replay through the production input pipeline. */
public final class CustomerDatasetReplayDemo {
    private CustomerDatasetReplayDemo() {}
    private static final String USAGE = "Usage: workbook.xlsx configuration.json configurationId configurationVersion "
            + "UTILIZATION_DRIVEN|POWER_DRIVEN maximumHold(ISO-duration) startUtc steps step(ISO-duration) "
            + "[serverCode[,serverCode...]]; steps 1..1000; at most 10 selected servers";

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    /** Returns 2 on any failure; output is buffered until the complete bounded run succeeds. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            execute(args, out);
            return 0;
        } catch (RuntimeException exception) {
            err.println("REPLAY_FAILED: " + exception.getMessage());
            return 2;
        }
    }

    private static void execute(String[] args, PrintStream out) {
        if (args.length != 9 && args.length != 10) throw new IllegalArgumentException(USAGE);
        var mode = DatacenterDataInputMode.valueOf(args[4]);
        if (mode == DatacenterDataInputMode.TEMPERATURE_DRIVEN)
            throw new IllegalArgumentException("Server replay supports only UTILIZATION_DRIVEN or POWER_DRIVEN");
        Duration hold = Duration.parse(args[5]);
        if (!args[6].endsWith("Z")) throw new IllegalArgumentException("startUtc must end in Z");
        Instant start = Instant.parse(args[6]);
        int steps = Integer.parseInt(args[7]);
        Duration step = Duration.parse(args[8]);
        var plan = new DatasetReplayPlan(mode, start, steps, step, hold);
        var session = DatasetReplaySession.load(Path.of(args[1]), Path.of(args[0]), args[2], args[3], plan);
        var dataset = session.dataset();
        var inventory = session.inventory();
        Set<String> selected = args.length == 10 ? new LinkedHashSet<>(List.of(args[9].split(",", -1)))
                : Set.of(inventory.servers().getFirst().location().code());
        if (selected.isEmpty() || selected.size() > 10 || selected.stream().anyMatch(code -> inventory.servers().stream().noneMatch(s -> s.location().code().equals(code))))
            throw new IllegalArgumentException("Select 1..10 installed server codes: " + selected);
        StringBuilder result = new StringBuilder();
        result.append(String.format(Locale.ROOT,
                "Dataset=%s mode=%s racks=%d servers=%d samples=%d diagnostics=0%ncoverage=[%s,%s) datasetOrigin=%s clockOrigin=%s maximumHold=%s start=%s steps=%d step=%s%n",
                dataset.metadata().get("datasetId"), mode, inventory.racks().size(), inventory.servers().size(), dataset.rows("ServerSamples").size(),
                dataset.metadata().get("coverageStartUtc"), dataset.metadata().get("coverageEndExclusiveUtc"), session.datasetOrigin(), session.clockOrigin(), hold, start, steps, step));
        result.append("mode,timeUtc,serverCode,sourceTimestampUtc,originalUtilizationFraction,originalPowerWatts,resultUtilizationFraction,resultPowerWatts\n");
        for (int i = 0; i < steps; i++) {
            var frame = session.advance().orElseThrow();
            for (var server : frame.servers()) if (selected.contains(server.location().code())) {
                var original = server.original();
                result.append(String.format(Locale.ROOT, "%s,%s,%s,%s,%s,%s,%.6f,%.3f%n", mode, frame.replayUtc(), server.location().code(),
                        original.text("timestampUtc"), original.number("utilizationFraction"), original.number("electricalPowerWatts"),
                        server.resultUtilizationFraction(), server.resultPowerWatts()));
            }
        }
        result.append("REPLAY_OK\n");
        out.print(result);
    }

}

package com.cpz.sim.datacenter.dataset;

import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.cpz.sim.datacenter.factory.*;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.input.PowerInputGranularity;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.*;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static com.cpz.sim.datacenter.dataset.DatasetReplayException.Kind.*;

/** Explicit single-threaded electrical replay. No timers, automatic steps or owned history.
 * Preparation checks the complete plan; each advance checks all installed servers again before
 * advancing the clock. Unexpected execution failures poison the mutable runtime until restart.
 */
public final class DatasetReplaySession {
    public enum State { PREPARED, ACTIVE, COMPLETED, FAILED }

    public record RackInventory(RackLocation location, String row, List<String> slots) {
        public RackInventory { slots = List.copyOf(slots); }
    }
    public record ServerInventory(ServerLocation location, ServerConfig config,
                                  HardwareStatus initialStatus, ServerRole role) {}
    public record Inventory(String roomCode, String roomName, List<RackInventory> racks,
                            List<ServerInventory> servers) {
        public Inventory { racks = List.copyOf(racks); servers = List.copyOf(servers); }
    }

    private final DatacenterDefinition definition;
    private final CustomerDataset dataset;
    private final String configurationId;
    private final String configurationVersion;
    private final DatasetReplayPlan plan;
    private final Inventory inventory;
    private Datacenter datacenter;
    private List<Server> servers;
    private ServerDatasetReplay replay;
    private SimulationEngine engine;
    private int completedPoints;
    private DatasetReplayFrame lastFrame;
    private State state;
    private DatasetReplayException failure;

    /** Loads each input once. Read errors are distinct from configuration and dataset diagnostics. */
    public static DatasetReplaySession load(Path configurationPath, Path workbookPath,
                                             String configurationId, String configurationVersion,
                                             DatasetReplayPlan plan) {
        if (configurationPath == null || workbookPath == null || plan == null || configurationId == null
                || configurationId.isBlank() || configurationVersion == null || configurationVersion.isBlank())
            throw new DatasetReplayException(INVALID_ARGUMENT, "Paths, plan and non-blank binding identifiers are required", null);
        DatacenterDefinition definition;
        CustomerDataset dataset;
        try { definition = new JsonDatacenterConfigLoader().load(configurationPath); }
        catch (RuntimeException exception) {
            throw new DatasetReplayException(CONFIGURATION_READ, exception.getMessage(), exception);
        }
        try { dataset = new XlsxCustomerDatasetLoader().load(workbookPath); }
        catch (RuntimeException exception) {
            throw new DatasetReplayException(DATASET_READ, exception.getMessage(), exception);
        }
        return prepare(definition, dataset, configurationId, configurationVersion, plan);
    }

    /** Reuses immutable observations; snapshots the configuration collections for restart. */
    public static DatasetReplaySession prepare(DatacenterDefinition definition, CustomerDataset dataset,
                                                String configurationId, String configurationVersion,
                                                DatasetReplayPlan plan) {
        if (definition == null || dataset == null || plan == null || configurationId == null
                || configurationId.isBlank() || configurationVersion == null || configurationVersion.isBlank())
            throw new DatasetReplayException(INVALID_ARGUMENT, "Configuration, dataset, plan and non-blank binding identifiers are required", null);
        return new DatasetReplaySession(definition, dataset, configurationId, configurationVersion, plan);
    }

    private DatasetReplaySession(DatacenterDefinition supplied, CustomerDataset dataset,
                                 String configurationId, String configurationVersion, DatasetReplayPlan plan) {
        this.plan = plan;
        this.dataset = dataset;
        this.configurationId = configurationId;
        this.configurationVersion = configurationVersion;
        var selected = new DatacenterDefinition(supplied.name(), supplied.layout(), supplied.serverModels(),
                supplied.servers(), supplied.temperature(), supplied.health(), supplied.cooling(), plan.mode(),
                supplied.powerInputGranularity(), supplied.temperatureInputGranularity(), supplied.roomAirTemperature(), supplied.rackCooling());
        try { new DatacenterConfigValidator().validate(selected); }
        catch (RuntimeException exception) { throw new DatasetReplayException(CONFIGURATION, exception.getMessage(), exception); }
        if (selected.layout().room() == null)
            throw new DatasetReplayException(CONFIGURATION, "Configuration needs room metadata", null);
        if (selected.cooling() != null || selected.rackCooling() != null || selected.roomAirTemperature() != null
                || selected.health() != null || (plan.mode() == DatacenterDataInputMode.POWER_DRIVEN
                && selected.powerInputGranularity() != PowerInputGranularity.SERVER))
            throw new DatasetReplayException(UNSUPPORTED_CONFIGURATION,
                    "Electrical replay does not install cooling, rack cooling, coupled room temperature or health; POWER_DRIVEN requires SERVER granularity", null);
        var layout = selected.layout();
        var aisles = layout.hotAisles() == null ? null : layout.hotAisles().stream()
                .map(a -> new HotAisleDefinition(a.code(), List.copyOf(a.columns()))).toList();
        this.definition = new DatacenterDefinition(selected.name(),
                new DatacenterLayoutDefinition(layout.room(), aisles, List.copyOf(layout.racks())),
                List.copyOf(selected.serverModels()), List.copyOf(selected.servers()), selected.temperature(),
                null, null, selected.dataInputMode(), selected.powerInputGranularity(), selected.temperatureInputGranularity(), null, null);
        initializeRuntime();
        inventory = new Inventory(definition.layout().room().code(), definition.layout().room().name(),
                datacenter.getRacks().stream().sorted(Comparator.comparing(r -> r.getLocation().code()))
                        .map(r -> new RackInventory(r.getLocation(), r.getRow(), r.getSlotCodes())).toList(),
                servers.stream().map(s -> new ServerInventory(s.getLocation(), s.getConfig(), s.getStatus(), s.getRole())).toList());
    }

    private void initializeRuntime() {
        try { datacenter = new DatacenterFactory().create(definition); }
        catch (RuntimeException exception) { throw new DatasetReplayException(CONFIGURATION, exception.getMessage(), exception); }
        servers = datacenter.getServers().stream().sorted(Comparator.comparing(Server::getCode)).toList();
        var binding = new DatasetBinding(configurationId, configurationVersion, definition.layout().room().code(), datacenter);
        var diagnostics = new CustomerDatasetValidator().validate(dataset, binding);
        if (!diagnostics.isEmpty()) throw diagnostics(diagnostics);
        String metric = plan.mode() == DatacenterDataInputMode.UTILIZATION_DRIVEN ? "utilizationFraction" : "electricalPowerWatts";
        var missing = dataset.rows("ServerSamples").stream().filter(row -> row.number(metric) == null)
                .map(row -> new DatasetIssue(DatasetIssue.Severity.ERROR, row.sheet(), row.excelRow(), metric,
                        "Every server sample must supply " + metric + " for this replay source")).toList();
        if (!missing.isEmpty()) throw diagnostics(missing);
        try { replay = new ServerDatasetReplay(dataset, binding, plan.maximumHold()); }
        catch (RuntimeException exception) { throw new DatasetReplayException(DATASET_CONTENT, exception.getMessage(), exception); }
        for (int i = 0; i < plan.points(); i++) observationsAt(plan.startUtc().plus(plan.step().multipliedBy(i)));
        var utilization = plan.mode() == DatacenterDataInputMode.UTILIZATION_DRIVEN ? replay.workloadSource() : null;
        var power = plan.mode() == DatacenterDataInputMode.POWER_DRIVEN ? replay.powerSource() : null;
        var options = new TemperatureSystemOptionsFactory().create(definition);
        var thermal = new TemperatureSystem(datacenter, options, new SimpleServerTemperatureModel());
        engine = new SimulationEngine(new SimulationClock(plan.step()));
        Duration offset = Duration.between(replay.origin(), clockOrigin());
        // Adapt source query time only. The production engine's elapsed time is unmodified.
        new DatacenterInputPipelineFactory().registerInputSystems(engine, definition, datacenter, thermal, options,
                utilization == null ? null : (server, tick) -> utilization.getUtilization(server, datasetTick(tick, offset)),
                power == null ? null : (server, tick) -> power.currentPowerWatts(server, datasetTick(tick, offset)));
        completedPoints = 0;
        lastFrame = null;
        failure = null;
        state = State.PREPARED;
    }

    private static DatasetReplayException diagnostics(List<DatasetIssue> issues) {
        return new DatasetReplayException(DATASET_DIAGNOSTICS,
                "Resolve " + issues.size() + " dataset diagnostics before replay (first 20): " + issues.stream().limit(20).toList(),
                null, issues, null, null);
    }

    private List<DatasetRow> observationsAt(Instant time) {
        List<DatasetRow> rows = new ArrayList<>(servers.size());
        for (Server server : servers) {
            try { rows.add(replay.observationAt(server.getLocation(), time)); }
            catch (RuntimeException exception) {
                throw new DatasetReplayException(AVAILABILITY, exception.getMessage(), exception,
                        List.of(), server.getLocation(), time);
            }
        }
        return List.copyOf(rows);
    }

    /** Returns empty after normal completion, without executing systems. Precheck failures mutate
     * nothing. An unexpected execution failure blocks further advances until restart().
     */
    public Optional<DatasetReplayFrame> advance() {
        if (state == State.FAILED)
            throw new DatasetReplayException(SESSION_STATE, "Replay execution failed; restart is required", failure);
        if (state == State.COMPLETED) return Optional.empty();
        if (state != State.PREPARED && state != State.ACTIVE)
            throw new DatasetReplayException(SESSION_STATE, "Replay session is not prepared", null);
        Duration replayOffset = plan.step().multipliedBy(completedPoints);
        Instant time = plan.startUtc().plus(replayOffset);
        var observations = observationsAt(time);
        try {
            var tick = engine.step();
            List<DatasetReplayFrame.ServerResult> results = new ArrayList<>(servers.size());
            for (int i = 0; i < servers.size(); i++) {
                Server server = servers.get(i);
                results.add(new DatasetReplayFrame.ServerResult(inventory.roomCode(), server.getLocation(), observations.get(i),
                        server.getUtilization(), server.getCurrentPowerWatts()));
            }
            var frame = new DatasetReplayFrame(completedPoints + 1, time, tick.elapsedTime(), replayOffset, results);
            lastFrame = frame;
            completedPoints++;
            state = completedPoints == plan.points() ? State.COMPLETED : State.ACTIVE;
            return Optional.of(frame);
        } catch (RuntimeException exception) {
            failure = new DatasetReplayException(EXECUTION, "Replay execution failed; mutable systems may have advanced; restart is required", exception);
            state = State.FAILED;
            throw failure;
        }
    }

    /** Reconstructs runtime state from captured inputs, without reading files or running a tick. */
    public void restart() {
        try { initializeRuntime(); }
        catch (RuntimeException exception) {
            state = State.FAILED;
            failure = exception instanceof DatasetReplayException replayFailure ? replayFailure
                    : new DatasetReplayException(EXECUTION, "Could not restart replay", exception);
            throw failure;
        }
    }

    private static SimulationTick datasetTick(SimulationTick tick, Duration offset) {
        return new SimulationTick(tick.index(), offset.plus(tick.elapsedTime()), tick.deltaTime());
    }

    public DatasetReplayPlan plan() { return plan; }
    public CustomerDataset dataset() { return dataset; }
    public Inventory inventory() { return inventory; }
    public Instant datasetOrigin() { return replay.origin(); }
    public Instant clockOrigin() { return plan.startUtc().minus(plan.step()); }
    public State state() { return state; }
    public int completedPoints() { return completedPoints; }
    public Duration engineElapsed() { return engine.currentTick().elapsedTime(); }
    public Optional<DatasetReplayFrame> lastFrame() { return Optional.ofNullable(lastFrame); }
    public Optional<DatasetReplayException> failure() { return Optional.ofNullable(failure); }
}

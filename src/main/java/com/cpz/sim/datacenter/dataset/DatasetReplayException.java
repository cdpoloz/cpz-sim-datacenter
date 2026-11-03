package com.cpz.sim.datacenter.dataset;

import com.cpz.sim.datacenter.model.ServerLocation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Structured replay failure. Dataset diagnostics retain their original source coordinates. */
public final class DatasetReplayException extends IllegalArgumentException {
    public enum Kind {
        INVALID_ARGUMENT, CONFIGURATION_READ, DATASET_READ, CONFIGURATION,
        UNSUPPORTED_CONFIGURATION, DATASET_DIAGNOSTICS, DATASET_CONTENT,
        AVAILABILITY, SESSION_STATE, EXECUTION
    }

    private final Kind kind;
    private final List<DatasetIssue> diagnostics;
    private final ServerLocation serverLocation;
    private final Instant replayUtc;

    DatasetReplayException(Kind kind, String message, Throwable cause) {
        this(kind, message, cause, List.of(), null, null);
    }

    DatasetReplayException(Kind kind, String message, Throwable cause, List<DatasetIssue> diagnostics,
                           ServerLocation serverLocation, Instant replayUtc) {
        super(message, cause);
        this.kind = kind;
        this.diagnostics = List.copyOf(diagnostics);
        this.serverLocation = serverLocation;
        this.replayUtc = replayUtc;
    }

    public Kind kind() { return kind; }
    public List<DatasetIssue> diagnostics() { return diagnostics; }
    public Optional<ServerLocation> serverLocation() { return Optional.ofNullable(serverLocation); }
    public Optional<Instant> replayUtc() { return Optional.ofNullable(replayUtc); }
}

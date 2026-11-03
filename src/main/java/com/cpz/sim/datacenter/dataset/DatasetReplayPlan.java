package com.cpz.sim.datacenter.dataset;

import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import java.time.*;
import static com.cpz.sim.datacenter.dataset.DatasetReplayException.Kind.INVALID_ARGUMENT;

/** Finite query grid; the input mode overrides the configuration's default mode in memory. */
public record DatasetReplayPlan(DatacenterDataInputMode mode, Instant startUtc, int points,
                                Duration step, Duration maximumHold) {
    public static final int MAX_POINTS = 1000;

    public DatasetReplayPlan {
        if (mode == null || mode == DatacenterDataInputMode.TEMPERATURE_DRIVEN)
            throw new DatasetReplayException(INVALID_ARGUMENT,
                    "Server replay supports only UTILIZATION_DRIVEN or POWER_DRIVEN", null);
        if (startUtc == null || points < 1 || points > MAX_POINTS || step == null || step.isZero()
                || step.isNegative() || maximumHold == null || maximumHold.isZero() || maximumHold.isNegative())
            throw new DatasetReplayException(INVALID_ARGUMENT,
                    "startUtc is required; points must be 1..1000; step and maximumHold must be positive", null);
        try {
            startUtc.minus(step);
            startUtc.plus(step.multipliedBy(points - 1L));
            step.multipliedBy(points);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new DatasetReplayException(INVALID_ARGUMENT, "Replay time range cannot be represented", exception);
        }
    }
}

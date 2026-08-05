package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.function.Supplier;

public final class GmContextCompactionScheduler {
    private final CompactionPolicy policy;
    private boolean scheduled;

    public GmContextCompactionScheduler(CompactionPolicy policy) { this.policy = Objects.requireNonNull(policy); }

    /** Call only after the GM turn transaction commits. */
    public synchronized boolean scheduleAfterCommit(ContextUsage usage, CompactionBarrier barrier, Supplier<Boolean> compactor) {
        Objects.requireNonNull(compactor);
        if (!policy.shouldSchedule(usage, scheduled) || !policy.canCompact(barrier)) return false;
        scheduled = true;
        return Boolean.TRUE.equals(compactor.get());
    }
    public synchronized boolean scheduled() { return scheduled; }
}

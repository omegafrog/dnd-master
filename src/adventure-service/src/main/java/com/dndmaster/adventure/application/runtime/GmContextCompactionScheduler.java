package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class GmContextCompactionScheduler {
    private final CompactionPolicy policy;
    private final Set<UUID> scheduled = ConcurrentHashMap.newKeySet();

    public GmContextCompactionScheduler(CompactionPolicy policy) { this.policy = Objects.requireNonNull(policy); }

    /** Call only after the GM turn transaction commits. */
    public boolean scheduleAfterCommit(UUID sessionId, ContextUsage usage, CompactionBarrier barrier, Supplier<Boolean> compactor) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(compactor);
        if (!policy.shouldSchedule(usage, scheduled.contains(sessionId)) || !policy.canCompact(barrier)) return false;
        if (!scheduled.add(sessionId)) return false;
        try {
            boolean completed = Boolean.TRUE.equals(compactor.get());
            scheduled.remove(sessionId);
            return completed;
        } catch (RuntimeException failure) {
            scheduled.remove(sessionId);
            throw failure;
        }
    }
    public boolean scheduleAfterCommit(ContextUsage usage, CompactionBarrier barrier, Supplier<Boolean> compactor) {
        return scheduleAfterCommit(new UUID(0, 0), usage, barrier, compactor);
    }
    public boolean scheduled(UUID sessionId) { return scheduled.contains(sessionId); }
}

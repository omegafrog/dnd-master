package com.dndmaster.adventure.application.runtime;

public final class CompactionPolicy {
    private final double threshold;
    public CompactionPolicy(double threshold) {
        if (threshold <= 0 || threshold > 1) throw new IllegalArgumentException("threshold must be in (0, 1]");
        this.threshold = threshold;
    }
    public boolean shouldSchedule(ContextUsage usage, boolean alreadyScheduled) {
        return !alreadyScheduled && usage.estimatedTokens() >= Math.ceil(usage.contextLimit() * threshold);
    }
    public boolean canCompact(CompactionBarrier barrier) { return barrier != null && barrier.clearForCompaction(); }
}

package com.dndmaster.adventure.application.runtime;

public record CompactionBarrier(boolean activeTurn, boolean pendingTool, boolean pendingMapCandidate,
                                boolean staleSnapshot, boolean saveFailure) {
    public static CompactionBarrier clear() { return new CompactionBarrier(false, false, false, false, false); }
    public boolean clearForCompaction() {
        return !activeTurn && !pendingTool && !pendingMapCandidate && !staleSnapshot && !saveFailure;
    }
}

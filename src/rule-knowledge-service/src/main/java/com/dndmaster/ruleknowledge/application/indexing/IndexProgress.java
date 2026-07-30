package com.dndmaster.ruleknowledge.application.indexing;

import java.time.Instant;

public record IndexProgress(
        String status,
        int totalChunks,
        int completedChunks,
        String lastError,
        String leaseOwner,
        Instant leaseUntil) {
    public int remainingChunks() { return Math.max(0, totalChunks - completedChunks); }
}

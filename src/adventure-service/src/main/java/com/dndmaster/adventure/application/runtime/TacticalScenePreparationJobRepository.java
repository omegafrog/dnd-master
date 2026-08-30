package com.dndmaster.adventure.application.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TacticalScenePreparationJobRepository {
    Job createOrGet(UUID sessionId, UUID ownerId, int stagePosition, String stageName, boolean mapRequired);
    Optional<Job> find(UUID sessionId, int stagePosition);
    List<Job> findUnfinished();
    boolean claim(UUID jobId);
    default boolean claim(UUID jobId, UUID leaseToken, java.time.Duration lease) { return claim(jobId); }
    default void recoverExpiredLeases(java.time.Instant now) {}
    void update(UUID jobId, Status status, int progress, int attempts, String message, String failureReason);
    void resetForRetry(UUID jobId);

    enum Status { QUEUED, RUNNING, COMPLETE, FAILED_RETRYABLE }

    record Job(UUID jobId, UUID sessionId, UUID ownerId, int stagePosition, String stageName,
            Status status, int progress, int attempts, boolean mapRequired, String message,
            String failureReason, Instant updatedAt) {}
}

package com.dndmaster.adventure.application.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

public interface TacticalScenePreparationJobRepository {
    Job createOrGet(UUID sessionId, UUID ownerId, int stagePosition, String stageName, boolean mapRequired);
    Optional<Job> find(UUID sessionId, int stagePosition);
    List<Job> findUnfinished();
    boolean claim(UUID jobId);
    default boolean claim(UUID jobId, UUID leaseToken, java.time.Duration lease) { return claim(jobId); }
    default void recoverExpiredLeases(java.time.Instant now) {}
    void update(UUID jobId, Status status, int progress, int attempts, String message, String failureReason);
    default void updateProgress(UUID jobId, Status status, PreparationProgress progress, int attempts,
                                String message, String failureReason) {
        update(jobId, status, progress.percentage() == null ? 0 : progress.percentage(), attempts, message, failureReason);
    }
    void resetForRetry(UUID jobId);

    enum Status { QUEUED, RUNNING, COMPLETE, FAILED_RETRYABLE }

    final class Job {
        private final UUID jobId;
        private final UUID sessionId;
        private final UUID ownerId;
        private final int stagePosition;
        private final String stageName;
        private final Status status;
        private final PreparationProgress preparationProgress;
        private final int attempts;
        private final boolean mapRequired;
        private final String message;
        private final String failureReason;
        private final Instant updatedAt;

        public Job(UUID jobId, UUID sessionId, UUID ownerId, int stagePosition, String stageName,
                   Status status, int progress, int attempts, boolean mapRequired, String message,
                   String failureReason, Instant updatedAt) {
            this(jobId, sessionId, ownerId, stagePosition, stageName, status,
                    PreparationProgress.legacy(progress), attempts, mapRequired, message, failureReason, updatedAt);
        }

        public Job(UUID jobId, UUID sessionId, UUID ownerId, int stagePosition, String stageName,
                   Status status, PreparationProgress preparationProgress, int attempts, boolean mapRequired,
                   String message, String failureReason, Instant updatedAt) {
            this.jobId = Objects.requireNonNull(jobId);
            this.sessionId = Objects.requireNonNull(sessionId);
            this.ownerId = Objects.requireNonNull(ownerId);
            if (stagePosition < 1) throw new IllegalArgumentException("stage position must be positive");
            this.stagePosition = stagePosition;
            this.stageName = Objects.requireNonNull(stageName);
            this.status = Objects.requireNonNull(status);
            this.preparationProgress = Objects.requireNonNull(preparationProgress);
            this.attempts = attempts;
            this.mapRequired = mapRequired;
            this.message = Objects.requireNonNull(message);
            this.failureReason = failureReason;
            this.updatedAt = Objects.requireNonNull(updatedAt);
        }

        public UUID jobId() { return jobId; }
        public UUID sessionId() { return sessionId; }
        public UUID ownerId() { return ownerId; }
        public int stagePosition() { return stagePosition; }
        public String stageName() { return stageName; }
        public Status status() { return status; }
        /** Legacy percentage projection retained for old callers and JSON rows. */
        public int progress() { return preparationProgress.percentage() == null ? 0 : preparationProgress.percentage(); }
        public PreparationProgress preparationProgress() { return preparationProgress; }
        public int attempts() { return attempts; }
        public boolean mapRequired() { return mapRequired; }
        public String message() { return message; }
        public String failureReason() { return failureReason; }
        public Instant updatedAt() { return updatedAt; }
    }
}

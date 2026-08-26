package com.dndmaster.adventure.domain.adventure;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AdventureStoryPlan {
    private final UUID planId;
    private final SessionId sessionId;
    private final long packageRevision;
    private final long partyRevision;
    private final long version;
    private final AdventurePlanConfiguration configuration;
    private final AdventureStoryPlanStatus status;
    private final List<AdventureStoryPlanStage> stages;
    private final int currentStage;
    private final String failureReason;
    private final Instant updatedAt;

    private AdventureStoryPlan(UUID planId, SessionId sessionId, long packageRevision, long partyRevision, long version,
            AdventureStoryPlanStatus status, AdventurePlanConfiguration configuration, List<AdventureStoryPlanStage> stages, int currentStage,
            String failureReason, Instant updatedAt) {
        this.planId = Objects.requireNonNull(planId);
        this.sessionId = Objects.requireNonNull(sessionId);
        if (packageRevision < 1 || partyRevision < 0 || version < 1) throw new IllegalArgumentException("invalid story plan revision");
        this.packageRevision = packageRevision;
        this.partyRevision = partyRevision;
        this.version = version;
        this.status = Objects.requireNonNull(status);
        this.configuration = Objects.requireNonNull(configuration);
        this.stages = List.copyOf(Objects.requireNonNull(stages));
        if (status == AdventureStoryPlanStatus.READY && this.stages.isEmpty()) throw new IllegalArgumentException("ready plan requires stages");
        if (currentStage < 0 || (!this.stages.isEmpty() && currentStage >= this.stages.size())) throw new IllegalArgumentException("invalid current stage");
        this.currentStage = currentStage;
        this.failureReason = failureReason;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static AdventureStoryPlan ready(SessionId sessionId, long partyRevision, long version, List<AdventureStoryPlanStage> stages) {
        return ready(UUID.randomUUID(), sessionId, 1, partyRevision, version, AdventurePlanConfiguration.defaults(), stages);
    }

    public static AdventureStoryPlan ready(UUID planId, SessionId sessionId, long packageRevision, long partyRevision, long version, List<AdventureStoryPlanStage> stages) {
        return ready(planId, sessionId, packageRevision, partyRevision, version, AdventurePlanConfiguration.defaults(), stages);
    }

    public static AdventureStoryPlan ready(UUID planId, SessionId sessionId, long packageRevision, long partyRevision, long version,
            AdventurePlanConfiguration configuration, List<AdventureStoryPlanStage> stages) {
        return new AdventureStoryPlan(planId, sessionId, packageRevision, partyRevision, version, AdventureStoryPlanStatus.READY,
                configuration, stages, 0, null, Instant.now());
    }

    public static AdventureStoryPlan failed(UUID planId, SessionId sessionId, long packageRevision, long partyRevision, long version, String reason) {
        return new AdventureStoryPlan(planId, sessionId, packageRevision, partyRevision, version, AdventureStoryPlanStatus.FAILED,
                AdventurePlanConfiguration.defaults(), List.of(), 0, reason, Instant.now());
    }

    public static AdventureStoryPlan blocked(UUID planId, SessionId sessionId, long packageRevision, long partyRevision, long version,
            AdventurePlanConfiguration configuration, List<AdventureStoryPlanStage> stages, String reason) {
        return new AdventureStoryPlan(planId, sessionId, packageRevision, partyRevision, version, AdventureStoryPlanStatus.BLOCKED,
                configuration, stages, 0, reason, Instant.now());
    }

    public static AdventureStoryPlan rehydrate(UUID planId, SessionId sessionId, long packageRevision, long partyRevision,
            long version, AdventureStoryPlanStatus status, List<AdventureStoryPlanStage> stages, int currentStage,
            String failureReason, Instant updatedAt) {
        return rehydrate(planId, sessionId, packageRevision, partyRevision, version, status,
                AdventurePlanConfiguration.defaults(), stages, currentStage, failureReason, updatedAt);
    }

    public static AdventureStoryPlan rehydrate(UUID planId, SessionId sessionId, long packageRevision, long partyRevision,
            long version, AdventureStoryPlanStatus status, AdventurePlanConfiguration configuration,
            List<AdventureStoryPlanStage> stages, int currentStage, String failureReason, Instant updatedAt) {
        return new AdventureStoryPlan(planId, sessionId, packageRevision, partyRevision, version, status,
                configuration, stages, currentStage, failureReason, updatedAt);
    }

    public UUID planId() { return planId; }
    public SessionId sessionId() { return sessionId; }
    public long packageRevision() { return packageRevision; }
    public long partyRevision() { return partyRevision; }
    public long version() { return version; }
    public AdventurePlanConfiguration configuration() { return configuration; }
    public AdventureStoryPlanStatus status() { return status; }
    public List<AdventureStoryPlanStage> stages() { return stages; }
    public int stageCount() { return stages.size(); }
    public int currentStage() { return currentStage; }
    public String failureReason() { return failureReason; }
    public Instant updatedAt() { return updatedAt; }

    public AdventureStoryPlan advanceTo(int nextStage) {
        if (nextStage < currentStage || nextStage >= stages.size()) throw new IllegalArgumentException("invalid story plan transition");
        return new AdventureStoryPlan(planId, sessionId, packageRevision, partyRevision, version + 1, status,
                configuration, stages, nextStage, failureReason, Instant.now());
    }

    /** Revisions may replace only stages that have not yet been published to play. */
    public AdventureStoryPlan reviseFutureStages(List<AdventureStoryPlanStage> candidate) {
        Objects.requireNonNull(candidate, "candidate stages must not be null");
        if (candidate.size() != stages.size()) throw new IllegalArgumentException("future revision must retain the stage graph");
        for (int index = 0; index <= currentStage; index++) {
            if (!stages.get(index).equals(candidate.get(index))) throw new IllegalStateException("published story stages are immutable");
        }
        return new AdventureStoryPlan(planId, sessionId, packageRevision, partyRevision, version + 1, status,
                configuration, candidate, currentStage, failureReason, Instant.now());
    }

    public AdventureStoryPlan reviseFutureStage(int position, AdventureStoryPlanStage replacement) {
        Objects.requireNonNull(replacement, "replacement stage must not be null");
        if (position < 1 || position > stages.size()) throw new IllegalArgumentException("story plan stage not found");
        if (position != replacement.position()) throw new IllegalArgumentException("replacement stage position mismatch");
        if (position <= currentStage + 1) throw new IllegalStateException("current and revealed story stages are immutable");
        List<AdventureStoryPlanStage> candidate = new java.util.ArrayList<>(stages);
        candidate.set(position - 1, replacement);
        return reviseFutureStages(candidate);
    }

    /** Publishes the scene for the stage being entered without changing the future stage graph. */
    public AdventureStoryPlan prepareCurrentStage(AdventureStoryPlanStage replacement) {
        Objects.requireNonNull(replacement, "replacement stage must not be null");
        if (replacement.position() != currentStage + 1) {
            throw new IllegalStateException("only the current stage may be prepared");
        }
        List<AdventureStoryPlanStage> candidate = new java.util.ArrayList<>(stages);
        candidate.set(currentStage, replacement);
        return new AdventureStoryPlan(planId, sessionId, packageRevision, partyRevision, version + 1, status,
                configuration, candidate, currentStage, failureReason, Instant.now());
    }
}

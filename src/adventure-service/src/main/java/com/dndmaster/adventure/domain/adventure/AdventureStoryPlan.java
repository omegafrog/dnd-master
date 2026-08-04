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
    private final AdventureStoryPlanStatus status;
    private final List<AdventureStoryPlanStage> stages;
    private final int currentStage;
    private final String failureReason;
    private final Instant updatedAt;

    private AdventureStoryPlan(UUID planId, SessionId sessionId, long packageRevision, long partyRevision, long version,
            AdventureStoryPlanStatus status, List<AdventureStoryPlanStage> stages, int currentStage,
            String failureReason, Instant updatedAt) {
        this.planId = Objects.requireNonNull(planId);
        this.sessionId = Objects.requireNonNull(sessionId);
        if (packageRevision < 1 || partyRevision < 0 || version < 1) throw new IllegalArgumentException("invalid story plan revision");
        this.packageRevision = packageRevision;
        this.partyRevision = partyRevision;
        this.version = version;
        this.status = Objects.requireNonNull(status);
        this.stages = List.copyOf(Objects.requireNonNull(stages));
        if (status == AdventureStoryPlanStatus.READY && this.stages.isEmpty()) throw new IllegalArgumentException("ready plan requires stages");
        if (currentStage < 0 || (!this.stages.isEmpty() && currentStage >= this.stages.size())) throw new IllegalArgumentException("invalid current stage");
        this.currentStage = currentStage;
        this.failureReason = failureReason;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static AdventureStoryPlan ready(SessionId sessionId, long partyRevision, long version, List<AdventureStoryPlanStage> stages) {
        return ready(UUID.randomUUID(), sessionId, 1, partyRevision, version, stages);
    }

    public static AdventureStoryPlan ready(UUID planId, SessionId sessionId, long packageRevision, long partyRevision, long version, List<AdventureStoryPlanStage> stages) {
        return new AdventureStoryPlan(planId, sessionId, packageRevision, partyRevision, version, AdventureStoryPlanStatus.READY, stages, 0, null, Instant.now());
    }

    public static AdventureStoryPlan failed(UUID planId, SessionId sessionId, long packageRevision, long partyRevision, long version, String reason) {
        return new AdventureStoryPlan(planId, sessionId, packageRevision, partyRevision, version, AdventureStoryPlanStatus.FAILED, List.of(), 0, reason, Instant.now());
    }

    public static AdventureStoryPlan rehydrate(UUID planId, SessionId sessionId, long packageRevision, long partyRevision,
            long version, AdventureStoryPlanStatus status, List<AdventureStoryPlanStage> stages, int currentStage,
            String failureReason, Instant updatedAt) {
        return new AdventureStoryPlan(planId, sessionId, packageRevision, partyRevision, version, status, stages,
                currentStage, failureReason, updatedAt);
    }

    public UUID planId() { return planId; }
    public SessionId sessionId() { return sessionId; }
    public long packageRevision() { return packageRevision; }
    public long partyRevision() { return partyRevision; }
    public long version() { return version; }
    public AdventureStoryPlanStatus status() { return status; }
    public List<AdventureStoryPlanStage> stages() { return stages; }
    public int stageCount() { return stages.size(); }
    public int currentStage() { return currentStage; }
    public String failureReason() { return failureReason; }
    public Instant updatedAt() { return updatedAt; }
}

package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.ExactTail;
import com.dndmaster.adventure.domain.runtime.checkpoint.ContextSummaryCandidate;
import com.dndmaster.adventure.domain.runtime.checkpoint.GmContextCheckpoint;
import com.dndmaster.adventure.domain.runtime.checkpoint.SnapshotReferences;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.plan.AdventureStoryPlanRevision;

public final class GmContextCheckpointApplicationService {
    private final CompactionPolicy policy;
    private final ContextCompactionPort compactionPort;
    private final GmContextCheckpointRepository repository;
    private final StoryPlanRevisionRepository plans;

    public GmContextCheckpointApplicationService(CompactionPolicy policy, ContextCompactionPort compactionPort,
                                                  GmContextCheckpointRepository repository, StoryPlanRevisionRepository plans) {
        this.policy = Objects.requireNonNull(policy); this.compactionPort = Objects.requireNonNull(compactionPort);
        this.repository = Objects.requireNonNull(repository);
        this.plans = Objects.requireNonNull(plans);
    }

    public Optional<GmContextCheckpoint> compact(UUID sessionId, UUID sourceTurnId, long version,
                                                  ContextUsage usage, CompactionBarrier barrier, String context,
                                                  ExactTail exactTail, SnapshotReferences references) {
        return compact(sessionId, sourceTurnId, version, usage, barrier, context, exactTail, references,
                "unknown", "unknown", "unknown");
    }

    public Optional<GmContextCheckpoint> compact(UUID sessionId, UUID sourceTurnId, long version,
                                                  ContextUsage usage, CompactionBarrier barrier, String context,
                                                  ExactTail exactTail, SnapshotReferences references,
                                                  String provider, String model, String reasoning) {
        if (!policy.shouldSchedule(usage, false) || !policy.canCompact(barrier)) return Optional.empty();
        try {
            ContextSummaryCandidate candidate = Objects.requireNonNull(compactionPort.summarize(
                    new ContextCompactionRequest(sessionId, sourceTurnId, context, exactTail, references)));
            AdventureStoryPlanRevision currentPlan = plans.current(sessionId).orElseThrow(() -> new IllegalStateException("story plan unavailable"));
            if (!currentPlan.revisionId().equals(candidate.planRevisionId()) || currentPlan.version() != candidate.planVersion()) {
                throw new IllegalStateException("compaction plan is stale");
            }
            long checkpointVersion = repository.current(sessionId).map(current -> current.version() + 1).orElse(1L);
            GmContextCheckpoint checkpoint = GmContextCheckpoint.create(sessionId, sourceTurnId, checkpointVersion,
                    candidate, exactTail, references, provider, model, reasoning);
            repository.append(checkpoint);
            return Optional.of(checkpoint);
        } catch (RuntimeException failure) {
            // Previous current checkpoint remains authoritative. Compaction is internal and retryable.
            return Optional.empty();
        }
    }
}

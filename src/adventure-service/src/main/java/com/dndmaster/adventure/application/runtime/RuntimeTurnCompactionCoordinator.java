package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.ExactTail;
import com.dndmaster.adventure.domain.runtime.checkpoint.SnapshotReferences;
import java.util.Objects;

public final class RuntimeTurnCompactionCoordinator {
    private final ProviderTokenEstimator estimator;
    private final GmContextCompactionScheduler scheduler;
    private final GmContextCheckpointApplicationService checkpoints;
    private final AuthoritativeSnapshotResolver snapshots;
    private final StoryPlanRevisionRepository plans;
    public RuntimeTurnCompactionCoordinator(ProviderTokenEstimator estimator, GmContextCompactionScheduler scheduler,
            GmContextCheckpointApplicationService checkpoints, AuthoritativeSnapshotResolver snapshots,
            StoryPlanRevisionRepository plans) {
        this.estimator = Objects.requireNonNull(estimator); this.scheduler = Objects.requireNonNull(scheduler);
        this.checkpoints = Objects.requireNonNull(checkpoints); this.snapshots = Objects.requireNonNull(snapshots);
        this.plans = Objects.requireNonNull(plans);
    }
    public void afterCommit(RuntimeTurn turn) {
        var current = snapshots.resolve(turn.sessionId());
        var plan = plans.current(turn.sessionId()).orElse(null);
        if (plan == null) return;
        var refs = new SnapshotReferences(plan.revisionId(), current.factVersion(), current.clockVersion(), current.characterVersion(), current.mapVersion(), current.mapVersion());
        var precedingScene = turn.conversation().stream()
                .filter(entry -> "AI_GAME_MASTER".equals(entry.speaker()))
                .reduce((first, second) -> second)
                .map(entry -> entry.content())
                .orElse(turn.context().currentScene());
        var tail = new ExactTail(turn.action(), precedingScene, turn.plan().narration(), turn.turnId().toString(),
                current.clockSnapshot(), turn.context().currentScene(), current.mapSnapshot(),
                current.mapSnapshot(), turn.context().pendingActionValue().orElse("choice:none"));
        var prompt = String.join("\n", turn.conversation().stream().map(Object::toString).toList())
                + "\n" + turn.context() + "\n" + turn.evidencePack() + "\n" + turn.plan();
        var usage = estimator.usage(turn.plan().provider(), prompt);
        // Resolver reads post-commit authoritative snapshots. Runtime-turn version is a different counter;
        // comparing them would create false stale barriers. Pending choice blocks map-candidate compaction.
        var barrier = new CompactionBarrier(!turn.committed(), false, turn.context().pendingActionValue().isPresent(),
                false, false);
        scheduler.scheduleAfterCommit(turn.sessionId(), usage, barrier,
                () -> checkpoints.compact(turn.sessionId(), turn.turnId(), turn.version(), usage, barrier, prompt, tail, refs).isPresent());
    }
}

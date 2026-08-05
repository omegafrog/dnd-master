package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.ExactTail;
import com.dndmaster.adventure.domain.runtime.checkpoint.SnapshotReferences;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class RuntimeTurnCompactionCoordinator {
    private final ProviderTokenEstimator estimator;
    private final GmContextCompactionScheduler scheduler;
    private final GmContextCheckpointApplicationService checkpoints;
    private final AuthoritativeSnapshotResolver snapshots;
    public RuntimeTurnCompactionCoordinator(ProviderTokenEstimator estimator, GmContextCompactionScheduler scheduler,
            GmContextCheckpointApplicationService checkpoints, AuthoritativeSnapshotResolver snapshots) {
        this.estimator = Objects.requireNonNull(estimator); this.scheduler = Objects.requireNonNull(scheduler);
        this.checkpoints = Objects.requireNonNull(checkpoints); this.snapshots = Objects.requireNonNull(snapshots);
    }
    public void afterCommit(RuntimeTurn turn) {
        var current = snapshots.resolve(turn.sessionId());
        var planId = java.util.UUID.nameUUIDFromBytes((turn.sessionId() + ":plan").getBytes(StandardCharsets.UTF_8));
        var refs = new SnapshotReferences(planId, current.factVersion(), current.clockVersion(), current.characterVersion(), current.mapVersion(), current.mapVersion());
        var tail = new ExactTail(turn.action(), turn.context().currentScene(), turn.plan().narration(), "turn:" + turn.turnId(), "round:unknown", "location:unknown", "map:authoritative", "fog:authoritative", "choice:none");
        var usage = estimator.usage(turn.plan().provider(), turn.action() + "\n" + turn.plan().narration());
        scheduler.scheduleAfterCommit(turn.sessionId(), usage, CompactionBarrier.clear(), () -> checkpoints.compact(turn.sessionId(), turn.turnId(), turn.version(), usage, CompactionBarrier.clear(), turn.plan().narration(), tail, refs).isPresent());
    }
}

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
        int playerIndex = -1;
        for (int i = turn.conversation().size() - 1; i >= 0; i--) {
            if ("PLAYER".equals(turn.conversation().get(i).speaker())) { playerIndex = i; break; }
        }
        String precedingScene = playerIndex > 0
                ? turn.conversation().subList(0, playerIndex).stream()
                    .filter(entry -> "AI_GAME_MASTER".equals(entry.speaker()))
                    .reduce((first, second) -> second).map(entry -> entry.content()).orElse(turn.context().currentScene())
                : turn.context().currentScene();
        var tail = new ExactTail(turn.action(), precedingScene, turn.plan().narration(), current.currentTurn(),
                current.currentRound(), current.location(), current.mapState(),
                current.fogOfWar(), turn.context().pendingActionValue().orElse("choice:none"));
        var prompt = String.join("\n", turn.conversation().stream().map(Object::toString).toList())
                + "\n" + turn.context() + "\n" + turn.evidencePack() + "\n" + turn.plan();
        var usage = estimator.usage(turn.plan().provider(), prompt);
        var barrier = new CompactionBarrier(!turn.committed(), current.pendingTool(), current.pendingMapCandidate(),
                false, current.saveFailure());
        scheduler.scheduleAfterCommit(turn.sessionId(), usage, barrier,
                () -> checkpoints.compact(turn.sessionId(), turn.turnId(), turn.version(), usage, barrier, prompt, tail, refs,
                        turn.plan().provider(), turn.plan().model(), turn.plan().reasoning()).isPresent());
    }
}

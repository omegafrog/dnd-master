package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Resumes only COMMITTING turns; normal RuntimeTurns stay synchronous. */
public final class RuntimeTurnRecoveryWorker {
    private final RuntimeTurnRepository turnRepository;
    private final RuntimeTurnResumeClient resumeClient;
    private final Function<RuntimeTurn, Runnable> localCommitFactory;

    public RuntimeTurnRecoveryWorker(RuntimeTurnRepository turnRepository, RuntimeTurnResumeClient resumeClient,
            Function<RuntimeTurn, Runnable> localCommitFactory) {
        this.turnRepository = Objects.requireNonNull(turnRepository, "turn repository must not be null");
        this.resumeClient = Objects.requireNonNull(resumeClient, "resume client must not be null");
        this.localCommitFactory = Objects.requireNonNull(localCommitFactory, "local commit factory must not be null");
    }

    public List<RuntimeTurnCommitOrchestrator.Result> recover() {
        return turnRepository.findAllByLifecycle(RuntimeTurnLifecycle.COMMITTING).stream()
                .map(turn -> resumeClient.resume(turn.turnId(), localCommitFactory.apply(turn))).toList();
    }
}

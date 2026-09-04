package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.function.Supplier;

/** Runs fixed resolution, narration, and safety without ever re-running resolution on retry. */
public final class RuntimeTurnSafetyOrchestrator {
    private final NarrationSafetyPort safetyPort;
    private final int maxNarrationAttempts;

    public RuntimeTurnSafetyOrchestrator(NarrationSafetyPort safetyPort) {
        this(safetyPort, 3);
    }

    public RuntimeTurnSafetyOrchestrator(NarrationSafetyPort safetyPort, int maxNarrationAttempts) {
        this.safetyPort = Objects.requireNonNull(safetyPort, "safety port must not be null");
        if (maxNarrationAttempts < 1) throw new IllegalArgumentException("max narration attempts must be positive");
        this.maxNarrationAttempts = maxNarrationAttempts;
    }

    public RuntimeTurn resolveAndNarrate(RuntimeTurn requested, RuntimeTurnResolution resolution,
            PendingRuntimeState pending, CompletionProposal completion, Supplier<String> narrationSupplier) {
        Objects.requireNonNull(requested, "requested turn must not be null");
        Objects.requireNonNull(narrationSupplier, "narration supplier must not be null");
        RuntimeTurn turn = requested.lifecycle() == RuntimeTurnLifecycle.REQUESTED ? requested : requested.asRequested();
        turn = turn.beginResolving().fixResolution(resolution, pending, completion).beginNarration();
        for (int attempt = 0; attempt < maxNarrationAttempts; attempt++) {
            String narration = narrationSupplier.get();
            turn = turn.beginSafetyCheck();
            NarrationSafetyAssessment safety = safetyPort.assess(new NarrationSafetyRequest(
                    narration, turn.evidencePack(), turn.context(), turn.action()));
            if (safety.approved()) return turn.readyToCommit(narration);
            if (attempt + 1 == maxNarrationAttempts) return turn.discard();
            turn = turn.retryNarration();
        }
        throw new IllegalStateException("narration attempts exhausted");
    }
}

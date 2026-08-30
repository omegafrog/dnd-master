package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only, deliberately whitelisted diagnostics projection for development support. */
public final class RuntimeTurnDiagnosticsApplicationService {
    private final RuntimeTurnRepository turns;
    private final RuntimeTurnFailureRepository failures;

    public RuntimeTurnDiagnosticsApplicationService(RuntimeTurnRepository turns) {
        this(turns, new NoopRuntimeTurnFailureRepository());
    }

    public RuntimeTurnDiagnosticsApplicationService(RuntimeTurnRepository turns, RuntimeTurnFailureRepository failures) {
        this.turns = Objects.requireNonNull(turns, "runtime turn repository must not be null");
        this.failures = Objects.requireNonNull(failures, "failure repository must not be null");
    }

    public Optional<RuntimeTurnDiagnosticsView> readByTurnId(UUID turnId) {
        return turns.findByTurnId(Objects.requireNonNull(turnId, "turn id must not be null"))
                .map(turn -> RuntimeTurnDiagnosticsView.from(turn, failures.findByTurnId(turn.turnId())));
    }

    public Optional<RuntimeTurnDiagnosticsView> readByCommandId(UUID commandId) {
        return turns.findByCommandId(Objects.requireNonNull(commandId, "command id must not be null"))
                .map(turn -> RuntimeTurnDiagnosticsView.from(turn, failures.findByTurnId(turn.turnId())));
    }

    /** Safe diagnostic view: forbidden facts, reasoning, raw evidence, and mutable context are excluded. */
    public record RuntimeTurnDiagnosticsView(
            UUID turnId, UUID commandId, AdventureId adventureId, UUID sessionId,
            RuntimeTurnLifecycle lifecycle, boolean committed, RuntimeTurnOrigin origin,
            PlannerArtifact planner, ResolvedArtifact resolved, WriterArtifact writer,
            List<RuntimeTurnFailureArtifact> failures) {
        static RuntimeTurnDiagnosticsView from(RuntimeTurn turn, List<RuntimeTurnFailureArtifact> failures) {
            Objects.requireNonNull(turn, "turn must not be null");
            TurnPlan plannerPlan = turn.resolvedArtifact() == null
                    ? TurnPlan.from(turn.plan()) : turn.resolvedArtifact().plan();
            ResolvedTurnPlan resolvedPlan = turn.resolvedArtifact();
            if (resolvedPlan == null) {
                resolvedPlan = ResolvedTurnPlan.of(plannerPlan, List.of(turn.plan().judgment()));
                if (turn.lifecycle() == RuntimeTurnLifecycle.PRESENTED) resolvedPlan = resolvedPlan.presented();
            }
            return new RuntimeTurnDiagnosticsView(turn.turnId(), turn.commandId(), turn.adventureId(), turn.sessionId(),
                    turn.lifecycle(), turn.committed(), turn.origin(), PlannerArtifact.from(plannerPlan),
                    ResolvedArtifact.from(resolvedPlan), WriterArtifact.from(turn, resolvedPlan), List.copyOf(failures));
        }
    }

    public record PlannerArtifact(String scene, String npcState, String judgment, List<String> revealableFacts) {
        static PlannerArtifact from(TurnPlan plan) {
            return new PlannerArtifact(plan.scene(), plan.npcState(), plan.judgment(), plan.revealableFacts());
        }
    }

    public record ResolvedArtifact(String scene, String npcState, String judgment,
                                   List<String> revealableFacts, List<String> outcomes,
                                   RuntimeTurnLifecycle lifecycle, EffectivePromptLineage promptLineage) {
        static ResolvedArtifact from(ResolvedTurnPlan plan) {
            return new ResolvedArtifact(plan.plan().scene(), plan.plan().npcState(), plan.plan().judgment(),
                    plan.plan().revealableFacts(), plan.outcomes(), plan.lifecycle(), plan.promptLineage());
        }
    }

    public record WriterArtifact(String visibleScene, List<String> revealableFacts,
                                 List<String> outcomes, String prose) {
        static WriterArtifact from(RuntimeTurn turn, ResolvedTurnPlan resolved) {
            return new WriterArtifact(resolved.plan().scene(), resolved.plan().revealableFacts(), resolved.outcomes(),
                    turn.lifecycle() == RuntimeTurnLifecycle.PRESENTED ? turn.plan().narration() : "");
        }
    }
}

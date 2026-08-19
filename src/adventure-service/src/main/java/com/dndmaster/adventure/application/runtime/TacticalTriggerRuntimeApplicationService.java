package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.Objects;
import java.util.UUID;

/** Evaluates authored triggers and sends only the resulting planned effect to runtime state. */
public final class TacticalTriggerRuntimeApplicationService {
    private final TacticalTriggerEvaluator evaluator;
    private final TacticalTriggerRuntimePort runtime;

    public TacticalTriggerRuntimeApplicationService(TacticalTriggerEvaluator evaluator, TacticalTriggerRuntimePort runtime) {
        this.evaluator = Objects.requireNonNull(evaluator, "trigger evaluator must not be null");
        this.runtime = Objects.requireNonNull(runtime, "tactical runtime must not be null");
    }

    public TacticalTriggerEvaluator.Evaluation apply(TacticalScenePlan scene, String triggerId,
            UUID combatMapId, UUID ownerPlayerId, long expectedVersion, UUID commandId) {
        var evaluation = evaluator.evaluate(scene, triggerId);
        runtime.apply(combatMapId, ownerPlayerId, expectedVersion, commandId, evaluation);
        return evaluation;
    }
}

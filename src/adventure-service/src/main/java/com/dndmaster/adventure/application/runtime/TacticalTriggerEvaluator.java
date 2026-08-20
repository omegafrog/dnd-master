package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import com.dndmaster.adventure.domain.adventure.TacticalTrigger;
import java.util.Objects;

/** Allows runtime effects only for trigger IDs authored in the ready tactical scene. */
public final class TacticalTriggerEvaluator {
    public Evaluation evaluate(TacticalScenePlan scene, String triggerId) {
        Objects.requireNonNull(scene, "tactical scene required");
        if (!scene.readyForActivation()) throw new IllegalStateException("tactical scene is not ready");
        TacticalTrigger trigger = scene.triggers().stream().filter(value -> value.id().equals(triggerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tactical trigger is not planned"));
        return new Evaluation(trigger.id(), trigger.type().name(), trigger.targetIds(), trigger.transitionId(), trigger.qualifyingAction());
    }
    public Evaluation evaluate(TacticalScenePlan scene, String triggerId, String qualifyingAction) {
        Evaluation evaluation = evaluate(scene, triggerId);
        if (qualifyingAction == null || !evaluation.qualifyingAction().equals(qualifyingAction.trim())) {
            throw new IllegalArgumentException("player action does not qualify the planned tactical trigger");
        }
        return evaluation;
    }
    public record Evaluation(String triggerId, String type, java.util.List<String> targetIds, String transitionId, String qualifyingAction) { }
}

package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.Objects;
import java.util.UUID;

/** Evaluates authored triggers and sends only the resulting planned effect to runtime state. */
public final class TacticalTriggerRuntimeApplicationService {
    private final TacticalTriggerEvaluator evaluator;
    private final TacticalTriggerRuntimePort runtime;
    private final ActiveTacticalMapPort activeMaps;

    public TacticalTriggerRuntimeApplicationService(TacticalTriggerEvaluator evaluator, TacticalTriggerRuntimePort runtime,
            ActiveTacticalMapPort activeMaps) {
        this.evaluator = Objects.requireNonNull(evaluator, "trigger evaluator must not be null");
        this.runtime = Objects.requireNonNull(runtime, "tactical runtime must not be null");
        this.activeMaps = Objects.requireNonNull(activeMaps, "active map lookup must not be null");
    }

    public TacticalTriggerEvaluator.Evaluation apply(TacticalScenePlan scene, String triggerId,
            UUID combatMapId, UUID ownerPlayerId, long expectedVersion, UUID commandId) {
        var evaluation = evaluator.evaluate(scene, triggerId);
        runtime.apply(combatMapId, ownerPlayerId, expectedVersion, commandId, evaluation);
        return evaluation;
    }

    public void bindActiveMap(UUID adventureId, int stagePosition, UUID ownerPlayerId, UUID combatMapId) {
        Objects.requireNonNull(adventureId);
        Objects.requireNonNull(ownerPlayerId);
        Objects.requireNonNull(combatMapId);
        if (stagePosition < 1) throw new IllegalArgumentException("stage position must be positive");
        activeMaps.bindActiveMap(adventureId, stagePosition, ownerPlayerId, combatMapId);
    }

    public TacticalTriggerEvaluator.Evaluation apply(UUID adventureId, int stagePosition, TacticalScenePlan scene,
            String triggerId, UUID combatMapId, UUID ownerPlayerId, long expectedVersion, UUID commandId) {
        UUID active = activeMaps.findActiveMap(adventureId, stagePosition, ownerPlayerId).orElse(null);
        if (active == null || !active.equals(combatMapId)) throw new IllegalArgumentException("combat map is not active for this tactical stage");
        return apply(scene, triggerId, combatMapId, ownerPlayerId, expectedVersion, commandId);
    }

}

package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Evaluates authored triggers and sends only the resulting planned effect to runtime state. */
public final class TacticalTriggerRuntimeApplicationService {
    private final TacticalTriggerEvaluator evaluator;
    private final TacticalTriggerRuntimePort runtime;
    private final java.util.Map<String, UUID> activeMaps = new ConcurrentHashMap<>();

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

    public void bindActiveMap(UUID adventureId, int stagePosition, UUID combatMapId) {
        activeMaps.put(key(adventureId, stagePosition), Objects.requireNonNull(combatMapId));
    }

    public TacticalTriggerEvaluator.Evaluation apply(UUID adventureId, int stagePosition, TacticalScenePlan scene,
            String triggerId, UUID combatMapId, UUID ownerPlayerId, long expectedVersion, UUID commandId) {
        UUID active = activeMaps.get(key(adventureId, stagePosition));
        if (active == null || !active.equals(combatMapId)) throw new IllegalArgumentException("combat map is not active for this tactical stage");
        return apply(scene, triggerId, combatMapId, ownerPlayerId, expectedVersion, commandId);
    }

    private static String key(UUID adventureId, int stagePosition) { return adventureId + ":" + stagePosition; }
}

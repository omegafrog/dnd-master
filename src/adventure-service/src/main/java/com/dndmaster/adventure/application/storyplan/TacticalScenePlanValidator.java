package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.FogPlan;
import com.dndmaster.adventure.domain.adventure.PlacementGrounding;
import com.dndmaster.adventure.domain.adventure.PlacementGroundingType;
import com.dndmaster.adventure.domain.adventure.TacticalEnvironment;
import com.dndmaster.adventure.domain.adventure.TacticalOutcome;
import com.dndmaster.adventure.domain.adventure.TacticalPlacement;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import com.dndmaster.adventure.domain.adventure.TacticalTrigger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Keeps source facts authoritative over tactical AI completion. */
public final class TacticalScenePlanValidator {
    public List<String> validate(TacticalSceneRequest request, TacticalScenePlanCandidate candidate) {
        if (candidate.stagePosition() != request.stage().position()) return List.of("tactical candidate targets the wrong stage");
        TacticalScenePlan scene = candidate.scene();
        if (!scene.readyForActivation()) return List.of("tactical scene is absent");
        Set<String> supplied = new HashSet<>();
        request.citations().forEach(citation -> supplied.add(key(citation)));
        for (var citation : candidate.citations()) {
            if (request.citations().stream().noneMatch(citation::equals)) return List.of("unknown tactical source citation");
            if (citation.quote() == null || citation.quote().isBlank()) return List.of("tactical source citation has no source fact");
        }
        for (PlacementGrounding grounding : groundings(scene)) {
            if (grounding.type() == PlacementGroundingType.SOURCE_CITATION && !supplied.contains(grounding.citation())) {
                return List.of("unknown tactical source citation");
            }
            if (grounding.type() == PlacementGroundingType.SOURCE_CITATION
                    && candidate.citations().stream().map(TacticalScenePlanValidator::key).noneMatch(grounding.citation()::equals)) {
                return List.of("tactical source fact was not supplied by the candidate");
            }
        }
        if (scene.bosses().stream().anyMatch(placement -> placement.grounding().type() != PlacementGroundingType.SOURCE_CITATION)) {
            return List.of("tactical boss requires source citation");
        }
        if (scene.outcomes().stream().anyMatch(outcome -> outcome.grounding().type() != PlacementGroundingType.SOURCE_CITATION)) {
            return List.of("tactical outcome requires source citation");
        }
        return List.of();
    }

    public static String key(AdventureStoryPlanGenerationPort.SourceCitation citation) {
        return citation.documentId() + ":" + citation.locator();
    }

    private static List<PlacementGrounding> groundings(TacticalScenePlan scene) {
        java.util.ArrayList<PlacementGrounding> result = new java.util.ArrayList<>();
        addPlacements(result, scene.players()); addPlacements(result, scene.allies()); addPlacements(result, scene.npcs());
        addPlacements(result, scene.enemies()); addPlacements(result, scene.bosses()); addPlacements(result, scene.interactiveObjects());
        for (TacticalEnvironment environment : scene.environments()) result.add(environment.grounding());
        FogPlan fog = scene.initialFog(); if (fog != null) result.add(fog.grounding());
        for (TacticalTrigger trigger : scene.triggers()) result.add(trigger.grounding());
        for (TacticalOutcome outcome : scene.outcomes()) result.add(outcome.grounding());
        return List.copyOf(result);
    }

    private static void addPlacements(List<PlacementGrounding> groundings, List<TacticalPlacement> placements) {
        placements.forEach(placement -> groundings.add(placement.grounding()));
    }
}

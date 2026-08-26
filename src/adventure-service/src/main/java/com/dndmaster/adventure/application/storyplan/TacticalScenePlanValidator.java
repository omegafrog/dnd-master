package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.FogPlan;
import com.dndmaster.adventure.domain.adventure.PlacementGrounding;
import com.dndmaster.adventure.domain.adventure.PlacementGroundingType;
import com.dndmaster.adventure.domain.adventure.TacticalEnvironment;
import com.dndmaster.adventure.domain.adventure.TacticalOutcome;
import com.dndmaster.adventure.domain.adventure.TacticalPlacement;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import com.dndmaster.adventure.domain.adventure.TacticalTrigger;
import com.dndmaster.adventure.domain.adventure.TacticalTriggerType;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;

/** Keeps source facts authoritative over tactical AI completion. */
public final class TacticalScenePlanValidator {
    private static final Set<TacticalTriggerType> REQUIRED_TRIGGER_TYPES = Set.copyOf(EnumSet.of(
            TacticalTriggerType.COMBAT_ENTRY,
            TacticalTriggerType.ALARM,
            TacticalTriggerType.REINFORCEMENT,
            TacticalTriggerType.BOSS,
            TacticalTriggerType.REWARD,
            TacticalTriggerType.FOG_REVEAL,
            TacticalTriggerType.SUCCESS,
            TacticalTriggerType.FAILURE,
            TacticalTriggerType.EXIT,
            TacticalTriggerType.SURRENDER));
    private final SourceEvidenceReconciliationPort evidence;

    public TacticalScenePlanValidator() {
        this(SourceEvidenceReconciliationPort.exact());
    }

    public TacticalScenePlanValidator(SourceEvidenceReconciliationPort evidence) {
        this.evidence = Objects.requireNonNull(evidence, "source evidence reconciler must not be null");
    }

    public List<String> validate(TacticalSceneRequest request, TacticalScenePlanCandidate candidate) {
        if (candidate.stagePosition() != request.stage().position()) return List.of("tactical candidate targets the wrong stage");
        TacticalScenePlan scene = candidate.scene();
        if (!scene.readyForActivation()) return List.of("tactical scene is absent");
        if (scene.triggers().stream().anyMatch(trigger -> trigger.qualifyingAction() == null || trigger.qualifyingAction().isBlank())) {
            return List.of("tactical trigger qualifying action is missing");
        }
        Set<String> supplied = new HashSet<>();
        request.citations().forEach(citation -> supplied.add(key(citation)));
        List<String> evidenceViolations = evidence.reconcile(request.citations(), candidate.citations(), scene);
        if (!evidenceViolations.isEmpty()) return evidenceViolations;
        for (var citation : candidate.citations()) {
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
        String identityViolation = TacticalEntityIdentityValidator.validate(request, candidate, scene);
        if (identityViolation != null) return List.of(identityViolation);
        if (scene.bosses().stream().anyMatch(placement -> placement.grounding().type() != PlacementGroundingType.SOURCE_CITATION)) {
            return List.of("tactical boss requires source citation");
        }
        if (scene.bosses().stream().anyMatch(placement ->
                !supportsClaim(candidate.citations(), placement.grounding(), placement.id()))) {
            return List.of("tactical boss is not supported by source evidence");
        }
        if (scene.triggers().isEmpty()) return List.of("tactical scene requires explicit trigger coverage");
        Set<TacticalTriggerType> presentTriggerTypes = EnumSet.noneOf(TacticalTriggerType.class);
        scene.triggers().forEach(trigger -> presentTriggerTypes.add(trigger.type()));
        Set<TacticalTriggerType> missingTriggerTypes = EnumSet.copyOf(REQUIRED_TRIGGER_TYPES);
        missingTriggerTypes.removeAll(presentTriggerTypes);
        if (!missingTriggerTypes.isEmpty()) {
            return List.of("tactical scene is missing required trigger types: " + missingTriggerTypes);
        }
        for (TacticalTrigger trigger : scene.triggers()) {
            if (trigger.qualifyingAction() == null || trigger.qualifyingAction().isBlank()) {
                return List.of("tactical trigger qualifying action is missing");
            }
            String violation = unsupportedCoreTriggerViolation(trigger);
            if (violation != null) return List.of(violation);
            if ((trigger.type() == TacticalTriggerType.BOSS || trigger.type() == TacticalTriggerType.REWARD)
                    && trigger.targetIds().stream().anyMatch(target ->
                            !supportsClaim(candidate.citations(), trigger.grounding(), target))) {
                return List.of("tactical " + trigger.type().name().toLowerCase(java.util.Locale.ROOT)
                        + " target is not supported by source evidence");
            }
        }
        Set<String> groundedTransitions = scene.triggers().stream()
                .filter(trigger -> trigger.grounding().type() == PlacementGroundingType.SOURCE_CITATION)
                .map(TacticalTrigger::transitionId)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (!groundedTransitions.containsAll(scene.transitionIds())) {
            return List.of("tactical transition requires source citation");
        }
        for (String transitionId : scene.transitionIds()) {
            if (SourceClaimSupport.structuralTarget(transitionId)) continue;
            boolean supported = scene.triggers().stream()
                    .filter(trigger -> transitionId.equals(trigger.transitionId()))
                    .anyMatch(trigger -> supportsClaim(candidate.citations(), trigger.grounding(), transitionId));
            if (!supported) return List.of("tactical transition is not supported by source evidence");
        }
        if (scene.outcomes().isEmpty()) return List.of("tactical scene requires explicit outcome coverage");
        if (scene.outcomes().stream().anyMatch(outcome -> outcome.grounding().type() != PlacementGroundingType.SOURCE_CITATION)) {
            return List.of("tactical outcome requires source citation");
        }
        if (scene.outcomes().stream().anyMatch(outcome ->
                !supportsClaim(candidate.citations(), outcome.grounding(), outcome.condition()))) {
            return List.of("tactical outcome is not supported by source evidence");
        }
        return List.of();
    }

    public static String key(AdventureStoryPlanGenerationPort.SourceCitation citation) {
        return citation.documentType() + ":" + citation.documentId() + ":"
                + citation.extractionVersion() + ":" + citation.locator();
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

    private static String unsupportedCoreTriggerViolation(TacticalTrigger trigger) {
        if (trigger.grounding().type() == PlacementGroundingType.SOURCE_CITATION) return null;
        if (!trigger.transitionId().isBlank()) return "tactical transition requires source citation";
        return switch (trigger.type()) {
            case BOSS -> "tactical boss trigger requires source citation";
            case REWARD -> "tactical reward requires source citation";
            case SUCCESS, FAILURE, EXIT, SURRENDER -> "tactical outcome trigger requires source citation";
            default -> null;
        };
    }

    private static boolean supportsClaim(
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations,
            PlacementGrounding grounding,
            String claim) {
        if (grounding.type() != PlacementGroundingType.SOURCE_CITATION) return false;
        return citations.stream()
                .filter(citation -> key(citation).equals(grounding.citation()))
                .map(AdventureStoryPlanGenerationPort.SourceCitation::quote)
                .anyMatch(quote -> SourceClaimSupport.supports(quote, claim));
    }
}

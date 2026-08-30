package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.CombatParticipant;
import com.dndmaster.adventure.domain.adventure.CombatRequirement;
import com.dndmaster.adventure.domain.adventure.CombatSkeleton;
import com.dndmaster.adventure.domain.adventure.SourceFactClaim;
import com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Validates the source-grounded, non-tactical combat outline of one stage. */
public final class AdventureStoryPlanCombatValidator {
    public List<AdventureStoryPlanProjectionViolation> validate(AdventureStoryPlanStage stage,
            List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative) {
        if (stage == null) throw new IllegalArgumentException("stage must not be null");
        List<AdventureStoryPlanProjectionViolation> violations = new ArrayList<>();
        boolean combatHint = hasCombatHint(stage);
        if (combatHint && stage.combatRequirement() == CombatRequirement.NONE) {
            violations.add(violation(stage, "COMBAT_REQUIREMENT_MISMATCH", "combatRequirement", "NONE",
                    "stage contains a combat, enemy, or boss hint"));
        }
        if (stage.combatRequirement() == CombatRequirement.REQUIRED) {
            validateCompleteSkeleton(stage, violations);
        }
        if (stage.tacticalPreparationRequirement() == TacticalPreparationRequirement.REQUIRED
                && stage.mapDefinitionId() == null) {
            violations.add(violation(stage, "TACTICAL_REQUIREMENT_NEEDS_MAP", "tacticalPreparationRequirement", "REQUIRED",
                    "required tactical preparation must be attached to a mapped stage"));
        }
        if (stage.combatRequirement() == CombatRequirement.REQUIRED && stage.mapDefinitionId() != null
                && stage.tacticalPreparationRequirement() != TacticalPreparationRequirement.REQUIRED) {
            violations.add(violation(stage, "TACTICAL_PREPARATION_REQUIREMENT_MISMATCH",
                    "tacticalPreparationRequirement", stage.tacticalPreparationRequirement().name(),
                    "mapped required combat must retain required tactical preparation intent"));
        }
        if (stage.tacticalPreparationRequirement() == TacticalPreparationRequirement.REQUIRED
                && stage.combatRequirement() != CombatRequirement.REQUIRED) {
            violations.add(violation(stage, "TACTICAL_PREPARATION_REQUIREMENT_MISMATCH",
                    "tacticalPreparationRequirement", stage.tacticalPreparationRequirement().name(),
                    "tactical preparation requires a required combat stage"));
        }
        validateClaims(stage, authoritative == null ? List.of() : authoritative, violations);
        return List.copyOf(violations);
    }

    private static void validateCompleteSkeleton(AdventureStoryPlanStage stage,
            List<AdventureStoryPlanProjectionViolation> violations) {
        CombatSkeleton skeleton = stage.combatSkeleton();
        if (skeleton.participants().isEmpty()) addMissing(stage, violations, "COMBAT_PARTICIPANTS_REQUIRED", "combatSkeleton.participants");
        if (skeleton.objective().isBlank()) addMissing(stage, violations, "COMBAT_OBJECTIVE_REQUIRED", "combatSkeleton.objective");
        if (skeleton.startTrigger().isBlank()) addMissing(stage, violations, "COMBAT_START_TRIGGER_REQUIRED", "combatSkeleton.startTrigger");
        if (skeleton.successOutcome().isBlank()) addMissing(stage, violations, "COMBAT_SUCCESS_OUTCOME_REQUIRED", "combatSkeleton.successOutcome");
        if (skeleton.failureOutcome().isBlank()) addMissing(stage, violations, "COMBAT_FAILURE_OUTCOME_REQUIRED", "combatSkeleton.failureOutcome");
    }

    private static void validateClaims(AdventureStoryPlanStage stage,
            List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative,
            List<AdventureStoryPlanProjectionViolation> violations) {
        Map<String, AdventureStoryPlanGenerationPort.SourceCitation> sources = new HashMap<>();
        for (AdventureStoryPlanGenerationPort.SourceCitation source : authoritative) {
            if (source.citationKey() != null && !source.citationKey().isBlank()) sources.put(source.citationKey(), source);
        }
        Map<String, AdventurePlanEvidence> evidence = new HashMap<>();
        for (AdventurePlanEvidence item : stage.evidence()) {
            if (item.citationKey() != null && !item.citationKey().isBlank()) evidence.put(item.citationKey(), item);
        }
        for (SourceFactClaim claim : allClaims(stage)) {
            String path = claimPath(stage, claim.fieldPath());
            if (!isCombatFieldPath(claim.fieldPath())) {
                violations.add(violation(stage, "SOURCE_FACT_CLAIM_FIELD_INVALID", path, claim.normalizedClaim(),
                        "source fact claim must target a combat skeleton field"));
                continue;
            }
            for (String key : claim.citationKeys()) {
                if (!evidence.containsKey(key)) {
                    violations.add(violation(stage, "SOURCE_FACT_CLAIM_UNBOUND", path, claim.normalizedClaim(),
                            "source fact citation key is not present in this stage evidence: " + key));
                    continue;
                }
                var source = sources.get(key);
                if (source == null) {
                    violations.add(repairableViolation(stage, "SOURCE_FACT_CLAIM_UNKNOWN_CITATION", path, claim.normalizedClaim(),
                            "source fact citation key is not registered: " + key));
                } else if (!supports(source.quote(), claim.normalizedClaim())) {
                    violations.add(violation(stage, "SOURCE_FACT_CLAIM_UNSUPPORTED", path, claim.normalizedClaim(),
                            "source citation does not support the field-specific claim"));
                }
            }
        }
        if (stage.combatRequirement() == CombatRequirement.REQUIRED) {
            for (int index = 0; index < stage.combatSkeleton().participants().size(); index++) {
                CombatParticipant participant = stage.combatSkeleton().participants().get(index);
                if (participant.citationKeys().isEmpty()) {
                    violations.add(violation(stage, "COMBAT_PARTICIPANT_SOURCE_REQUIRED",
                            "combatSkeleton.participants[" + index + "].name", participant.name(),
                            "combat participant must carry field-specific source keys"));
                }
                for (String key : participant.citationKeys()) {
                    if (!evidence.containsKey(key) || !sources.containsKey(key)
                            || !supports(sources.get(key).quote(), participant.name())) {
                        violations.add(repairableViolation(stage, "COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED",
                                "combatSkeleton.participants[" + index + "].name", participant.name(),
                                "combat participant is not supported by its field-specific source"));
                    } else if (!supportsCount(sources.get(key).quote(), participant.minimumCount(), participant.maximumCount())) {
                        violations.add(violation(stage, "COMBAT_PARTICIPANT_COUNT_UNSUPPORTED",
                                "combatSkeleton.participants[" + index + "].minimumCount",
                                Integer.toString(participant.minimumCount()),
                                "combat participant count is not supported by its field-specific source"));
                    }
                }
            }
        }
    }

    private static AdventureStoryPlanProjectionViolation repairableViolation(AdventureStoryPlanStage stage,
            String code, String path, String rejectedValue, String message) {
        return new AdventureStoryPlanProjectionViolation(code, stage.position(),
                path.startsWith("stages[") ? path : "stages[" + Math.max(0, stage.position() - 1) + "]." + path,
                rejectedValue, "authoritative source evidence",
                AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE, message);
    }

    private static List<SourceFactClaim> allClaims(AdventureStoryPlanStage stage) {
        List<SourceFactClaim> claims = new ArrayList<>(stage.sourceFactClaims());
        claims.addAll(stage.combatSkeleton().rewards());
        return claims;
    }

    private static String claimPath(AdventureStoryPlanStage stage, String fieldPath) {
        String normalized = fieldPath.trim();
        if (normalized.startsWith("stages[")) return normalized;
        return "stages[" + Math.max(0, stage.position() - 1) + "]." + normalized;
    }

    private static boolean isCombatFieldPath(String fieldPath) {
        String normalized = fieldPath == null ? "" : fieldPath.trim();
        return normalized.equals("combatSkeleton.objective")
                || normalized.equals("combatSkeleton.startTrigger")
                || normalized.equals("combatSkeleton.successOutcome")
                || normalized.equals("combatSkeleton.failureOutcome")
                || normalized.matches("combatSkeleton\\.participants\\[[0-9]+\\]\\.(participantId|role|name|minimumCount|maximumCount)")
                || normalized.matches("combatSkeleton\\.rewards\\[[0-9]+\\]");
    }

    private static boolean hasCombatHint(AdventureStoryPlanStage stage) {
        if (!stage.enemies().isEmpty() || !stage.boss().isBlank() || !stage.combatSkeleton().participants().isEmpty()) return true;
        if (stage.stageType() == com.dndmaster.adventure.domain.adventure.AdventureStageType.ENCOUNTER
                || stage.stageType() == com.dndmaster.adventure.domain.adventure.AdventureStageType.FINALE) return true;
        String text = String.join(" ", stage.title(), stage.goal(), stage.conflict(), stage.transitionCondition(),
                stage.clearCondition(), stage.failureCondition(), String.join(" ", stage.npcOrClues())).toLowerCase(Locale.ROOT);
        return text.matches(".*(combat|battle|fight|encounter|ambush|enemy|boss|monster|attack|전투|전투|적|보스|괴물|습격|싸움|거미|쥐).*" );
    }

    private static boolean supports(String source, String claim) {
        if (SourceClaimSupport.supports(source, claim)) return true;
        String normalizedClaim = SourceClaimSupport.normalize(claim);
        String normalizedSource = SourceClaimSupport.normalize(source);
        return (" " + normalizedSource + " ").contains(" " + normalizedClaim + "s ");
    }

    private static boolean supportsCount(String source, int minimum, int maximum) {
        if (minimum == 1 && maximum == 1) return true;
        String normalized = SourceClaimSupport.normalize(source);
        if (minimum == maximum) {
            String number = Integer.toString(minimum);
            if (normalized.matches(".*(^|[^0-9])" + number + "([^0-9]|$).*")) return true;
            String word = switch (minimum) {
                case 2 -> "two|둘|두";
                case 3 -> "three|셋|세";
                case 4 -> "four|넷|네";
                case 5 -> "five|다섯";
                default -> "";
            };
            return !word.isBlank() && normalized.matches(".*(" + word + ").*");
        }
        return normalized.matches(".*[0-9]+\\s*[-~]\\s*[0-9]+.*") || normalized.contains("several") || normalized.contains("여러");
    }

    private static void addMissing(AdventureStoryPlanStage stage, List<AdventureStoryPlanProjectionViolation> violations,
            String code, String field) {
        violations.add(violation(stage, code, field, "", field + " is required for combat REQUIRED"));
    }

    private static AdventureStoryPlanProjectionViolation violation(AdventureStoryPlanStage stage, String code,
            String field, String rejected, String message) {
        return new AdventureStoryPlanProjectionViolation(code, stage.position(),
                "stages[" + Math.max(0, stage.position() - 1) + "]." + field, rejected, "authoritative field evidence",
                requiresRegeneration(code)
                        ? AdventureStoryPlanProjectionViolation.Repairability.REGENERATE_REQUIRED
                        : AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE, message);
    }

    private static boolean requiresRegeneration(String code) {
        return code.equals("SOURCE_FACT_CLAIM_FIELD_INVALID")
                || code.equals("SOURCE_FACT_CLAIM_UNBOUND")
                || code.equals("SOURCE_FACT_CLAIM_UNSUPPORTED")
                || code.equals("COMBAT_REQUIREMENT_MISMATCH")
                || code.equals("COMBAT_PARTICIPANT_SOURCE_REQUIRED")
                || code.equals("COMBAT_PARTICIPANT_COUNT_UNSUPPORTED");
    }
}

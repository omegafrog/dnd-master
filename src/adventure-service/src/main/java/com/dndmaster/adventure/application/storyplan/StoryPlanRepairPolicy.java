package com.dndmaster.adventure.application.storyplan;

import java.util.List;
import java.util.Locale;

/** Repair-first classification policy for story-plan validation failures. */
public final class StoryPlanRepairPolicy {
    private StoryPlanRepairPolicy() { }

    public static AdventureStoryPlanProjectionViolation.Repairability classify(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MISSING_RULE_CHECK", "MISSING_RULE_OUTCOME",
                    "SOURCE_FACT_CLAIM_UNKNOWN_CITATION", "UNKNOWN_CITATION", "UNKNOWN_CITATION_KEY",
                    "COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED" ->
                    AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE;
            case "SOURCE_EVIDENCE_INSUFFICIENT", "UNKNOWN_SOURCE_EVIDENCE", "SOURCE_CLAIM_UNSUPPORTED",
                    "SOURCE_FACT_CLAIM_UNSUPPORTED", "SOURCE_FACT_CLAIM_UNBOUND" ->
                    AdventureStoryPlanProjectionViolation.Repairability.SOURCE_EVIDENCE_INSUFFICIENT;
            case "SYSTEM_CONTRACT_ERROR", "CANDIDATE_SERIALIZATION_ERROR" ->
                    AdventureStoryPlanProjectionViolation.Repairability.SYSTEM_CONTRACT_ERROR;
            case "REQUIRED_FIELD_MISSING", "INVALID_TRANSITION_CONDITION", "INVALID_CLEAR_CONDITION",
                    "INVALID_FAILURE_CONDITION" -> AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE;
            default -> AdventureStoryPlanProjectionViolation.Repairability.REGENERATE_REQUIRED;
        };
    }

    public static Decision decide(List<AdventureStoryPlanProjectionViolation> violations,
            RepairScope scope, boolean regenerationUsed) {
        if (violations == null || violations.isEmpty()) return Decision.READY;
        if (scope != null && !regenerationUsed && scope.isRepairable()
                && violations.stream().allMatch(item -> classify(item.code()) ==
                        AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE)) {
            return Decision.REPAIR;
        }
        return regenerationUsed ? Decision.BLOCKED : Decision.FULL_REGENERATION;
    }

    public enum Decision { READY, REPAIR, FULL_REGENERATION, BLOCKED }
}

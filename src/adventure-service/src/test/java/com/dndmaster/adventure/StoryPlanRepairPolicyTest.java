package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation;
import com.dndmaster.adventure.application.storyplan.StoryPlanRepairPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoryPlanRepairPolicyTest {
    @Test
    void classifies_supported_scoped_contract_failures_as_repairable() {
        for (String code : List.of("MISSING_RULE_CHECK", "MISSING_RULE_OUTCOME",
                "SOURCE_FACT_CLAIM_UNKNOWN_CITATION", "UNKNOWN_CITATION", "UNKNOWN_CITATION_KEY",
                "COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED")) {
            assertEquals(AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE,
                    StoryPlanRepairPolicy.classify(code));
        }
    }

    @Test
    void chooses_scoped_repair_for_provider_unknown_citation_diagnostics() {
        var violation = new AdventureStoryPlanProjectionViolation(
                "UNKNOWN_CITATION", 1, "stages[0].evidence[0].citationKey", "citation-999", "citation-999",
                AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE, "citation key is not registered");

        assertEquals(StoryPlanRepairPolicy.Decision.REPAIR,
                StoryPlanRepairPolicy.decide(List.of(violation),
                        new com.dndmaster.adventure.application.storyplan.RepairScope(
                                java.util.Set.of(violation.fieldPath()), java.util.Set.of(), java.util.Set.of()), false));
    }

    @Test
    void classifies_structural_failures_as_regeneration_required() {
        assertEquals(AdventureStoryPlanProjectionViolation.Repairability.REGENERATE_REQUIRED,
                StoryPlanRepairPolicy.classify("STRUCTURAL_CONTRACT_VIOLATION"));
    }
}

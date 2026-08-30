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
                "SOURCE_FACT_CLAIM_UNKNOWN_CITATION", "COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED")) {
            assertEquals(AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE,
                    StoryPlanRepairPolicy.classify(code));
        }
    }

    @Test
    void classifies_structural_failures_as_regeneration_required() {
        assertEquals(AdventureStoryPlanProjectionViolation.Repairability.REGENERATE_REQUIRED,
                StoryPlanRepairPolicy.classify("STRUCTURAL_CONTRACT_VIOLATION"));
    }
}

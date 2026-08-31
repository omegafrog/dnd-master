package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanCandidateValidationException;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation.Repairability;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanCandidateValidationExceptionTest {
    @Test
    void maps_legacy_unknown_citation_diagnostic_to_scoped_repair() {
        var violation = AdventureStoryPlanCandidateValidationException.legacyViolation(
                "Stage 1 unknown citation key: citation-999");

        assertEquals("CITATION_CONTRACT_VIOLATION", violation.code());
        assertEquals(Repairability.REPAIRABLE, violation.repairability());
        assertEquals("stages[0].evidence[*].citationKey", violation.fieldPath());
    }

    @Test
    void maps_legacy_unsupported_combat_participant_to_combat_scope_repair() {
        var violation = AdventureStoryPlanCandidateValidationException.legacyViolation(
                "Stage 2 combat participant is not supported by its field-specific source: goblin");

        assertEquals("COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED", violation.code());
        assertEquals(Repairability.REPAIRABLE, violation.repairability());
        assertEquals("stages[1].combatSkeleton.participants[*].name", violation.fieldPath());
    }

    @Test
    void maps_burning_web_consequence_to_failure_condition_scope_repair() {
        var violation = AdventureStoryPlanCandidateValidationException.legacyViolation(
                "Stage 2 burning-web consequence is missing");

        assertEquals("FAILURE_CONSEQUENCE_MISSING", violation.code());
        assertEquals(Repairability.REPAIRABLE, violation.repairability());
        assertEquals("stages[1].failureCondition", violation.fieldPath());
    }

    @Test
    void maps_missing_ending_ids_to_the_exact_stage_field_and_repair() {
        var violation = AdventureStoryPlanCandidateValidationException.legacyViolation(
                "Stage 3 endingIds must be explicit");

        assertEquals("ENDING_IDS_MISSING", violation.code());
        assertEquals(Repairability.REPAIRABLE, violation.repairability());
        assertEquals("stages[2].endingIds", violation.fieldPath());
    }
}

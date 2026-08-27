package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanCombatValidator;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.CombatParticipant;
import com.dndmaster.adventure.domain.adventure.CombatRequirement;
import com.dndmaster.adventure.domain.adventure.CombatSkeleton;
import com.dndmaster.adventure.domain.adventure.SourceFactClaim;
import com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanCombatValidatorTest {
    @Test
    void required_combat_needs_a_complete_skeleton() {
        var stage = stage().withCombat(CombatRequirement.REQUIRED, CombatSkeleton.empty(), List.of(), TacticalPreparationRequirement.REQUIRED);

        var violations = new AdventureStoryPlanCombatValidator().validate(stage, List.of());

        assertTrue(violations.stream().anyMatch(v -> v.code().equals("COMBAT_PARTICIPANTS_REQUIRED")));
        assertTrue(violations.stream().anyMatch(v -> v.code().equals("COMBAT_OBJECTIVE_REQUIRED")));
        assertTrue(violations.stream().anyMatch(v -> v.code().equals("COMBAT_START_TRIGGER_REQUIRED")));
        assertTrue(violations.stream().anyMatch(v -> v.code().equals("COMBAT_SUCCESS_OUTCOME_REQUIRED")));
        assertTrue(violations.stream().anyMatch(v -> v.code().equals("COMBAT_FAILURE_OUTCOME_REQUIRED")));
    }

    @Test
    void non_combat_stage_explicitly_none_allows_empty_participants() {
        var stage = stage().withCombat(CombatRequirement.NONE, CombatSkeleton.empty(), List.of(), TacticalPreparationRequirement.NOT_REQUIRED);

        var violations = new AdventureStoryPlanCombatValidator().validate(stage, List.of());

        assertTrue(violations.isEmpty());
    }

    @Test
    void field_specific_claim_must_bind_to_stage_evidence_and_storybook_fact() {
        UUID storybook = UUID.randomUUID();
        var citation = new AdventureStoryPlanGenerationPort.SourceCitation(
                "STORYBOOK", storybook, 1, "page:3", "The cellar contains two giant rats.", .9).withCitationKey("rat-fact");
        var participant = new CombatParticipant("rat", CombatParticipant.Role.ENEMY, "giant rat", 2, 2, List.of("rat-fact"));
        var skeleton = new CombatSkeleton("Drive the rats from the cellar", "When the party enters the cellar",
                List.of(participant), "The rats are defeated", "The party retreats and may return", List.of());
        var stage = stage().withCombat(CombatRequirement.REQUIRED, skeleton,
                List.of(new SourceFactClaim("combatSkeleton.participants[0].name", "giant rat", List.of("rat-fact"))),
                TacticalPreparationRequirement.REQUIRED);
        stage = stage.withEvidence(List.of(new com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence(
                "STORYBOOK", storybook, 1, "page:3", "The cellar contains two giant rats.", .9, citation.provenance(), "rat-fact")));

        var violations = new AdventureStoryPlanCombatValidator().validate(stage, List.of(citation));

        assertFalse(violations.stream().anyMatch(v -> v.code().equals("COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED")));
        assertTrue(violations.stream().noneMatch(v -> v.fieldPath().equals("stages[0].combatSkeleton.participants[0].name")
                && v.code().equals("SOURCE_FACT_CLAIM_UNBOUND")));
    }

    @Test
    void combat_hint_cannot_be_classified_as_none() {
        var stage = stage().withCombat(CombatRequirement.NONE, CombatSkeleton.empty(), List.of(), TacticalPreparationRequirement.NOT_REQUIRED)
                .withConflict("A goblin ambush blocks the road");

        var violations = new AdventureStoryPlanCombatValidator().validate(stage, List.of());

        assertEquals("COMBAT_REQUIREMENT_MISMATCH", violations.getFirst().code());
    }

    private static AdventureStoryPlanStage stage() {
        return new AdventureStoryPlanStage(1, "Cellar", "Explore", "Threat", "Continue", List.of(), List.of("ending-1"));
    }
}

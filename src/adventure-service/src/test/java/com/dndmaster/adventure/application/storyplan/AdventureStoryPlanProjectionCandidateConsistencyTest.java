package com.dndmaster.adventure.application.storyplan;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence;
import com.dndmaster.adventure.domain.adventure.CombatParticipant;
import com.dndmaster.adventure.domain.adventure.CombatRequirement;
import com.dndmaster.adventure.domain.adventure.CombatSkeleton;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanProjectionCandidateConsistencyTest {
    @Test
    void preserves_full_evidence_when_serializing_a_domain_projection() throws Exception {
        UUID documentId = UUID.randomUUID();
        AdventureStoryPlanStage stage = new AdventureStoryPlanStage(
                1, "The cellar", "Find the key", "The gate is sealed", "The key is found",
                List.of("A locked chest"), List.of("stage-2"))
                .withEvidence(List.of(new AdventurePlanEvidence(
                        "STORYBOOK", documentId, 2, "page:3", "The cellar contains two giant rats.", .9)));

        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(AdventureStoryPlanProjectionCandidateConsistency.serialize(List.of(stage)));
        var evidence = root.path("stages").get(0).path("evidence").get(0);

        org.junit.jupiter.api.Assertions.assertEquals(documentId.toString(), evidence.path("documentId").asText());
        org.junit.jupiter.api.Assertions.assertEquals("The cellar contains two giant rats.", evidence.path("quote").asText());
        assertDoesNotThrow(() -> AdventureStoryPlanProjectionCandidateConsistency.assertEquivalent(
                mapper.writeValueAsString(root), List.of(stage)));
    }

    @Test
    void accepts_provider_projection_that_omits_participant_count_defaults() throws Exception {
        AdventureStoryPlanStage stage = new AdventureStoryPlanStage(
                2, "The cellar", "Find the key", "The gate is sealed", "The key is found",
                List.of("A locked chest"), List.of("stage-3"))
                .withCombat(CombatRequirement.REQUIRED,
                        new CombatSkeleton("Protect the chest", "When it opens",
                                List.of(new CombatParticipant("guard", CombatParticipant.Role.ENEMY,
                                        "Cellar guard", 1, 1, List.of("citation-1"))),
                                "The chest is secured", "The party retreats", List.of()),
                        List.of(), com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement.NOT_REQUIRED);

        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(AdventureStoryPlanProjectionCandidateConsistency.serialize(List.of(stage)));
        ObjectNode participant = (ObjectNode) root.path("stages").get(0).path("combatSkeleton").path("participants").get(0);
        participant.remove("minimumCount");
        participant.remove("maximumCount");

        assertDoesNotThrow(() -> AdventureStoryPlanProjectionCandidateConsistency.assertEquivalent(
                mapper.writeValueAsString(root), List.of(stage)));
    }
}

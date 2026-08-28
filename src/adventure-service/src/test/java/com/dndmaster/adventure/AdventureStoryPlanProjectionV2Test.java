package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.CombatParticipant;
import com.dndmaster.adventure.domain.adventure.CombatRequirement;
import com.dndmaster.adventure.domain.adventure.CombatSkeleton;
import com.dndmaster.adventure.domain.adventure.SourceFactClaim;
import com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanProjectionV2Test {
    @Test
    void v2_projection_contains_grounding_and_tactical_intent_without_coordinates() throws Exception {
        var participant = new CombatParticipant("rat", CombatParticipant.Role.ENEMY, "giant rat", 2, 2, List.of("rat-fact"));
        var skeleton = new CombatSkeleton("Defeat the rats", "When the party enters", List.of(participant),
                "Rats are defeated", "Retreat and return", List.of());
        var stage = new AdventureStoryPlanStage(1, "Cellar", "Explore", "Rats attack", "Continue", List.of(), List.of("ending-1"))
                .withCombat(CombatRequirement.REQUIRED, skeleton,
                        List.of(new SourceFactClaim("combatSkeleton.participants[0].name", "giant rat", List.of("rat-fact"))),
                        TacticalPreparationRequirement.REQUIRED);

        JsonNode json = new ObjectMapper().readTree(AdventureStoryPlanGenerationPort.ProjectionCandidate.fromStages(List.of(stage)).serializedCandidate());

        assertEquals(2, json.path("schemaVersion").asInt());
        assertEquals("REQUIRED", json.at("/stages/0/combatRequirement").asText());
        assertEquals("REQUIRED", json.at("/stages/0/tacticalPreparationRequirement").asText());
        assertEquals("giant rat", json.at("/stages/0/combatSkeleton/participants/0/name").asText());
        assertEquals("rat-fact", json.at("/stages/0/sourceFactClaims/0/citationKeys/0").asText());
        assertFalse(json.at("/stages/0").has("coordinates"));
    }

    @Test
    void player_projection_does_not_expose_hidden_combat_or_ending_fields() throws Exception {
        var stage = new AdventureStoryPlanStage(1, "Cellar", "Explore", "Hidden rats", "Continue", List.of(), List.of("ending-1"))
                .withCombat(CombatRequirement.REQUIRED, new CombatSkeleton("Defeat rats", "Enter", List.of(
                        new CombatParticipant("rat", CombatParticipant.Role.ENEMY, "giant rat", 2, 2, List.of("rat-fact"))),
                        "Clear", "Retreat", List.of()), List.of(), TacticalPreparationRequirement.REQUIRED);
        var plan = com.dndmaster.adventure.domain.adventure.AdventureStoryPlan.ready(
                com.dndmaster.adventure.domain.adventure.SessionId.generate(), 0, 1, List.of(stage));
        var view = com.dndmaster.adventure.api.AdventureStoryPlanController.PlayerPlanView.from(plan);
        String json = new ObjectMapper().writeValueAsString(view);

        assertTrue(json.contains("Cellar"));
        assertFalse(json.contains("combatRequirement"));
        assertFalse(json.contains("giant rat"));
        assertFalse(json.contains("ending-1"));
    }
}

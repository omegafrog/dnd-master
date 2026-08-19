package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanControllerMarkdownTest {
    @Test
    void preserves_supplied_map_identity_from_markdown_stage() {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5));
        String markdown = """
                # Adventure Plan
                ## Stage 1: Cellar
                - Type: dungeon
                - Location: Beer Cellar
                - Goal: Clear the rats
                - Conflict: Giant rats ambush the party
                - Clear condition: The rats are defeated
                - Enemies: Giant Rats
                - Boss: none
                - Rewards: 25 gp
                - Branches: ending-1
                - Map definition ID: 859f67d2-1a4b-3679-b832-2c3ad59c6e9d
                - Map asset: page
                - Map locator: page
                ## Stage 2: Corridor
                - Type: event
                - Goal: Cross the corridor
                - Conflict: A mosaic trap blocks passage
                - Clear condition: The party crosses safely
                ## Stage 3: Return
                - Type: town
                - Goal: Return to Glowkindle
                - Conflict: Report the result
                - Clear condition: Payment is received
                """;

        var stages = controller.parseMarkdown(markdown,
                new AdventureStoryPlanController.Configuration(1, "SHORT"));

        assertEquals("859f67d2-1a4b-3679-b832-2c3ad59c6e9d", stages.getFirst().mapDefinitionId());
        assertEquals("page", stages.getFirst().mapAssetId());
        assertEquals("page", stages.getFirst().mapAssetLocator());
    }

    @Test
    void tactical_scene_generation_requires_internal_token() {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("service-secret"));

        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.generateTacticalScene("wrong", new ObjectMapper().createObjectNode()));
    }
}

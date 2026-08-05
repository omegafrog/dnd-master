package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.GmToolDefinition;
import com.dndmaster.adventure.application.runtime.StoryContinuityToolRegistry;
import org.junit.jupiter.api.Test;

class StoryContinuityToolRegistryTest {
    @Test
    void exposes_only_domain_continuity_tools_with_versioned_inputs() {
        var tools = StoryContinuityToolRegistry.definitions(invocation -> null, invocation -> null);
        assertTrue(tools.stream().anyMatch(tool -> tool.name().equals("revise_story_plan") && tool.inputSchema().contains("expectedPlanVersion") && tool.inputSchema().contains("commandId")));
        assertTrue(tools.stream().anyMatch(tool -> tool.name().equals("advance_game_time")
                && tool.inputSchema().contains("expectedClockVersion")
                && tool.inputSchema().contains("commandId")
                && tool.inputSchema().contains("ruleSecondsPerTurn")));
    }
}

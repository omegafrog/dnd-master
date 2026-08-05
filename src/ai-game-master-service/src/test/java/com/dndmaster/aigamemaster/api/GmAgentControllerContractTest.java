package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class GmAgentControllerContractTest {
    @Test
    void accepts_complete_read_only_structured_response() {
        assertDoesNotThrow(() -> GmAgentController.requireComplete(
                new GmAgentController.Response("scene", "npc", "judgment", "narration", null,
                        List.of(), List.of(), "ollama", "qwen3:8b", "reasoning", List.of())));
    }

    @Test
    void rejects_missing_or_mutating_structured_response_fields() {
        assertThrows(IllegalArgumentException.class, () -> GmAgentController.requireComplete(
                new GmAgentController.Response("scene", "npc", "judgment", "narration", null,
                        null, List.of(), "ollama", "qwen3:8b", "reasoning", List.of())));
        assertThrows(IllegalArgumentException.class, () -> GmAgentController.requireComplete(
                new GmAgentController.Response("scene", "npc", "judgment", "narration", null,
                        List.of(), List.of(), "ollama", "qwen3:8b", "reasoning", List.of("hp=1"))));
    }
}

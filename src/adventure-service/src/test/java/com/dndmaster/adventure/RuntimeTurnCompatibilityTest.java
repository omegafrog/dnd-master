package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.infrastructure.persistence.RuntimeTurnCompatibilityException;
import com.dndmaster.adventure.infrastructure.persistence.RuntimeTurnJsonCompatibilityAdapter;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeTurnCompatibilityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void restores_legacy_combined_runtime_plan_with_persisted_identity_defaults() throws Exception {
        UUID turnId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        RuntimeTurn current = new RuntimeTurn(turnId, commandId, new AdventureId(UUID.randomUUID()), sessionId,
                packageId, 1L, "open", new EvidencePack(List.of(), List.of(), List.of()),
                new RuntimePlan("door", "quiet", "success", "The door opens.", null, List.of(), List.of(),
                        "provider", "model", "private reasoning"), null,
                new AdventureContext("door", "quiet", "open", "success"),
                List.of(new ConversationEntry(0, "AI_GAME_MASTER", "The door opens.")), 1L, List.of(), List.of());

        ObjectNode legacy = mapper.valueToTree(current);
        legacy.remove(List.of("commandId", "sessionId", "origin", "playerOrigin", "advancesState", "lifecycle", "resolvedPlan"));
        ObjectNode plan = (ObjectNode) legacy.get("plan");
        plan.remove(List.of("provider", "model", "reasoning", "advanceStoryPlan", "selectedBranchId",
                "requestedSelection", "effectiveSelection", "attemptCount", "citationBindings", "stateDelta"));

        RuntimeTurn restored = RuntimeTurnJsonCompatibilityAdapter.read(mapper, mapper.writeValueAsString(legacy),
                turnId, commandId, sessionId, packageId);

        assertEquals(commandId, restored.commandId());
        assertEquals(sessionId, restored.sessionId());
        assertEquals("legacy", restored.plan().provider());
        assertEquals("legacy", restored.plan().model());
        assertEquals(1, restored.plan().attemptCount());
        assertEquals(List.of(), restored.plan().citationBindings());
    }

    @Test
    void rejects_unreadable_legacy_payload_as_compatibility_error() {
        assertThrows(RuntimeTurnCompatibilityException.class, () -> RuntimeTurnJsonCompatibilityAdapter.read(
                mapper, "{not-json", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }
}

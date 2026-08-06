package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.*;
import com.dndmaster.adventure.domain.adventure.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GmAgentToolWiringTest {
    @Test
    void agentPlanToolCallsCrossCapabilityGatewayAndSagaBeforeFinalValidation() {
        UUID session = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        GmAgentPort agent = context -> new GmPlanResult(
                new RuntimePlan("scene", "npc", "judgment", "narration", null, List.of(), List.of()),
                "test", "test", "reasoning", List.of(), List.of(new GmToolCall("dice.roll", "{}", true)));
        GmToolGateway gateway = (capability, invocation) -> { calls.incrementAndGet(); return GmToolOutcome.completed("rolled"); };
        RuntimePlanningRequest request = new RuntimePlanningRequest(
                new AdventureId(UUID.randomUUID()), new OwnerPlayerId(UUID.randomUUID()), session, turn,
                UUID.randomUUID(), 1, new AdventureContext("scene", "npc", null, null), null, "open door", new EvidencePack(List.of(), List.of(), List.of()), List.of(), List.of(), "");

        RuntimePlan plan = new GmAgentRuntimePlanningAdapter(agent, new GmFinalValidator(), gateway,
                new RuntimeCommandSagaApplicationService(new InMemoryRuntimeCommandJournal())).plan(request);

        assertEquals("narration", plan.narration());
        assertEquals(1, calls.get());
    }

    @Test
    void repairs_invalid_plan_once_then_revalidates() {
        RuntimeEvidence rule = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1", "rule");
        GmAgentPort agent = new GmAgentPort() {
            public GmPlanResult plan(GmContextEnvelope ignored) {
                return new GmPlanResult(new RuntimePlan("scene", "npc", "the rule says roll", "narration", null,
                        List.of(), List.of()), "test", "test", "reasoning", List.of());
            }

            public GmPlanResult repairPlan(GmContextEnvelope ignored, GmPlanResult failed, String reason) {
                return new GmPlanResult(new RuntimePlan("scene", "npc", "the rule says roll", "narration", null,
                        List.of(rule), List.of()), "test", "test", "reasoning", List.of());
            }
        };
        RuntimePlan plan = new GmAgentRuntimePlanningAdapter(agent, new GmFinalValidator()).plan(
                new RuntimePlanningRequest(new AdventureId(UUID.randomUUID()), new OwnerPlayerId(UUID.randomUUID()),
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                        new AdventureContext("scene", "npc", null, null), null, "open door",
                        new EvidencePack(List.of(), List.of(rule), List.of()), List.of(), List.of(), ""));

        assertEquals(List.of(rule), plan.citedEvidence());
        assertTrue(plan.warnings().contains("repair-attempted=true"));
    }

    @Test
    void refuses_invalid_plan_when_repair_unavailable() {
        GmAgentPort agent = context -> new GmPlanResult(
                new RuntimePlan("scene", "npc", "the rule says roll", "narration", null, List.of(), List.of()),
                "test", "test", "reasoning", List.of());
        RuntimePlan plan = new GmAgentRuntimePlanningAdapter(agent, new GmFinalValidator()).plan(
                new RuntimePlanningRequest(new AdventureId(UUID.randomUUID()), new OwnerPlayerId(UUID.randomUUID()),
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                        new AdventureContext("scene", "npc", null, null), null, "open door",
                        new EvidencePack(List.of(), List.of(), List.of()), List.of(), List.of(), ""));

        assertTrue(plan.narration().contains("cannot provide a grounded result"));
        assertTrue(plan.warnings().stream().anyMatch(warning -> warning.startsWith("degraded-mode:")));
    }
}

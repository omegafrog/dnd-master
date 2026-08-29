package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.NarrativeVerificationContext;
import com.dndmaster.adventure.application.runtime.ResolvedTurnPlan;
import com.dndmaster.adventure.application.runtime.TurnPlan;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import com.dndmaster.adventure.domain.runtime.narrative.WorldFact;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.SubmitRuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.WriterContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

class NarrativeRuntimeIntegrationTest {
    @Test
    void verification_context_uses_projected_knowledge_and_hides_unknown_world_facts() {
        NarrativeState state = NarrativeState.empty()
                .addWorldFact(new WorldFact("known", "the bell rings", false))
                .addWorldFact(new WorldFact("secret", "the vault is sealed", false))
                .recordKnowledge("player", "known");

        var projected = state.project("player", "hall");
        var context = NarrativeVerificationContext.from(
                ResolvedTurnPlan.of(new TurnPlan("hall", "guard", "wait", List.of(), List.of()), List.of("wait")),
                state, projected, new EvidencePack(List.of(), List.of(), List.of()));

        assertTrue(context.supportedFacts().contains("the bell rings"));
        assertTrue(context.hiddenFacts().contains("the vault is sealed"));
        assertEquals("hall | wait | outcomes=wait", context.turnPlanSummary());
    }

    @Test
    void default_verifier_does_not_treat_a_missing_semantic_provider_as_an_unconditional_pass() {
        var context = new NarrativeVerificationContext("hall", List.of("the bell rings"), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var result = new com.dndmaster.adventure.application.runtime.DefaultNarrativeVerifier(null)
                .verify(context, "An unrelated claim is made.");

        assertTrue(result.hasErrors());
    }

    @Test
    void citation_alone_does_not_reveal_a_world_fact() {
        NarrativeState state = NarrativeState.empty()
                .addWorldFact(new WorldFact("secret", "the vault is sealed", false));
        RuntimePlan plan = new RuntimePlan("hall", "guard", "wait", "A citation exists.", null,
                List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK, new KnowledgeDocumentId(UUID.randomUUID()), 1,
                        "p1", "source", "secret")), List.of());

        var delta = com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService.deltaFor(
                state, new SubmitRuntimeTurnCommand(new AdventureId(UUID.randomUUID()), new OwnerPlayerId(UUID.randomUUID()),
                        UUID.randomUUID(), UUID.randomUUID(), "wait"), plan);

        assertTrue(delta.revealedFactIds().isEmpty());
    }

    @Test
    void writer_context_intersects_planner_facts_with_actor_projection() {
        NarrativeState state = NarrativeState.empty()
                .addWorldFact(new WorldFact("known", "the bell rings", false))
                .addWorldFact(new WorldFact("secret", "the vault is sealed", false))
                .recordKnowledge("player", "known");
        var context = state.project("player", "hall");
        var writer = WriterContext.of(context, ResolvedTurnPlan.of(
                new TurnPlan("hall", "guard", "wait", List.of("known", "secret"), List.of()), List.of()), List.of());

        assertEquals(List.of("known"), writer.visibleFacts());
        assertEquals("player", writer.narrativeContext().actorId());
    }
}

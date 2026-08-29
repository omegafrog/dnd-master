package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.NarrativeVerificationContext;
import com.dndmaster.adventure.application.runtime.ResolvedTurnPlan;
import com.dndmaster.adventure.application.runtime.TurnPlan;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import com.dndmaster.adventure.domain.runtime.narrative.WorldFact;
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
}

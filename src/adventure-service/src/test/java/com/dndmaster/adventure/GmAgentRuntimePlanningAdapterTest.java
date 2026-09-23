package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmAgentRuntimePlanningAdapter;
import com.dndmaster.adventure.application.runtime.GmFinalValidator;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimeAddedFactCandidate;
import com.dndmaster.adventure.application.runtime.RuntimePlanningRequest;
import com.dndmaster.adventure.application.runtime.SituationProposal;
import com.dndmaster.adventure.application.runtime.SituationUpdateProposal;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GmAgentRuntimePlanningAdapterTest {
    @Test
    void translates_only_explicit_change_and_reveal_delta_semantics() {
        RuntimePlan plan = plan(List.of("change:door", "reveal:secret"));
        var translated = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan, "provider", "model", "reasoning", List.of("change:door", "reveal:secret")),
                new GmFinalValidator()).plan(request());

        assertThat(translated.stateDelta().changedFactIds()).containsExactly("door");
        assertThat(translated.stateDelta().revealedFactIds()).containsExactly("secret");
    }

    @Test
    void rejects_unsupported_raw_delta_instead_of_broadening_it_into_a_reveal() {
        RuntimePlan plan = plan(List.of("door"));

        assertThatThrownBy(() -> new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan, "provider", "model", "reasoning", List.of("door")),
                new GmFinalValidator()).plan(request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported state delta");
    }

    @Test
    void carries_the_gms_situation_choice_to_the_runtime_turn() {
        RuntimePlan plan = plan(List.of());
        SituationProposal situation = new SituationProposal(
                SituationUpdateProposal.transition("cellar", "rats block the exit", "Giant Rats", "escape"),
                SituationProposal.Basis.RAG, "storybook:cellar-rats", true);

        var result = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan, "provider", "model", "reasoning", List.of(), List.of(), situation),
                new GmFinalValidator()).planWithOutcomes(request());

        assertThat(result.resolutionProposal().situationProposal()).isEqualTo(situation);
        assertThat(result.resolutionProposal().situationUpdate().threat()).isEqualTo("Giant Rats");
    }

    @Test
    void passes_established_runtime_facts_as_a_separate_authoritative_context() {
        AtomicReference<GmContextEnvelope> captured = new AtomicReference<>();
        RuntimePlanningRequest request = new RuntimePlanningRequest(
                AdventureId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, new AdventureContext("scene", null, null, null), null,
                "I ask the keeper about the reward", new EvidencePack(List.of(), List.of(), List.of()),
                List.of(), List.of(), "scenario model", null, "provider", "model", "reasoning", null, null,
                List.of("The keeper offered to open the gate."));

        new GmAgentRuntimePlanningAdapter(
                context -> {
                    captured.set(context);
                    return new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of());
                }, new GmFinalValidator()).plan(request);

        assertThat(captured.get().runtimeFacts())
                .containsExactly("The keeper offered to open the gate.");
    }

    @Test
    void turns_a_new_runtime_fact_into_a_pending_resolution_addition() {
        RuntimeAddedFactCandidate candidate = new RuntimeAddedFactCandidate("reward", "The keeper offers 30 gold pieces.");
        var result = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of(), List.of(), null,
                        List.of(candidate)), new GmFinalValidator()).planWithOutcomes(request());

        assertThat(result.resolutionProposal().runtimeAddedFacts()).singleElement()
                .satisfies(fact -> {
                    assertThat(fact.content()).isEqualTo("The keeper offers 30 gold pieces.");
                    assertThat(fact.establishedTurnId()).isNotNull();
                });
    }

    @Test
    void never_persists_a_candidate_that_tries_to_create_a_canonical_truth() {
        RuntimeAddedFactCandidate candidate = new RuntimeAddedFactCandidate("culprit", "The culprit is the keeper.");
        var result = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of(), List.of(), null,
                        List.of(candidate)), new GmFinalValidator()).planWithOutcomes(request());

        assertThat(result.resolutionProposal().runtimeAddedFacts()).isEmpty();
    }

    private static RuntimePlanningRequest request() {
        return new RuntimePlanningRequest(AdventureId.generate(), new OwnerPlayerId(UUID.randomUUID()),
                UUID.randomUUID(), 1, new AdventureContext("scene", null, null, null), null,
                "action", new EvidencePack(List.of(), List.of(), List.of()));
    }

    private static RuntimePlan plan(List<String> ignored) {
        return new RuntimePlan("scene", null, "judgment", "narration", null, List.of(), List.of());
    }
}

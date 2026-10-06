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
    void narration_only_path_uses_one_provider_request_without_materializing_runtime_changes() {
        var calls = new int[1];
        RuntimePlan result = new GmAgentRuntimePlanningAdapter(
                context -> {
                    calls[0]++;
                    return new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of("change:door"));
                }, new GmFinalValidator()).planNarration(request());

        assertThat(calls[0]).isEqualTo(1);
        assertThat(result.narration()).isEqualTo("narration");
    }

    @Test
    void confirmed_combat_narration_does_not_require_player_action_rulebook_citations() {
        RuntimePlan confirmedResult = new RuntimePlan("scene", null, "Attack hit", "The attack hits and deals damage.",
                null, List.of(), List.of());
        var adapter = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(confirmedResult, "provider", "model", "reasoning", List.of()),
                new GmFinalValidator());

        RuntimePlan narrated = adapter.planNarration(request("ATTACK: confirmed hit and damage", UUID.randomUUID()));

        assertThat(narrated).isEqualTo(confirmedResult);
    }

    @Test
    void does_not_infer_rule_citation_need_from_attack_wording() {
        RuntimePlan attack = new RuntimePlan("scene", null, "Attack hit", "The attack hits and deals damage.",
                null, List.of(), List.of());
        var adapter = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(attack, "provider", "model", "reasoning", List.of()),
                new GmFinalValidator());

        assertThat(adapter.plan(request("ATTACK: hit and damage", UUID.randomUUID()))).isEqualTo(attack);
    }

    @Test
    void narration_validation_checks_individual_private_facts_instead_of_the_whole_scenario_context() {
        RuntimePlanningRequest request = request().withHiddenFacts(List.of("The caretaker is the missing heir."));
        RuntimePlan leaking = new RuntimePlan("scene", null, "judgment", "The caretaker is the missing heir.",
                null, List.of(), List.of());

        assertThatThrownBy(() -> new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(leaking, "provider", "model", "reasoning", List.of()),
                new GmFinalValidator()).planNarration(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HIDDEN_DATA_IN_NARRATION");
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
    void preserves_the_agents_semantic_decision_for_a_proposed_fact() {
        RuntimeAddedFactCandidate candidate = new RuntimeAddedFactCandidate("culprit", "The culprit is the keeper.");
        var result = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of(), List.of(), null,
                        List.of(candidate)), new GmFinalValidator()).planWithOutcomes(request());

        assertThat(result.resolutionProposal().runtimeAddedFacts()).singleElement()
                .satisfies(fact -> assertThat(fact.content()).isEqualTo(candidate.content()));
    }

    @Test
    void does_not_infer_a_long_term_goal_from_a_keyword_in_the_player_action() {
        UUID turnId = UUID.randomUUID();
        RuntimePlanningRequest request = request("내 목표는 실종된 탐험가를 찾는 것이다.", turnId);
        var result = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of()),
                new GmFinalValidator()).planWithOutcomes(request);

        assertThat(result.resolutionProposal().runtimeAddedFacts()).isEmpty();
    }

    @Test
    void does_not_infer_a_relationship_objective_from_action_wording() {
        UUID turnId = UUID.randomUUID();
        String action = "사라진 탐험가를 찾고 마을 사람들과 신뢰를 쌓는다.";
        var result = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of()),
                new GmFinalValidator()).planWithOutcomes(
                        request(action, turnId));

        assertThat(result.resolutionProposal().runtimeAddedFacts()).isEmpty();
    }

    @Test
    void does_not_record_an_immediate_action_or_an_ambiguous_wish_as_a_long_term_goal() {
        for (String action : List.of("문을 열고 안으로 들어간다.", "저는 문을 열고 싶다.",
                "마을 사람들에게 신뢰를 물어보고 문을 연다.",
                "문을 열고 마을 사람들과 신뢰를 쌓는다.",
                "마을 사람들과 신뢰를 쌓고 문을 연다.",
                "사라진 탐험가를 찾고 마을 사람들과 신뢰를 쌓고 싶다.")) {
            var result = new GmAgentRuntimePlanningAdapter(
                    context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of()),
                    new GmFinalValidator()).planWithOutcomes(request(action, UUID.randomUUID()));

            assertThat(result.resolutionProposal().runtimeAddedFacts()).isEmpty();
        }
    }

    @Test
    void does_not_infer_a_durable_goal_from_a_place_or_goal_phrase() {
        UUID turnId = UUID.randomUUID();
        var result = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of()),
                new GmFinalValidator()).planWithOutcomes(
                        request("내 목표는 동굴에서 실종된 탐험가를 찾는 것이다.", turnId));

        assertThat(result.resolutionProposal().runtimeAddedFacts()).isEmpty();
    }

    @Test
    void does_not_record_an_ordinary_action_as_a_goal() {
        RuntimePlanningRequest request = request("문을 열고 안으로 들어간다.", UUID.randomUUID());
        var result = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of()),
                new GmFinalValidator()).planWithOutcomes(request);

        assertThat(result.resolutionProposal().runtimeAddedFacts()).isEmpty();
    }

    @Test
    void does_not_turn_a_declared_resource_or_combat_state_into_a_long_term_goal_fact() {
        RuntimePlanningRequest request = request("내 목표는 HP를 회복하고 전투 위치를 지키는 것이다.", UUID.randomUUID());
        var result = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of()),
                new GmFinalValidator()).planWithOutcomes(request);

        assertThat(result.resolutionProposal().runtimeAddedFacts()).isEmpty();
    }

    @Test
    void deduplicates_an_explicit_goal_against_existing_and_model_proposed_facts() {
        String goal = "목표: 실종된 탐험가를 찾는 것이다.";
        UUID turnId = UUID.randomUUID();
        RuntimePlanningRequest request = request("내 목표는 실종된 탐험가를 찾는 것이다.", turnId);
        RuntimeAddedFactCandidate sameGoal = new RuntimeAddedFactCandidate("goal", goal);
        var modelProposed = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of(),
                        List.of(), null, List.of(sameGoal)), new GmFinalValidator()).planWithOutcomes(request);
        assertThat(modelProposed.resolutionProposal().runtimeAddedFacts()).singleElement()
                .satisfies(fact -> assertThat(fact.content()).isEqualTo(goal));

        RuntimePlanningRequest alreadyRecorded = new RuntimePlanningRequest(request.adventureId(), request.ownerPlayerId(),
                request.sessionId(), turnId, request.scenarioPackageId(), request.bindingVersion(), request.currentContext(),
                request.activeSourceContext(), request.action(), request.evidencePack(), request.recentTurns(),
                request.characterSnapshots(), request.scenarioContext(), request.providerEndpointId(), request.provider(),
                request.model(), request.reasoning(), request.narrativeContext(), request.ruleSetId(), List.of(goal),
                request.factLookupResults(), request.currentSituation(), request.longTermFacts());
        var existing = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan(List.of()), "provider", "model", "reasoning", List.of()),
                new GmFinalValidator()).planWithOutcomes(alreadyRecorded);
        assertThat(existing.resolutionProposal().runtimeAddedFacts()).isEmpty();
    }

    private static RuntimePlanningRequest request() {
        return new RuntimePlanningRequest(AdventureId.generate(), new OwnerPlayerId(UUID.randomUUID()),
                UUID.randomUUID(), 1, new AdventureContext("scene", null, null, null), null,
                "action", new EvidencePack(List.of(), List.of(), List.of()));
    }

    private static RuntimePlanningRequest request(String action, UUID turnId) {
        return new RuntimePlanningRequest(AdventureId.generate(), new OwnerPlayerId(UUID.randomUUID()),
                UUID.randomUUID(), turnId, UUID.randomUUID(), 1,
                new AdventureContext("scene", null, null, null), null, action,
                new EvidencePack(List.of(), List.of(), List.of()), List.of(), List.of(), "", null,
                "provider", "model", "reasoning", null, null, List.of(), List.of(), "", List.of());
    }

    private static RuntimePlan plan(List<String> ignored) {
        return new RuntimePlan("scene", null, "judgment", "narration", null, List.of(), List.of());
    }
}

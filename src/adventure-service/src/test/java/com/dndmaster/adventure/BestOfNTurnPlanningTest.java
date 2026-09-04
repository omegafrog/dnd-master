package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.CandidateHardFilter;
import com.dndmaster.adventure.application.runtime.PlanCandidate;
import com.dndmaster.adventure.application.runtime.PlanCandidatePort;
import com.dndmaster.adventure.application.runtime.PlanJudgePort;
import com.dndmaster.adventure.application.runtime.PlanSelection;
import com.dndmaster.adventure.application.runtime.PlanSelectionPolicy;
import com.dndmaster.adventure.application.runtime.PlanningContext;
import com.dndmaster.adventure.application.runtime.TurnPlan;
import com.dndmaster.adventure.application.runtime.BestOfNPlanningCoordinator;
import com.dndmaster.adventure.application.runtime.BestOfNRuntimePlanningAdapter;
import com.dndmaster.adventure.application.runtime.RuntimePlanningRequest;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmAgentRuntimePlanningAdapter;
import com.dndmaster.adventure.application.runtime.GmFinalValidator;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;
import com.dndmaster.adventure.domain.runtime.narrative.WorldFact;
import com.dndmaster.adventure.infrastructure.integration.TurnPlanCandidateV1;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BestOfNTurnPlanningTest {
    @Test
    void candidate_count_defaults_to_three_and_simple_turns_use_one() {
        assertEquals(3, PlanningContext.candidateCount(false));
        assertEquals(1, PlanningContext.candidateCount(true));
        assertEquals(1, PlanningContext.boundedCandidateCount(0, false));
        assertEquals(5, PlanningContext.boundedCandidateCount(5, false));
    }

    @Test
    void hard_filter_rejects_secret_entity_agency_and_rule_violations_before_judge() {
        PlanningContext context = context();
        PlanCandidate secret = candidate("secret", List.of("hidden-key"), Set.of("door"), true, true);
        PlanCandidate unsupported = candidate("entity", List.of(), Set.of("dragon"), true, true);
        PlanCandidate unsafe = candidate("agency", List.of(), Set.of("door"), false, true);
        PlanCandidate invalidRule = candidate("rule", List.of(), Set.of("door"), true, false);
        PlanCandidate valid = candidate("valid", List.of("door-key"), Set.of("door"), true, true);

        var result = new CandidateHardFilter().filter(List.of(secret, unsupported, unsafe, invalidRule, valid), context);

        assertEquals(List.of("valid"), result.valid().stream().map(PlanCandidate::candidateId).toList());
        assertEquals(4, result.rejected().size());
        assertTrue(result.rejected().stream().allMatch(rejection -> !rejection.violations().isEmpty()));
    }

    @Test
    void selection_prioritizes_agency_and_continuity_then_prefers_simpler_plan_on_tie() {
        PlanCandidate simple = candidate("simple", List.of(), Set.of("door"), true, true, 1);
        PlanCandidate complex = candidate("complex", List.of(), Set.of("door"), true, true, 3);
        PlanSelection selection = new PlanSelectionPolicy().select(
                List.of(simple, complex), List.of(
                        PlanSelection.score("simple", 5, 5, 4, 5, 1, List.of("continuity")),
                        PlanSelection.score("complex", 5, 5, 4, 5, 3, List.of("continuity"))));

        assertEquals("simple", selection.selected().candidateId());
        assertEquals(2, selection.evaluations().size());
    }

    @Test
    void candidate_generator_receives_requested_n_and_no_prose_is_required() {
        var requested = new int[1];
        PlanCandidatePort generator = (context, count) -> { requested[0] = count; return List.of(candidate("one", List.of(), Set.of("door"), true, true)); };
        List<PlanCandidate> candidates = generator.generate(context(), 3);
        assertEquals(3, requested[0]);
        assertEquals("scene", candidates.getFirst().plan().scene());
    }

    @Test
    void coordinator_filters_before_judge_and_audits_only_the_selected_plan() {
        var judged = new int[1];
        var audits = new java.util.ArrayList<com.dndmaster.adventure.application.runtime.PlanSelectionAudit>();
        PlanCandidatePort generator = (context, count) -> List.of(
                candidate("invalid", List.of("hidden-key"), Set.of("door"), true, true),
                candidate("selected", List.of(), Set.of("door"), true, true));
        PlanJudgePort judge = (candidates, context) -> {
            judged[0] = candidates.size();
            return List.of(PlanSelection.score("selected", 5, 5, 5, 5, 1, List.of("agency")));
        };

        PlanSelection result = new BestOfNPlanningCoordinator(generator, judge, audits::add)
                .plan(context(), 3, false);

        assertEquals("selected", result.selected().candidateId());
        assertEquals(1, judged[0]);
        assertEquals("selected", audits.getFirst().selectedCandidateId());
        assertEquals(1, audits.getFirst().rejected().size());
    }

    @Test
    void judge_failure_uses_deterministic_first_valid_candidate_and_records_fallback() {
        var audits = new java.util.ArrayList<com.dndmaster.adventure.application.runtime.PlanSelectionAudit>();
        PlanSelection result = new BestOfNPlanningCoordinator(
                (context, count) -> List.of(candidate("first", List.of(), Set.of("door"), true, true), candidate("second", List.of(), Set.of("door"), true, true)),
                (candidates, context) -> { throw new IllegalStateException("provider unavailable"); }, audits::add)
                .plan(context(), 3, false);
        assertEquals("first", result.selected().candidateId());
        assertEquals("judge fallback", audits.getFirst().failure());
    }

    @Test
    void versioned_candidate_mapping_round_trips_without_prose() {
        PlanCandidate original = candidate("candidate-1", List.of("door-key"), Set.of("door"), true, true);
        TurnPlanCandidateV1 dto = TurnPlanCandidateV1.from(original);
        assertEquals(original, dto.toDomain());
        assertTrue(!dto.toString().contains("narration"));
    }

    @Test
    void runtime_adapter_passes_actor_scoped_fact_boundary_and_preserves_selection_identity() {
        var captured = new NarrativeContext[1];
        var audits = new java.util.ArrayList<com.dndmaster.adventure.application.runtime.PlanSelectionAudit>();
        var index = new int[1];
        RuntimePlan first = new RuntimePlan("scene", "npc", "judgment", "narration", null, List.of(), List.of(),
                "provider", "model", "reasoning", false, "branch-a");
        RuntimePlan second = new RuntimePlan("scene", "npc", "judgment", "narration", null, List.of(), List.of(),
                "provider", "model", "reasoning", false, "branch-b");
        var adapter = new BestOfNRuntimePlanningAdapter(request -> {
            captured[0] = request.narrativeContext();
            index[0]++;
            return index[0] == 1 ? first : second;
        }, 2, false, audits::add);
        UUID session = UUID.randomUUID();
        NarrativeContext actor = new NarrativeContext("player", "scene", 4, Set.of("known"),
                List.of(new WorldFact("known", "known fact", false), new WorldFact("secret", "secret fact", false)),
                java.util.Map.of(), List.of(), List.of(), List.of());
        RuntimePlan selected = adapter.plan(new RuntimePlanningRequest(new AdventureId(UUID.randomUUID()),
                new OwnerPlayerId(UUID.randomUUID()), session, UUID.randomUUID(), UUID.randomUUID(), 1,
                new AdventureContext("scene", "npc", "open", "judgment"), null, "open", new EvidencePack(List.of(), List.of(), List.of()),
                List.of(), List.of(), "stage", null, "provider", "model", "reasoning", actor));

        assertEquals("branch-a", selected.requestedSelectionId());
        assertEquals(Set.of("known"), captured[0].factsKnownBy());
        assertEquals("branch-a", audits.getFirst().selectedCandidateId());
    }

    @Test
    void runtime_adapter_derives_hard_filter_fields_and_judge_sees_only_safe_provider_plans() {
        var judged = new int[1];
        var audits = new java.util.ArrayList<com.dndmaster.adventure.application.runtime.PlanSelectionAudit>();
        List<RuntimePlan> plans = List.of(
                new RuntimePlan("dragon-lair", "dragon", "judgment", "narration", null, List.of(), List.of("UNSUPPORTED_ENTITY"), "p", "m", "r"),
                new RuntimePlan("scene", "npc", "player decides", "narration", null, List.of(), List.of("PLAYER_AGENCY_VIOLATION"), "p", "m", "r"),
                new RuntimePlan("scene", "npc", "continuity", "narration", null, List.of(), List.of("CONTINUITY_VIOLATION"), "p", "m", "r"),
                new RuntimePlan("scene", "npc", "rule", "narration", null, List.of(), List.of("RULE_VIOLATION"), "p", "m", "r"),
                new RuntimePlan("scene", "npc", "valid", "narration", null, List.of(), List.of(), "p", "m", "r"));
        var index = new int[1];
        var adapter = new BestOfNRuntimePlanningAdapter(request -> plans.get(index[0]++), 5, false, audits::add);
        RuntimePlanningRequest request = new RuntimePlanningRequest(new AdventureId(UUID.randomUUID()), new OwnerPlayerId(UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new AdventureContext("scene", "npc", "open", "judgment"), null, "open", new EvidencePack(List.of(), List.of(), List.of()),
                List.of(), List.of(), "stage", null, "provider", "model", "reasoning",
                new NarrativeContext("player", "scene", 4, Set.of("known"), List.of(new WorldFact("known", "known fact", false)),
                        java.util.Map.of(), List.of(), List.of(), List.of()));
        RuntimePlan selected = adapter.plan(request);

        assertEquals("runtime-4", selected.requestedSelectionId().isBlank() ? "runtime-4" : selected.requestedSelectionId());
        assertEquals(1, audits.getFirst().evaluations().size());
        assertEquals(4, audits.getFirst().rejected().size());
        assertEquals(Set.of("UNSUPPORTED_ENTITY", "PLAYER_AGENCY_VIOLATION", "CONTINUITY_VIOLATION", "RULE_VIOLATION"),
                audits.getFirst().rejected().stream().flatMap(rejection -> rejection.violations().stream())
                        .map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void decomposed_runtime_selection_keeps_valid_candidates_when_one_model_output_fails_validation() {
        RuntimeEvidence story = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.randomUUID()),
                1, "page:1", "A supported cellar scene.");
        RuntimeEvidence outsidePack = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.randomUUID()), 1, "page:2", "Outside evidence.");
        var calls = new int[1];
        GmAgentPort agent = context -> {
            calls[0]++;
            List<RuntimeEvidence> citations = calls[0] == 1 ? List.of(outsidePack) : List.of(story);
            return new GmPlanResult(new RuntimePlan("scene", "npc", "judgment", "narration", null, citations, List.of(), "p", "m", "r"),
                    "p", "m", "r", List.of());
        };
        var adapter = new BestOfNRuntimePlanningAdapter(new GmAgentRuntimePlanningAdapter(agent, new GmFinalValidator()), 3, false, audit -> { });
        RuntimePlan selected = adapter.plan(new RuntimePlanningRequest(AdventureId.generate(), new OwnerPlayerId(UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new AdventureContext("scene", "npc", "open", "judgment"), null, "open", new EvidencePack(List.of(story), List.of(), List.of()),
                List.of(), List.of(), "stage", null, "provider", "model", "reasoning", new NarrativeContext("player", "scene", 0,
                        Set.of(), List.of(), java.util.Map.of(), List.of(), List.of(), List.of())));

        assertEquals(List.of(story), selected.citedEvidence());
        assertEquals(3, calls[0]);
    }

    private static PlanningContext context() {
        return new PlanningContext("open door", "state-1", "stage-1", "player-visible", Set.of("door"), Set.of("door-key"), Set.of("hidden-key"));
    }

    private static PlanCandidate candidate(String id, List<String> facts, Set<String> entities, boolean agency, boolean rules) {
        return candidate(id, facts, entities, agency, rules, 1);
    }

    private static PlanCandidate candidate(String id, List<String> facts, Set<String> entities, boolean agency, boolean rules, int complexity) {
        return new PlanCandidate(id, new TurnPlan("scene", "npc", "judgment", facts, List.of()), "open door", "state-1", "stage-1", "player-visible", entities, agency, true, rules, complexity);
    }
}

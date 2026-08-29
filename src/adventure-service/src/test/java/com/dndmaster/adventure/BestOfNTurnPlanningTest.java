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
import com.dndmaster.adventure.infrastructure.integration.TurnPlanCandidateV1;
import java.util.List;
import java.util.Set;
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

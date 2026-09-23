package com.dndmaster.adventure.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EvidenceAcquisitionApplicationServiceTest {
    @Test
    void keeps_selected_evidence_pinned_across_at_most_two_additional_searches() {
        EvidenceCandidate first = candidate("first");
        EvidenceCandidate second = candidate("second");
        EvidenceCandidate third = candidate("third");
        AtomicInteger searches = new AtomicInteger();
        EvidenceAcquisitionApplicationService service = new EvidenceAcquisitionApplicationService(
                request -> switch (searches.getAndIncrement()) {
                    case 0 -> List.of(first);
                    case 1 -> {
                        assertEquals("more context", request.query());
                        assertEquals(1, request.additionalSearches());
                        yield List.of(second);
                    }
                    default -> {
                        assertEquals("more context", request.query());
                        assertEquals(2, request.additionalSearches());
                        yield List.of(third);
                    }
                }, request -> request.candidates().stream().map(EvidenceCandidate::id).toList(),
                request -> request.additionalSearches() < 2
                        ? SufficiencyDecision.insufficient(List.of(first.id()), Map.of(first.id(), "present"), "more context")
                        : SufficiencyDecision.sufficient(List.of(first.id(), third.id()), Map.of(first.id(), "present", third.id(), "needed")));

        EvidenceAcquisitionResult result = service.acquire(new EvidenceAcquisitionRequest("PLAYER_ACTION", "action", List.of()));

        assertEquals(3, searches.get());
        assertEquals(List.of(first.id(), third.id()), result.decision().selectedEvidenceIds());
    }

    @Test
    void retries_a_transient_reranker_failure_once_without_nested_retry() {
        EvidenceCandidate candidate = candidate("only");
        AtomicInteger reranks = new AtomicInteger();
        EvidenceAcquisitionApplicationService service = new EvidenceAcquisitionApplicationService(
                request -> List.of(candidate), request -> {
                    if (reranks.getAndIncrement() == 0) throw new EvidenceAcquisitionTransientException("provider unavailable");
                    return List.of(candidate.id());
                }, request -> SufficiencyDecision.sufficient(List.of(candidate.id()), Map.of(candidate.id(), "needed")));

        service.acquire(new EvidenceAcquisitionRequest("RULE_GUIDANCE", "question", List.of()));

        assertEquals(2, reranks.get());
    }

    @Test
    void rejects_model_selection_outside_reranked_candidates() {
        EvidenceCandidate candidate = candidate("only");
        EvidenceAcquisitionApplicationService service = new EvidenceAcquisitionApplicationService(
                request -> List.of(candidate), request -> List.of(candidate.id()),
                request -> {
                    UUID outside = UUID.randomUUID();
                    return SufficiencyDecision.sufficient(List.of(outside), Map.of(outside, "not supplied"));
                });

        assertThrows(EvidenceAcquisitionContractException.class,
                () -> service.acquire(new EvidenceAcquisitionRequest("RULE_GUIDANCE", "question", List.of())));
    }

    @Test
    void rejects_judge_reasons_that_do_not_match_the_selected_evidence() {
        EvidenceCandidate candidate = candidate("only");
        EvidenceAcquisitionApplicationService service = new EvidenceAcquisitionApplicationService(
                request -> List.of(candidate), request -> List.of(candidate.id()),
                request -> SufficiencyDecision.sufficient(List.of(candidate.id()), Map.of(UUID.randomUUID(), "not selected")));

        assertThrows(IllegalArgumentException.class,
                () -> service.acquire(new EvidenceAcquisitionRequest("RULE_GUIDANCE", "question", List.of())));
    }

    @Test
    void four_fixed_policies_keep_their_document_scope_and_final_insufficiency() {
        List<EvidenceSufficiencyPolicy> policies = List.of(new PlayerActionEvidenceSufficiencyPolicy(),
                new ScenarioPreparationEvidenceSufficiencyPolicy(), new OpeningSceneEvidenceSufficiencyPolicy(),
                new RuleGuidanceEvidenceSufficiencyPolicy());
        assertEquals(List.of("PLAYER_ACTION", "SCENARIO_PREPARATION", "OPENING_SCENE", "RULE_GUIDANCE"),
                policies.stream().map(EvidenceSufficiencyPolicy::policyId).toList());
        assertEquals(EvidenceSufficiencyPolicy.FinalInsufficiency.LIMITED_PLAYER_ACTION,
                policies.getFirst().finalInsufficiency());
        assertEquals(Set.of("RULEBOOK"), policies.getLast().documentTypes());
    }

    @Test
    void rerank_request_keeps_first_candidate_when_the_same_id_is_repeated() {
        EvidenceCandidate first = candidate("same");
        EvidenceCandidate duplicate = new EvidenceCandidate(first.id(), "doc", "STORYBOOK", "p:duplicate", "duplicate");

        EvidenceRerankRequest request = new EvidenceRerankRequest(
                "RULE_GUIDANCE", "question", List.of(first, duplicate));

        assertEquals(List.of(first), request.candidates());
    }

    private static EvidenceCandidate candidate(String label) {
        return new EvidenceCandidate(UUID.nameUUIDFromBytes(label.getBytes()), "doc", "STORYBOOK", "p:" + label, label);
    }
}

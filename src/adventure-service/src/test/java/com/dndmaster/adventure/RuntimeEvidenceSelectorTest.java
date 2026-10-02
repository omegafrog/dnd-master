package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.adventure.application.runtime.*;
import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.*;
import org.junit.jupiter.api.Test;

class RuntimeEvidenceSelectorTest {
    private final UUID adventureId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID packageId = UUID.randomUUID();
    private final UUID storybookId = UUID.randomUUID();
    private final UUID rulebookId = UUID.randomUUID();

    @Test
    void searches_both_rules_sources_with_the_full_per_source_limit_and_keeps_all_results() {
        List<RuntimeEvidence> story = evidence(RuntimeEvidenceType.STORYBOOK, storybookId, 7);
        List<RuntimeEvidence> rules = evidence(RuntimeEvidenceType.RULEBOOK, rulebookId, 7);
        RuntimeEvidenceSelection selection = new RuntimeEvidenceSelector(request ->
                request.evidenceType() == RuntimeEvidenceType.STORYBOOK ? story : rules)
                .select(request("MIXED", 8), List.of());

        assertEquals(14, selection.pack().storybook().size() + selection.pack().rulebook().size());
        assertEquals(7, selection.pack().storybook().size());
        assertEquals(7, selection.pack().rulebook().size());
        assertEquals(14, selection.metrics().selectedCount());
        assertEquals(7, selection.metrics().selectedByType().get(RuntimeEvidenceType.STORYBOOK));
    }

    @Test
    void searches_both_sources_for_every_action_and_preserves_provenance_and_key() {
        RuntimeEvidence item = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(storybookId), 12, "page:4:block:2", "지하실에는 거대 쥐 두 마리가 있습니다.", "rat-fact");
        List<RuntimeEvidenceSearchRequest> requests = new ArrayList<>();
        RuntimeEvidenceSelection selection = new RuntimeEvidenceSelector(request -> {
            requests.add(request);
            return List.of(item);
        }).select(request("EXPLORE", 8), List.of());

        assertEquals(2, requests.size());
        assertEquals(List.of(RuntimeEvidenceType.STORYBOOK, RuntimeEvidenceType.RULEBOOK),
                requests.stream().map(RuntimeEvidenceSearchRequest::evidenceType).toList());
        assertEquals(List.of("open the cellar", "open the cellar"), requests.stream()
                .map(RuntimeEvidenceSearchRequest::action).toList());
        assertEquals(12, selection.pack().storybook().getFirst().extractionVersion());
        assertEquals("rat-fact", selection.pack().storybook().getFirst().citationKey());
        assertEquals("page:4:block:2", selection.pack().storybook().getFirst().locator());
    }

    @Test
    void does_not_apply_an_eight_item_total_cap() {
        List<RuntimeEvidence> story = evidence(RuntimeEvidenceType.STORYBOOK, storybookId, 12);
        List<RuntimeEvidence> rules = evidence(RuntimeEvidenceType.RULEBOOK, rulebookId, 12);
        RuntimeEvidenceSelection selection = new RuntimeEvidenceSelector(request ->
                request.evidenceType() == RuntimeEvidenceType.STORYBOOK ? story : rules)
                .select(request("MIXED", 12), List.of());

        assertEquals(24, selection.pack().totalEvidenceCount());
        assertEquals(24, selection.metrics().selectedCount());
        assertEquals(24, selection.pack().rules().size());
    }

    @Test
    void permits_a_turn_without_a_match_in_either_rules_source() {
        RuntimeEvidenceSelection selection = new RuntimeEvidenceSelector(request -> List.of())
                .select(request("PLAYER_ACTION", 8), List.of());
        assertTrue(selection.pack().all().isEmpty());
    }

    private RuntimeEvidenceSearchRequest request(String intent, int limit) {
        return new RuntimeEvidenceSearchRequest(new AdventureId(adventureId), new OwnerPlayerId(ownerId),
                new SessionId(sessionId), packageId, List.of(storybookId, rulebookId), null,
                "open the cellar", RuntimeEvidenceType.STORYBOOK, limit,
                Map.of(storybookId, 12L, rulebookId, 4L), "stage-2", intent);
    }

    private static List<RuntimeEvidence> evidence(RuntimeEvidenceType type, UUID id, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new RuntimeEvidence(type, new KnowledgeDocumentId(id), 1, "page:" + i,
                        type + " evidence " + i, type + "-" + i))
                .toList();
    }
}

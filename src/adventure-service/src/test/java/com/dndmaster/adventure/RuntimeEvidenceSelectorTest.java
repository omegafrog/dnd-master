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
    void selects_storybook_first_and_never_exceeds_eight_items() {
        List<RuntimeEvidence> story = evidence(RuntimeEvidenceType.STORYBOOK, storybookId, 7);
        List<RuntimeEvidence> rules = evidence(RuntimeEvidenceType.RULEBOOK, rulebookId, 7);
        RuntimeEvidenceSelection selection = new RuntimeEvidenceSelector(request ->
                request.evidenceType() == RuntimeEvidenceType.STORYBOOK ? story : rules)
                .select(request("MIXED", 8), List.of());

        assertEquals(8, selection.pack().storybook().size() + selection.pack().rulebook().size());
        assertEquals(7, selection.pack().storybook().size());
        assertEquals(1, selection.pack().rulebook().size());
        assertEquals(8, selection.metrics().selectedCount());
        assertEquals(7, selection.metrics().selectedByType().get(RuntimeEvidenceType.STORYBOOK));
    }

    @Test
    void does_not_query_rulebook_for_story_intent_and_preserves_provenance_and_key() {
        RuntimeEvidence item = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(storybookId), 12, "page:4:block:2", "지하실에는 거대 쥐 두 마리가 있습니다.", "rat-fact");
        List<RuntimeEvidenceSearchRequest> requests = new ArrayList<>();
        RuntimeEvidenceSelection selection = new RuntimeEvidenceSelector(request -> {
            requests.add(request);
            return List.of(item);
        }).select(request("EXPLORE", 8), List.of());

        assertEquals(1, requests.size());
        assertEquals(RuntimeEvidenceType.STORYBOOK, requests.getFirst().evidenceType());
        assertEquals(12, selection.pack().storybook().getFirst().extractionVersion());
        assertEquals("rat-fact", selection.pack().storybook().getFirst().citationKey());
        assertEquals("page:4:block:2", selection.pack().storybook().getFirst().locator());
    }

    @Test
    void rejects_missing_storybook_as_structured_selection_failure() {
        RuntimeEvidenceSelectionException failure = assertThrows(RuntimeEvidenceSelectionException.class,
                () -> new RuntimeEvidenceSelector(request -> List.of()).select(request("RULE", 8), List.of()));
        assertEquals("MISSING_STORYBOOK", failure.violation().code());
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

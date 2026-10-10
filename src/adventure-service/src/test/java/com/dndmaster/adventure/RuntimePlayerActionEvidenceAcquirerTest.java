package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimePlayerActionEvidenceAcquirer;
import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceCandidateSearchPort;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimePlayerActionEvidenceAcquirerTest {
    @Test
    void makes_one_mixed_search_without_reranker_or_sufficiency_calls() {
        UUID ownerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID storybookId = UUID.randomUUID();
        UUID rulebookId = UUID.randomUUID();
        EvidenceSearchScope scope = new EvidenceSearchScope(ownerId, sessionId, packageId, "scene:cellar", "PLAYER_ACTION",
                List.of(new EvidenceSearchScope.Document(storybookId, 7, "STORYBOOK"),
                        new EvidenceSearchScope.Document(rulebookId, 3, "RULEBOOK")), List.of("page:2"));
        AtomicInteger calls = new AtomicInteger();
        EvidenceCandidateSearchPort search = request -> {
            calls.incrementAndGet();
            assertEquals(scope, request.acquisitionRequest().searchScope());
            assertEquals(0, request.acquisitionRequest().additionalSearchLimit());
            assertEquals(0, request.additionalSearches());
            assertTrue(request.query().contains("플레이어 행동: open the cellar"));
            assertTrue(request.query().contains("현재 상황: 문 뒤에서 이상한 소리가 들린다."));
            assertTrue(request.query().contains("지각"));
            return List.of(
                    new EvidenceCandidate(UUID.randomUUID(), storybookId.toString(), "STORYBOOK", "page:2", "A cellar door is locked."),
                    new EvidenceCandidate(UUID.randomUUID(), rulebookId.toString(), "RULEBOOK", "page:18", "Lockpicks require a check."));
        };

        List<RuntimeEvidence> result = new RuntimePlayerActionEvidenceAcquirer(search)
                .acquire(scope, "open the cellar", "문 뒤에서 이상한 소리가 들린다.");

        assertEquals(1, calls.get());
        assertEquals(List.of(RuntimeEvidenceType.STORYBOOK, RuntimeEvidenceType.RULEBOOK),
                result.stream().map(RuntimeEvidence::evidenceType).toList());
        assertEquals(7, result.getFirst().extractionVersion());
        assertEquals(3, result.getLast().extractionVersion());
    }

    @Test
    void excludes_known_evidence_but_treats_a_new_extraction_version_as_new() {
        UUID documentId = UUID.randomUUID();
        UUID knownChunk = UUID.randomUUID();
        UUID updatedChunk = UUID.randomUUID();
        EvidenceSearchScope scope = new EvidenceSearchScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "scene:cellar", "PLAYER_ACTION", List.of(new EvidenceSearchScope.Document(documentId, 2, "STORYBOOK")),
                List.of(), List.of("IMMUTABLE_DOCUMENT:STORYBOOK:" + documentId + ":1:page:1"));
        EvidenceCandidateSearchPort search = request -> List.of(
                new EvidenceCandidate(knownChunk, documentId.toString(), "STORYBOOK", "page:1", "Current version result"),
                new EvidenceCandidate(updatedChunk, documentId.toString(), "STORYBOOK", "page:1", "Duplicate current version result"));

        List<RuntimeEvidence> result = new RuntimePlayerActionEvidenceAcquirer(search).acquire(scope, "open cellar");

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().extractionVersion());
        assertEquals("Current version result", result.getFirst().excerpt());
    }
}

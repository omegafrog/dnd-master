package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimePlayerActionEvidenceAcquirer;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionApplicationService;
import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import com.dndmaster.adventure.evidence.SufficiencyDecision;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimePlayerActionEvidenceAcquirerTest {
    @Test
    void uses_the_confirmed_session_scope_and_only_the_judges_selected_candidates() {
        UUID ownerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID storybookId = UUID.randomUUID();
        UUID rulebookId = UUID.randomUUID();
        UUID storyChunkId = UUID.randomUUID();
        UUID ruleChunkId = UUID.randomUUID();
        EvidenceSearchScope scope = new EvidenceSearchScope(ownerId, sessionId, packageId, "scene:cellar", "MIXED",
                List.of(new EvidenceSearchScope.Document(storybookId, 7, "STORYBOOK"),
                        new EvidenceSearchScope.Document(rulebookId, 3, "RULEBOOK")), List.of("page:2"));
        List<EvidenceCandidate> candidates = List.of(
                new EvidenceCandidate(storyChunkId, storybookId.toString(), "STORYBOOK", "page:2", "A cellar door is locked."),
                new EvidenceCandidate(ruleChunkId, rulebookId.toString(), "RULEBOOK", "page:18", "Lockpicks require a check."));
        EvidenceAcquisitionApplicationService service = new EvidenceAcquisitionApplicationService(
                request -> {
                    assertEquals(scope, request.acquisitionRequest().searchScope());
                    assertEquals("open the cellar", request.query());
                    return candidates;
                }, request -> List.of(ruleChunkId, storyChunkId),
                request -> SufficiencyDecision.sufficient(List.of(ruleChunkId), Map.of(ruleChunkId, "The rule resolves the action.")));

        List<RuntimeEvidence> result = new RuntimePlayerActionEvidenceAcquirer(service).acquire(scope, "open the cellar");

        assertEquals(1, result.size());
        assertEquals(RuntimeEvidenceType.RULEBOOK, result.getFirst().evidenceType());
        assertEquals(rulebookId, result.getFirst().knowledgeDocumentId().value());
        assertEquals(3, result.getFirst().extractionVersion());
        assertEquals("page:18", result.getFirst().locator());
    }
}

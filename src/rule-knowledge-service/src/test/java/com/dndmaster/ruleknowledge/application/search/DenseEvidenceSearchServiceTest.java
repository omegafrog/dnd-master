package com.dndmaster.ruleknowledge.application.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DenseEvidenceSearchServiceTest {
    private static final OwnerPlayerId OWNER = new OwnerPlayerId(UUID.randomUUID());
    private static final AuthorizedDocumentScope SCOPE = new AuthorizedDocumentScope(
            new KnowledgeDocumentId(UUID.randomUUID()), 1, DocumentType.STORYBOOK);

    @Test
    void returnsDenseCandidatesWithoutFusionMetadata() {
        var service = new DenseEvidenceSearchService(request -> List.of(candidate("found")));

        var result = service.search(request());

        assertEquals(1, result.candidates().size());
        assertEquals("found", result.candidates().getFirst().excerpt());
        assertEquals(0.82, result.candidates().getFirst().denseSimilarity());
    }

    @Test
    void retriesDenseBatchOnceAndPreservesRequestOrder() {
        AtomicInteger calls = new AtomicInteger();
        DenseEvidenceCandidateSearchPort dense = new DenseEvidenceCandidateSearchPort() {
            @Override public List<EvidenceCandidate> search(EvidenceSearchRequest request) { return List.of(); }
            @Override public List<List<EvidenceCandidate>> searchBatch(List<EvidenceSearchRequest> requests) {
                if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary failure");
                return requests.stream().map(request -> List.of(candidate(request.query()))).toList();
            }
        };

        var results = new DenseEvidenceSearchService(dense).searchBatch(List.of(request(), request("second")));

        assertEquals(2, calls.get());
        assertEquals(List.of("query", "second"), results.stream()
                .map(result -> result.candidates().getFirst().excerpt()).toList());
    }

    @Test
    void rejectsDenseCandidatesOutsideTheAuthorizedDocumentScope() {
        var foreign = new AuthorizedDocumentScope(new KnowledgeDocumentId(UUID.randomUUID()), 1, DocumentType.RULEBOOK);
        var dense = (DenseEvidenceCandidateSearchPort) request -> List.of(new EvidenceCandidate(foreign.documentId(),
                ChunkId.fromStableValue("foreign"), 1, DocumentType.RULEBOOK, "page:2", "foreign",
                new SourceProvenance(2, List.of(), List.of(), null, "page:2"), 1, 0.9));

        assertThrows(EvidenceSearchUnavailableException.class, () -> new DenseEvidenceSearchService(dense).search(request()));
    }

    private static EvidenceCandidate candidate(String excerpt) {
        return new EvidenceCandidate(SCOPE.documentId(), ChunkId.fromStableValue(excerpt), 1, DocumentType.STORYBOOK,
                "page:1", excerpt, new SourceProvenance(1, List.of(), List.of(), null, "page:1"), 1, 0.82);
    }

    private static EvidenceSearchRequest request() { return request("query"); }
    private static EvidenceSearchRequest request(String query) {
        return new EvidenceSearchRequest(OWNER, UUID.randomUUID(), UUID.randomUUID(), "opening", "STORY",
                List.of(SCOPE), List.of("page:1"), query, 30);
    }
}

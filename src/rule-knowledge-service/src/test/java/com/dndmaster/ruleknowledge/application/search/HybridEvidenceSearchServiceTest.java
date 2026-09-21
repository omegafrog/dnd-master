package com.dndmaster.ruleknowledge.application.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HybridEvidenceSearchServiceTest {
    private static final OwnerPlayerId OWNER = new OwnerPlayerId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final AuthorizedDocumentScope SCOPE = new AuthorizedDocumentScope(
            new KnowledgeDocumentId(UUID.fromString("00000000-0000-0000-0000-000000000002")), 7, DocumentType.RULEBOOK);

    @Test
    void searchesBothRetrieversWithTheAuthorizedScopeAndFusesTheirThirtyCandidates() {
        List<SearchInvocation> denseInvocations = new ArrayList<>();
        List<SearchInvocation> bm25Invocations = new ArrayList<>();
        DenseEvidenceCandidateSearchPort dense = (owner, scope, query, limit) -> {
            denseInvocations.add(new SearchInvocation(owner, scope, query, limit));
            return List.of(candidate("dense-first"), candidate("shared"));
        };
        Bm25EvidenceCandidateSearchPort bm25 = (owner, scope, query, limit) -> {
            bm25Invocations.add(new SearchInvocation(owner, scope, query, limit));
            return List.of(candidate("shared"), candidate("bm25-second"));
        };

        EvidenceSearchResult result = new HybridEvidenceSearchService(dense, bm25, new RrfFusionPolicy())
                .search(new EvidenceSearchRequest(OWNER, List.of(SCOPE), "find the armor class", 30, 30));

        assertEquals(List.of(new SearchInvocation(OWNER, List.of(SCOPE), "find the armor class", 30)), denseInvocations);
        assertEquals(List.of(new SearchInvocation(OWNER, List.of(SCOPE), "find the armor class", 30)), bm25Invocations);
        assertEquals(List.of("shared", "dense-first", "bm25-second"), result.candidates().stream()
                .map(candidate -> candidate.excerpt()).toList());
        assertEquals(2, result.candidates().get(0).denseRank());
        assertEquals(1, result.candidates().get(0).bm25Rank());
        assertEquals(SCOPE.documentId(), result.candidates().get(0).documentId());
        assertEquals("page:1", result.candidates().get(0).provenance().originalLocator());
    }

    @Test
    void retriesAFailedRetrieverOnceAndNeverReturnsDenseOnlyCandidates() {
        AtomicInteger denseCalls = new AtomicInteger();
        AtomicInteger bm25Calls = new AtomicInteger();
        DenseEvidenceCandidateSearchPort dense = (owner, scope, query, limit) -> {
            denseCalls.incrementAndGet();
            return List.of(candidate("dense"));
        };
        Bm25EvidenceCandidateSearchPort bm25 = (owner, scope, query, limit) -> {
            bm25Calls.incrementAndGet();
            throw new IllegalStateException("database unavailable");
        };

        HybridEvidenceSearchService service = new HybridEvidenceSearchService(dense, bm25, new RrfFusionPolicy());

        EvidenceSearchUnavailableException error = assertThrows(EvidenceSearchUnavailableException.class,
                () -> service.search(new EvidenceSearchRequest(OWNER, List.of(SCOPE), "find the armor class", 30, 30)));

        assertEquals("Evidence candidate search is unavailable", error.getMessage());
        assertEquals(1, denseCalls.get());
        assertEquals(2, bm25Calls.get());
    }

    private static EvidenceCandidate candidate(String stableId) {
        return new EvidenceCandidate(SCOPE.documentId(), ChunkId.fromStableValue(stableId), SCOPE.extractionVersion(),
                SCOPE.documentType(), "page:1", stableId, new SourceProvenance(1, List.of("Combat"), List.of(), null, "page:1"),
                null, null, 0.0);
    }

    private record SearchInvocation(OwnerPlayerId owner, List<AuthorizedDocumentScope> scope, String query, int limit) { }
}

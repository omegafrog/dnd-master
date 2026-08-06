package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.application.search.ContextExpansionPort;
import com.dndmaster.ruleknowledge.application.search.CandidateWindowContextExpansion;
import com.dndmaster.ruleknowledge.application.search.EvidencePack;
import com.dndmaster.ruleknowledge.application.search.EvidencePackAssembler;
import com.dndmaster.ruleknowledge.application.search.HybridRetrievalCandidate;
import com.dndmaster.ruleknowledge.application.search.Reranker;
import com.dndmaster.ruleknowledge.application.search.RetrievalScope;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RerankingAndContextExpansionTest {
    @Test
    void reranksDeterministicallyAndKeepsDiverseDocuments() {
        UUID owner = UUID.randomUUID();
        KnowledgeDocumentId first = KnowledgeDocumentId.generate();
        KnowledgeDocumentId second = KnowledgeDocumentId.generate();
        RetrievalScope scope = RetrievalScope.builder(owner).sessionId("s").packageId("p").stage("start")
                .document(first, DocumentType.RULEBOOK, 1).document(second, DocumentType.STORYBOOK, 2).build();
        List<HybridRetrievalCandidate> candidates = List.of(
                candidate(owner, first, DocumentType.RULEBOOK, 1, "p1", "rule", .4),
                candidate(owner, first, DocumentType.RULEBOOK, 1, "p2", "rule duplicate", .3),
                candidate(owner, second, DocumentType.STORYBOOK, 2, "p1", "story", .8));

        EvidencePack pack = new EvidencePackAssembler(Reranker.deterministic(), ContextExpansionPort.identity(), 3, 1)
                .assemble("rule", candidates, scope);

        assertEquals(List.of("story", "rule"), pack.entries().stream().map(entry -> entry.candidate().excerpt()).toList());
        assertTrue(pack.entries().stream().allMatch(entry -> entry.provenance().rerankScore() >= 0));
    }

    @Test
    void expansionCannotEscapeScopeAndFailureFallsBackToSeed() {
        UUID owner = UUID.randomUUID();
        KnowledgeDocumentId document = KnowledgeDocumentId.generate();
        RetrievalScope scope = RetrievalScope.builder(owner).sessionId("s").packageId("p").stage("start")
                .document(document, DocumentType.RULEBOOK, 1).build();
        HybridRetrievalCandidate seed = candidate(owner, document, DocumentType.RULEBOOK, 1, "p1", "seed", .9);
        HybridRetrievalCandidate foreign = candidate(owner, document, DocumentType.RULEBOOK, 2, "p2", "foreign version", .99);

        EvidencePack pack = new EvidencePackAssembler((query, values) -> { throw new RuntimeException("timeout"); },
                (value, ignored, radius) -> List.of(foreign), 3, 2).assemble("rule", List.of(seed), scope);

        assertEquals(1, pack.entries().size());
        assertEquals("seed", pack.entries().getFirst().candidate().excerpt());
        assertTrue(pack.degraded());
    }

    @Test
    void expandsOnlyAdjacentSameVersionChunksAndReportsLatency() {
        UUID owner = UUID.randomUUID();
        KnowledgeDocumentId document = KnowledgeDocumentId.generate();
        RetrievalScope scope = RetrievalScope.builder(owner).sessionId("s").packageId("p").stage("start")
                .document(document, DocumentType.RULEBOOK, 1).build();
        List<HybridRetrievalCandidate> window = List.of(
                candidate(owner, document, DocumentType.RULEBOOK, 1, "p0", "before", .5),
                candidate(owner, document, DocumentType.RULEBOOK, 1, "p1", "seed", .9),
                candidate(owner, document, DocumentType.RULEBOOK, 1, "p2", "after", .4),
                candidate(owner, document, DocumentType.RULEBOOK, 2, "p1", "old version", .99));
        long[] elapsed = { -1L };

        EvidencePack pack = new EvidencePackAssembler(Reranker.deterministic(),
                new CandidateWindowContextExpansion(window), 3, 2,
                (candidates, entries, degraded, nanos) -> elapsed[0] = nanos)
                .assemble("seed", window, scope);

        assertEquals(List.of("seed", "before", "after"), pack.entries().getFirst().context().stream()
                .map(HybridRetrievalCandidate::excerpt).toList());
        assertTrue(elapsed[0] >= 0 && elapsed[0] < 100_000_000L);
    }

    private static HybridRetrievalCandidate candidate(UUID owner, KnowledgeDocumentId document, DocumentType type,
            long version, String locator, String excerpt, double score) {
        return new HybridRetrievalCandidate(owner, document, type, version, locator, excerpt,
                score, score, UUID.nameUUIDFromBytes(locator.getBytes()), "s", "p", "start", "PLAYER_VISIBLE")
                .withScore(score);
    }
}

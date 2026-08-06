package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.application.search.HybridRetrievalCandidate;
import com.dndmaster.ruleknowledge.application.search.HybridRetrievalService;
import com.dndmaster.ruleknowledge.application.search.DecomposedIntent;
import com.dndmaster.ruleknowledge.application.search.DecomposedRetrievalService;
import com.dndmaster.ruleknowledge.application.search.QueryDecomposer;
import com.dndmaster.ruleknowledge.application.search.QueryIntentPart;
import com.dndmaster.ruleknowledge.application.search.RetrievalScope;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HybridRetrievalTest {
    @Test
    void decomposesFixedActionDeterministicallyIntoRelevantIntents() {
        String action = "I attack the goblin with my sword and spend a spell slot to open the sealed door";

        List<QueryIntentPart> first = QueryDecomposer.decompose(action);
        List<QueryIntentPart> second = QueryDecomposer.decompose(action);

        assertEquals(first, second);
        assertEquals(Set.of("RULES", "COMBAT", "RESOURCES", "SCENE", "NPC"),
                first.stream().map(part -> part.intent().name()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void mergesNormalizesAndDeduplicatesOnlyScopedCandidates() {
        UUID owner = UUID.randomUUID();
        KnowledgeDocumentId document = KnowledgeDocumentId.generate();
        RetrievalScope scope = RetrievalScope.builder(owner)
                .sessionId("session-1")
                .packageId("package-1")
                .document(document, DocumentType.STORYBOOK, 3)
                .visibleToPlayer()
                .stage("crypt")
                .activeLocator("page-2")
                .build();
        HybridRetrievalService service = new HybridRetrievalService((query, ignored, limit) -> List.of(
                candidate(owner, document, 3, "page-2", 0.2, 0.9, "session-1", "package-1", "crypt", "PLAYER_VISIBLE"),
                candidate(owner, document, 3, "page-9", 0.1, 0.8, "session-1", "package-1", "crypt", "GM_ONLY")),
                (query, ignored, limit) -> List.of(
                candidate(owner, document, 3, "page-2", 0.9, 0.1, "session-1", "package-1", "crypt", "PLAYER_VISIBLE"),
                candidate(owner, document, 3, "page-3", 0.8, 0.7, "other-session", "package-1", "crypt", "PLAYER_VISIBLE")));

        List<HybridRetrievalCandidate> results = service.search("open the door", scope, 5);

        assertEquals(1, results.size());
        assertEquals("page-2", results.getFirst().locator());
        assertTrue(results.getFirst().score() > 0d && results.getFirst().score() <= 1d);
    }

    @Test
    void returnsExplicitDegradedResultWhenBothRetrieversFail() {
        HybridRetrievalService service = new HybridRetrievalService((query, scope, limit) -> { throw new RuntimeException("dense down"); },
                (query, scope, limit) -> { throw new RuntimeException("keyword down"); });

        var result = service.retrieve("what happens", RetrievalScope.builder(UUID.randomUUID()).sessionId("s").packageId("p").stage("stage").build(), 3);

        assertTrue(result.degraded());
        assertEquals("RETRIEVAL_UNAVAILABLE", result.status());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void buildsSeparateEvidencePackForEachIntent() {
        HybridRetrievalService service = new HybridRetrievalService((query, scope, limit) -> List.of(),
                (query, scope, limit) -> List.of());

        var pack = new DecomposedRetrievalService(service).retrieve(
                "attack the goblin and open the door", RetrievalScope.builder(UUID.randomUUID()).sessionId("s").packageId("p").stage("stage").build(), 2);

        assertTrue(pack.byIntent().containsKey(DecomposedIntent.COMBAT));
        assertTrue(pack.byIntent().containsKey(DecomposedIntent.SCENE));
        assertEquals(0, pack.byIntent().get(DecomposedIntent.COMBAT).candidates().size());
    }

    private static HybridRetrievalCandidate candidate(UUID owner, KnowledgeDocumentId document, long version, String locator,
            double dense, double keyword, String session, String pack, String stage, String visibility) {
        return new HybridRetrievalCandidate(owner, document, DocumentType.STORYBOOK, version, locator, "excerpt", dense,
                keyword, UUID.nameUUIDFromBytes(locator.getBytes(java.nio.charset.StandardCharsets.UTF_8)), session, pack, stage, visibility);
    }
}

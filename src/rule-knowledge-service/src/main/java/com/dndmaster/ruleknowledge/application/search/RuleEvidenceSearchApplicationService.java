package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;

import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.UUID;

public final class RuleEvidenceSearchApplicationService {
    private final RuleEvidenceSearchPort searchRepository;
    private final EmbeddingPort embeddingPort;
    private final String embeddingModel;
    private final int embeddingDimension;
    private final HybridRetrievalService hybridRetrieval;
    private final DecomposedRetrievalService decomposedRetrieval;

    public RuleEvidenceSearchApplicationService(
            RuleEvidenceSearchPort searchRepository,
            EmbeddingPort embeddingPort,
            String embeddingModel,
            int embeddingDimension) {
        this.searchRepository = Objects.requireNonNull(searchRepository, "searchRepository must not be null");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort must not be null");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
        if (embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be positive");
        }
        this.embeddingDimension = embeddingDimension;
        this.hybridRetrieval = new HybridRetrievalService(
                (text, scope, limit) -> retrieveCandidates(text, scope, limit, false),
                (text, scope, limit) -> retrieveCandidates(text, scope, limit, true));
        this.decomposedRetrieval = new DecomposedRetrievalService(hybridRetrieval);
    }

    public List<RuleEvidenceResult> search(SearchRuleEvidenceQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        RetrievalScope.Builder scopeBuilder = RetrievalScope.builder(query.owner().value())
                .sessionId("legacy-rule-search")
                .packageId("legacy-rule-search")
                .stage("current");
        query.selectedRulebooks().forEach(id -> scopeBuilder.document(com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId.fromRulebookId(id),
                com.dndmaster.ruleknowledge.domain.rulebook.DocumentType.RULEBOOK, 1));
        RetrievalScope scope = scopeBuilder.build();
        int candidateLimit = Math.max(20, query.limit() * 4);
        DecomposedEvidencePack pack = decomposedRetrieval.retrieve(query.situation(), scope, candidateLimit);
        if (pack.degraded() && pack.byIntent().values().stream().allMatch(result -> result.candidates().isEmpty())) {
            throw new IllegalStateException("rule retrieval degraded: no scoped evidence available");
        }
        List<HybridRetrievalCandidate> candidates = pack.byIntent().values().stream()
                .flatMap(result -> result.candidates().stream())
                .toList();
        EvidencePack evidencePack = new EvidencePackAssembler(Reranker.deterministic(),
                new CandidateWindowContextExpansion(candidates), query.limit(), 2, new LoggingEvidencePackObserver())
                .assemble(query.situation(), candidates, scope);
        return evidencePack.entries().stream()
                .map(entry -> entry.candidate())
                .map(hit -> new RuleEvidenceResult(
                        hit.documentId().asRulebookId(),
                        new ChunkId(hit.chunkId()),
                        hit.locator(),
                        hit.excerpt(), hit.score(), null, null))
                .toList();
    }

    private List<HybridRetrievalCandidate> retrieveCandidates(String text, RetrievalScope scope, int limit,
            boolean keyword) {
        if (keyword && searchRepository instanceof RuleEvidenceKeywordSearchPort keywordPort) {
            return keywordPort.searchKeyword(new com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId(scope.ownerId()),
                            scope.documents().keySet().stream().map(com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId::asRulebookId).toList(), text, limit).stream()
                    .map(hit -> toCandidate(hit, scope, 0d, Math.max(0d, 1d - hit.distance()))).toList();
        }
        RulebookChunk input = new RulebookChunk(RulebookId.generate(), new ChunkId(UUID.randomUUID()), 0,
                new ExtractedContentRange(0, text.length()), text, null, null);
        float[] embedding = embeddingPort.embed(List.of(input), embeddingModel, embeddingDimension).getFirst().vector();
        QueryIntent intent = QueryIntent.RULE;
        return searchRepository.search(new com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId(scope.ownerId()),
                        scope.documents().keySet().stream().map(com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId::asRulebookId).toList(),
                        embedding, intent, limit).stream()
                .map(hit -> {
                    double dense = Math.max(0d, 1d - hit.distance());
                    double keywordScore = keyword ? lexicalScore(text, hit.content()) : 0d;
                    long version = scope.extractionVersions().get(com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId.fromRulebookId(hit.rulebookId()));
                    return toCandidate(hit, scope, dense, keywordScore);
                }).toList();
    }

    private static HybridRetrievalCandidate toCandidate(RuleSearchHit hit, RetrievalScope scope, double dense, double keyword) {
        long version = scope.extractionVersions().get(com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId.fromRulebookId(hit.rulebookId()));
        return new HybridRetrievalCandidate(scope.ownerId(), com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId.fromRulebookId(hit.rulebookId()),
                com.dndmaster.ruleknowledge.domain.rulebook.DocumentType.RULEBOOK, version, hit.locator(), hit.content(), dense, keyword,
                hit.chunkId().value(), scope.sessionId(), scope.packageId(), scope.currentStage(), "PLAYER_VISIBLE");
    }

    private static double lexicalScore(String query, String content) {
        java.util.Set<String> terms = new java.util.HashSet<>(java.util.Arrays.asList(query.toLowerCase().split("\\W+")));
        long matches = terms.stream().filter(term -> !term.isBlank() && content.toLowerCase().contains(term)).count();
        return terms.isEmpty() ? 0d : (double) matches / terms.size();
    }
}

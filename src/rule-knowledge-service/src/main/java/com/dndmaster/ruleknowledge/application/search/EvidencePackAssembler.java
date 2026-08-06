package com.dndmaster.ruleknowledge.application.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;

public final class EvidencePackAssembler {
    private final Reranker reranker;
    private final ContextExpansionPort expansion;
    private final int maxEntries;
    private final int maxPerDocument;
    private final EvidencePackObserver observer;

    public EvidencePackAssembler(Reranker reranker, ContextExpansionPort expansion, int maxEntries,
            int maxPerDocument) {
        this(reranker, expansion, maxEntries, maxPerDocument, EvidencePackObserver.noop());
    }

    public EvidencePackAssembler(Reranker reranker, ContextExpansionPort expansion, int maxEntries,
            int maxPerDocument, EvidencePackObserver observer) {
        this.reranker = Objects.requireNonNull(reranker);
        this.expansion = Objects.requireNonNull(expansion);
        this.observer = Objects.requireNonNull(observer);
        if (maxEntries <= 0 || maxPerDocument <= 0) throw new IllegalArgumentException("limits must be positive");
        this.maxEntries = maxEntries;
        this.maxPerDocument = maxPerDocument;
    }

    public EvidencePack assemble(String query, List<HybridRetrievalCandidate> candidates, RetrievalScope scope) {
        long started = System.nanoTime();
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        Objects.requireNonNull(candidates);
        Objects.requireNonNull(scope);
        List<HybridRetrievalCandidate> scoped = candidates.stream().filter(scope::accepts).toList();
        boolean degraded = false;
        List<HybridRetrievalCandidate> ranked;
        try {
            ranked = List.copyOf(reranker.rerank(query, scoped));
        } catch (RuntimeException failure) {
            ranked = scoped.stream().sorted(java.util.Comparator.comparingDouble(HybridRetrievalCandidate::score)
                    .reversed().thenComparing(HybridRetrievalCandidate::key)).toList();
            degraded = true;
        }
        List<EvidencePackEntry> entries = new ArrayList<>();
        Set<KnowledgeDocumentId> documents = new HashSet<>();
        Set<String> contextKeys = new HashSet<>();
        List<HybridRetrievalCandidate> selected = selectDiverse(ranked, scope);
        for (HybridRetrievalCandidate candidate : selected) {
            if (!scope.accepts(candidate) || documents.stream().filter(candidate.documentId()::equals).count() >= maxPerDocument) continue;
            List<HybridRetrievalCandidate> context;
            try {
                context = expansion.expand(candidate, scope, 1).stream().filter(scope::accepts).distinct()
                        .filter(item -> item.documentId().equals(candidate.documentId()))
                        .filter(item -> item.key().equals(candidate.key()) || !contextKeys.contains(item.key()))
                        .limit(maxEntries).toList();
            } catch (RuntimeException failure) {
                context = List.of(candidate);
                degraded = true;
            }
            if (context.isEmpty()) context = List.of(candidate);
            contextKeys.addAll(context.stream().map(HybridRetrievalCandidate::key).toList());
            entries.add(new EvidencePackEntry(candidate, context,
                    new EvidenceProvenance(candidate.key(), candidate.score(), context.stream().map(HybridRetrievalCandidate::key).toList())));
            documents.add(candidate.documentId());
            if (entries.size() == maxEntries) break;
        }
        EvidencePack result = new EvidencePack(entries, degraded);
        observer.onAssembled(candidates.size(), entries.size(), degraded, System.nanoTime() - started);
        return result;
    }

    private List<HybridRetrievalCandidate> selectDiverse(List<HybridRetrievalCandidate> ranked, RetrievalScope scope) {
        List<HybridRetrievalCandidate> result = new ArrayList<>();
        Set<DocumentType> types = new HashSet<>();
        for (HybridRetrievalCandidate candidate : ranked) {
            if (scope.accepts(candidate) && types.add(candidate.documentType())) result.add(candidate);
        }
        for (HybridRetrievalCandidate candidate : ranked) {
            if (scope.accepts(candidate) && !result.contains(candidate)) result.add(candidate);
        }
        return result;
    }
}

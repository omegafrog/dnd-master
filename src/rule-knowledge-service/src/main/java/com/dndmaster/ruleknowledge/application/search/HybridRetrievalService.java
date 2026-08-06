package com.dndmaster.ruleknowledge.application.search;

import java.util.*;
import java.util.stream.Stream;

public final class HybridRetrievalService {
    private final RetrievalCandidateSource dense;
    private final RetrievalCandidateSource keyword;
    public HybridRetrievalService(RetrievalCandidateSource dense, RetrievalCandidateSource keyword) {
        this.dense = Objects.requireNonNull(dense); this.keyword = Objects.requireNonNull(keyword);
    }
    public List<HybridRetrievalCandidate> search(String query, RetrievalScope scope, int limit) {
        return retrieve(query, scope, limit).candidates();
    }
    public HybridRetrievalResult retrieve(String query, RetrievalScope scope, int limit) {
        if (query == null || query.isBlank() || limit <= 0) throw new IllegalArgumentException("invalid retrieval request");
        Objects.requireNonNull(scope);
        List<HybridRetrievalCandidate> denseHits = call(dense, query, scope, limit * 2);
        List<HybridRetrievalCandidate> keywordHits = call(keyword, query, scope, limit * 2);
        boolean failed = denseHits == null || keywordHits == null;
        List<HybridRetrievalCandidate> safeDense = denseHits == null ? List.of() : denseHits;
        List<HybridRetrievalCandidate> safeKeyword = keywordHits == null ? List.of() : keywordHits;
        Map<String, HybridRetrievalCandidate> merged = new LinkedHashMap<>();
        for (HybridRetrievalCandidate candidate : Stream.concat(safeDense.stream(), safeKeyword.stream())
                .filter(scope::accepts).toList()) {
            merged.merge(candidate.key(), candidate, HybridRetrievalService::merge);
        }
        List<HybridRetrievalCandidate> ranked = normalize(merged.values().stream().toList()).stream()
                .sorted(Comparator.comparingDouble(HybridRetrievalCandidate::score).reversed()
                        .thenComparing(HybridRetrievalCandidate::key)).limit(limit).toList();
        boolean empty = ranked.isEmpty();
        return new HybridRetrievalResult(ranked, failed || empty,
                failed && empty ? "RETRIEVAL_UNAVAILABLE" : failed ? "RETRIEVAL_DEGRADED" : empty ? "NO_EVIDENCE" : "OK");
    }
    private static List<HybridRetrievalCandidate> call(RetrievalCandidateSource source, String q, RetrievalScope s, int limit) {
        try { return Optional.ofNullable(source.search(q, s, limit)).orElse(List.of()); } catch (RuntimeException ex) { return null; }
    }
    private static HybridRetrievalCandidate merge(HybridRetrievalCandidate a, HybridRetrievalCandidate b) {
        return new HybridRetrievalCandidate(a.ownerId(), a.documentId(), a.documentType(), a.extractionVersion(),
                a.locator(), a.excerpt(), Math.max(a.denseScore(), b.denseScore()),
                Math.max(a.keywordScore(), b.keywordScore()), a.chunkId(), a.sessionId(), a.packageId(), a.stage(), a.visibility());
    }
    private static List<HybridRetrievalCandidate> normalize(List<HybridRetrievalCandidate> values) {
        if (values.isEmpty()) return values;
        double maxDense = values.stream().mapToDouble(HybridRetrievalCandidate::denseScore).max().orElse(1);
        double minDense = values.stream().mapToDouble(HybridRetrievalCandidate::denseScore).min().orElse(0);
        double maxKeyword = values.stream().mapToDouble(HybridRetrievalCandidate::keywordScore).max().orElse(1);
        double minKeyword = values.stream().mapToDouble(HybridRetrievalCandidate::keywordScore).min().orElse(0);
        return values.stream().map(c -> c.withScore(.65 * normalize(c.denseScore(), minDense, maxDense)
                + .35 * normalize(c.keywordScore(), minKeyword, maxKeyword))).toList();
    }
    private static double normalize(double value, double min, double max) { return max == min ? 1d : (value - min) / (max - min); }
}

package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Collects both required scoped candidate lists and fuses them deterministically with RRF. */
public final class HybridEvidenceSearchService {
    private final DenseEvidenceCandidateSearchPort denseSearch;
    private final Bm25EvidenceCandidateSearchPort bm25Search;
    private final RrfFusionPolicy rrfFusionPolicy;

    public HybridEvidenceSearchService(
            DenseEvidenceCandidateSearchPort denseSearch,
            Bm25EvidenceCandidateSearchPort bm25Search,
            RrfFusionPolicy rrfFusionPolicy) {
        this.denseSearch = Objects.requireNonNull(denseSearch, "dense search must not be null");
        this.bm25Search = Objects.requireNonNull(bm25Search, "BM25 search must not be null");
        this.rrfFusionPolicy = Objects.requireNonNull(rrfFusionPolicy, "RRF fusion policy must not be null");
    }

    public EvidenceSearchResult search(EvidenceSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<EvidenceCandidate> denseCandidates = retrieve(() -> denseSearch.search(
                request.ownerPlayerId(), request.scope(), request.query(), request.denseLimit()));
        List<EvidenceCandidate> bm25Candidates = retrieve(() -> bm25Search.search(
                request.ownerPlayerId(), request.scope(), request.query(), request.bm25Limit()));

        Map<String, EvidenceCandidate> candidatesByStableId = new LinkedHashMap<>();
        addCandidates(candidatesByStableId, denseCandidates);
        addCandidates(candidatesByStableId, bm25Candidates);
        Map<String, RrfFusionPolicy.RankedChunk> ranksByStableId = new LinkedHashMap<>();
        rrfFusionPolicy.fuse(stableIds(denseCandidates), stableIds(bm25Candidates))
                .forEach(rank -> ranksByStableId.put(rank.stableChunkId(), rank));

        return new EvidenceSearchResult(ranksByStableId.values().stream()
                .map(rank -> withRanks(candidatesByStableId.get(rank.stableChunkId()), rank))
                .toList());
    }

    private static List<EvidenceCandidate> retrieve(Supplier<List<EvidenceCandidate>> search) {
        RuntimeException firstFailure;
        try {
            return validatedCandidates(search.get());
        } catch (RuntimeException exception) {
            firstFailure = exception;
        }
        try {
            return validatedCandidates(search.get());
        } catch (RuntimeException retryFailure) {
            retryFailure.addSuppressed(firstFailure);
            throw new EvidenceSearchUnavailableException(retryFailure);
        }
    }

    private static List<EvidenceCandidate> validatedCandidates(List<EvidenceCandidate> candidates) {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "retriever candidates must not be null"));
        if (candidates.size() > RrfFusionPolicy.MAX_RESULTS_PER_RETRIEVER || candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("retriever returned invalid candidate list");
        }
        return candidates;
    }

    private static void addCandidates(Map<String, EvidenceCandidate> target, List<EvidenceCandidate> candidates) {
        candidates.forEach(candidate -> target.putIfAbsent(stableId(candidate), candidate));
    }

    private static List<String> stableIds(List<EvidenceCandidate> candidates) {
        return candidates.stream().map(HybridEvidenceSearchService::stableId).toList();
    }

    private static String stableId(EvidenceCandidate candidate) {
        return candidate.chunkId().value().toString();
    }

    private static EvidenceCandidate withRanks(EvidenceCandidate candidate, RrfFusionPolicy.RankedChunk rank) {
        return new EvidenceCandidate(candidate.documentId(), candidate.chunkId(), candidate.extractionVersion(),
                candidate.documentType(), candidate.locator(), candidate.excerpt(), candidate.provenance(),
                rank.denseRank(), rank.bm25Rank(), rank.rrfScore());
    }
}

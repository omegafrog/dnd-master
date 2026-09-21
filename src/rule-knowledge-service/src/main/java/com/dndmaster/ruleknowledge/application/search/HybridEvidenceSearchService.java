package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        RetrievalCandidates retrievalCandidates = retrieveTogether(request);
        List<EvidenceCandidate> denseCandidates = retrievalCandidates.denseCandidates();
        List<EvidenceCandidate> bm25Candidates = retrievalCandidates.bm25Candidates();

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

    private RetrievalCandidates retrieveTogether(EvidenceSearchRequest request) {
        RuntimeException firstFailure;
        try {
            return retrieveOnce(request);
        } catch (RuntimeException exception) {
            firstFailure = exception;
        }
        try {
            return retrieveOnce(request);
        } catch (RuntimeException retryFailure) {
            retryFailure.addSuppressed(firstFailure);
            throw new EvidenceSearchUnavailableException(retryFailure);
        }
    }

    private RetrievalCandidates retrieveOnce(EvidenceSearchRequest request) {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<List<EvidenceCandidate>> dense = executor.submit(() -> denseSearch.search(request));
            Future<List<EvidenceCandidate>> bm25 = executor.submit(() -> bm25Search.search(request));
            List<EvidenceCandidate> denseCandidates = await(dense);
            List<EvidenceCandidate> bm25Candidates = await(bm25);
            return new RetrievalCandidates(
                    validatedCandidates(request, denseCandidates), validatedCandidates(request, bm25Candidates));
        }
    }

    private static List<EvidenceCandidate> await(Future<List<EvidenceCandidate>> retrieval) {
        try {
            return retrieval.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("candidate retrieval was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("candidate retrieval failed", cause);
        }
    }

    private static List<EvidenceCandidate> validatedCandidates(EvidenceSearchRequest request, List<EvidenceCandidate> candidates) {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "retriever candidates must not be null"));
        if (candidates.size() > RrfFusionPolicy.MAX_RESULTS_PER_RETRIEVER || candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("retriever returned invalid candidate list");
        }
        if (candidates.stream().anyMatch(candidate -> !request.scope().contains(new AuthorizedDocumentScope(
                candidate.documentId(), candidate.extractionVersion(), candidate.documentType())))) {
            throw new IllegalStateException("retriever returned a candidate outside the authorized document scope");
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

    private record RetrievalCandidates(List<EvidenceCandidate> denseCandidates, List<EvidenceCandidate> bm25Candidates) {}
}

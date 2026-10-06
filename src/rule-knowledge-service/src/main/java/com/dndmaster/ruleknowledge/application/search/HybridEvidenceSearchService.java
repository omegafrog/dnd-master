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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HybridEvidenceSearchService.class);
    private final DenseEvidenceCandidateSearchPort denseSearch;
    private final Bm25EvidenceCandidateSearchPort bm25Search;
    private final RrfFusionPolicy rrfFusionPolicy;
    private final boolean bm25Enabled;

    public HybridEvidenceSearchService(
            DenseEvidenceCandidateSearchPort denseSearch,
            Bm25EvidenceCandidateSearchPort bm25Search,
            RrfFusionPolicy rrfFusionPolicy) {
        this(denseSearch, bm25Search, rrfFusionPolicy, true);
    }

    public HybridEvidenceSearchService(
            DenseEvidenceCandidateSearchPort denseSearch,
            Bm25EvidenceCandidateSearchPort bm25Search,
            RrfFusionPolicy rrfFusionPolicy,
            boolean bm25Enabled) {
        this.denseSearch = Objects.requireNonNull(denseSearch, "dense search must not be null");
        this.bm25Search = Objects.requireNonNull(bm25Search, "BM25 search must not be null");
        this.rrfFusionPolicy = Objects.requireNonNull(rrfFusionPolicy, "RRF fusion policy must not be null");
        this.bm25Enabled = bm25Enabled;
    }

    public EvidenceSearchResult search(EvidenceSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        RetrievalCandidates retrievalCandidates = retrieveTogether(request);
        List<EvidenceCandidate> denseCandidates = retrievalCandidates.denseCandidates();
        List<EvidenceCandidate> bm25Candidates = retrievalCandidates.bm25Candidates();
        devLog("dev_evidence_search stage=retrieval ownerId={} scenarioPackageId={} stageKey={} queryFingerprint={} scope={} denseCount={} bm25Count={} denseChunks={} bm25Chunks={}",
                request.ownerPlayerId().value(), request.scenarioPackageId(), request.stageKey(), fingerprint(request.query()),
                scopeKeys(request), denseCandidates.size(), bm25Candidates.size(), chunkIds(denseCandidates), chunkIds(bm25Candidates));

        Map<String, EvidenceCandidate> candidatesByStableId = new LinkedHashMap<>();
        addCandidates(candidatesByStableId, denseCandidates);
        addCandidates(candidatesByStableId, bm25Candidates);
        Map<String, RrfFusionPolicy.RankedChunk> ranksByStableId = new LinkedHashMap<>();
        rrfFusionPolicy.fuse(stableIds(denseCandidates), stableIds(bm25Candidates))
                .forEach(rank -> ranksByStableId.put(rank.stableChunkId(), rank));

        EvidenceSearchResult result = new EvidenceSearchResult(ranksByStableId.values().stream()
                .map(rank -> withRanks(candidatesByStableId.get(rank.stableChunkId()), rank))
                .toList());
        devLog("dev_evidence_search stage=rank_fusion ownerId={} scenarioPackageId={} stageKey={} queryFingerprint={} fusedCount={} fused={}",
                request.ownerPlayerId().value(), request.scenarioPackageId(), request.stageKey(), fingerprint(request.query()), result.candidates().size(),
                result.candidates().stream().map(candidate -> candidate.chunkId().value() + ":dense="
                        + candidate.denseRank() + ":bm25=" + candidate.bm25Rank() + ":rrf=" + candidate.rrfScore()).toList());
        return result;
    }

    private RetrievalCandidates retrieveTogether(EvidenceSearchRequest request) {
        RuntimeException firstFailure;
        try {
            return retrieveOnce(request);
        } catch (RuntimeException exception) {
            firstFailure = exception;
            devLog("dev_evidence_search stage=retrieval_attempt attempt=1 outcome=failed ownerId={} bundleId={} failureClass={}",
                    request.ownerPlayerId().value(), request.scenarioPackageId(), exception.getClass().getName());
        }
        try {
            RetrievalCandidates retry = retrieveOnce(request);
            devLog("dev_evidence_search stage=retrieval_attempt attempt=2 outcome=success ownerId={} bundleId={} denseCount={} bm25Count={}",
                    request.ownerPlayerId().value(), request.scenarioPackageId(), retry.denseCandidates().size(), retry.bm25Candidates().size());
            return retry;
        } catch (RuntimeException retryFailure) {
            retryFailure.addSuppressed(firstFailure);
            devLog("dev_evidence_search stage=retrieval_attempt attempt=2 outcome=failed ownerId={} bundleId={} failureClass={}",
                    request.ownerPlayerId().value(), request.scenarioPackageId(), retryFailure.getClass().getName());
            throw new EvidenceSearchUnavailableException(retryFailure);
        }
    }

    private RetrievalCandidates retrieveOnce(EvidenceSearchRequest request) {
        try (ExecutorService executor = Executors.newFixedThreadPool(bm25Enabled ? 2 : 1)) {
            Future<List<EvidenceCandidate>> dense = executor.submit(() -> searchDense(request));
            Future<List<EvidenceCandidate>> bm25 = bm25Enabled
                    ? executor.submit(() -> searchBm25(request))
                    : null;
            List<EvidenceCandidate> denseCandidates = await(dense);
            List<EvidenceCandidate> bm25Candidates = bm25Enabled ? await(bm25) : List.of();
            return new RetrievalCandidates(
                    validatedCandidates(request, denseCandidates), validatedCandidates(request, bm25Candidates));
        }
    }

    private List<EvidenceCandidate> searchDense(EvidenceSearchRequest request) {
        try {
            return denseSearch.search(request);
        } catch (RuntimeException failure) {
            devLog("dev_evidence_search stage=dense outcome=failed ownerId={} bundleId={} failureClass={} failure={}",
                    request.ownerPlayerId().value(), request.scenarioPackageId(), failure.getClass().getName(), failure.getMessage());
            throw failure;
        }
    }

    private List<EvidenceCandidate> searchBm25(EvidenceSearchRequest request) {
        try {
            return bm25Search.search(request);
        } catch (RuntimeException failure) {
            devLog("dev_evidence_search stage=bm25 outcome=failed ownerId={} bundleId={} failureClass={} failure={}",
                    request.ownerPlayerId().value(), request.scenarioPackageId(), failure.getClass().getName(), failure.getMessage());
            throw failure;
        }
    }

    private static List<String> chunkIds(List<EvidenceCandidate> candidates) {
        return candidates.stream().map(candidate -> candidate.chunkId().value().toString()).toList();
    }

    private static List<String> scopeKeys(EvidenceSearchRequest request) {
        return request.scope().stream().map(scope -> scope.documentId().value() + ":" + scope.extractionVersion()
                + ":" + scope.documentType()).toList();
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void devLog(String pattern, Object... arguments) {
        if (Boolean.parseBoolean(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")))) LOGGER.info(pattern, arguments);
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
        if (candidates.stream().anyMatch(candidate -> request.scope().stream().noneMatch(scope ->
                scope.documentId().equals(candidate.documentId())
                        && scope.extractionVersion() == candidate.extractionVersion()
                        && scope.documentType() == candidate.documentType()))) {
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

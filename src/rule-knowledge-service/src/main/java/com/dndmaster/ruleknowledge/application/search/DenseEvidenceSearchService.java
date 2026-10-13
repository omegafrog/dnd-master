package com.dndmaster.ruleknowledge.application.search;

import java.util.List;
import java.util.Objects;

/** Performs scoped dense retrieval and retries one transient failure without returning partial results. */
public final class DenseEvidenceSearchService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DenseEvidenceSearchService.class);
    private final DenseEvidenceCandidateSearchPort denseSearch;

    public DenseEvidenceSearchService(DenseEvidenceCandidateSearchPort denseSearch) {
        this.denseSearch = Objects.requireNonNull(denseSearch, "dense search must not be null");
    }

    public EvidenceSearchResult search(EvidenceSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new EvidenceSearchResult(retrieve(() -> denseSearch.search(request), request));
    }

    public List<EvidenceSearchResult> searchBatch(List<EvidenceSearchRequest> requests) {
        requests = List.copyOf(Objects.requireNonNull(requests, "requests must not be null"));
        if (requests.isEmpty()) return List.of();
        List<EvidenceSearchRequest> batch = requests;
        RuntimeException firstFailure;
        try {
            return searchBatchOnce(batch);
        } catch (RuntimeException failure) {
            firstFailure = failure;
            devLog("dev_evidence_search stage=dense_batch attempt=1 outcome=failed requestCount={} failureClass={}",
                    batch.size(), failure.getClass().getName());
        }
        try {
            List<EvidenceSearchResult> results = searchBatchOnce(batch);
            devLog("dev_evidence_search stage=dense_batch attempt=2 outcome=success requestCount={} resultCount={}",
                    batch.size(), results.size());
            return results;
        } catch (RuntimeException retryFailure) {
            retryFailure.addSuppressed(firstFailure);
            devLog("dev_evidence_search stage=dense_batch attempt=2 outcome=failed requestCount={} failureClass={}",
                    batch.size(), retryFailure.getClass().getName());
            throw new EvidenceSearchUnavailableException(retryFailure);
        }
    }

    private List<EvidenceSearchResult> searchBatchOnce(List<EvidenceSearchRequest> requests) {
        List<List<EvidenceCandidate>> retrieved = denseSearch.searchBatch(requests);
        if (retrieved.size() != requests.size()) throw new IllegalStateException("dense batch size mismatch");
        java.util.ArrayList<EvidenceSearchResult> results = new java.util.ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            EvidenceSearchRequest request = requests.get(index);
            List<EvidenceCandidate> candidates = validate(request, retrieved.get(index));
            results.add(new EvidenceSearchResult(candidates));
        }
        return List.copyOf(results);
    }

    private List<EvidenceCandidate> retrieve(java.util.function.Supplier<List<EvidenceCandidate>> retrieval,
            EvidenceSearchRequest request) {
        RuntimeException firstFailure;
        try {
            return validate(request, retrieval.get());
        } catch (RuntimeException failure) {
            firstFailure = failure;
            devLog("dev_evidence_search stage=dense attempt=1 outcome=failed scopeCount={} failureClass={}",
                    request.scope().size(), failure.getClass().getName());
        }
        try {
            List<EvidenceCandidate> result = validate(request, retrieval.get());
            devLog("dev_evidence_search stage=dense attempt=2 outcome=success scopeCount={} resultCount={}",
                    request.scope().size(), result.size());
            return result;
        } catch (RuntimeException retryFailure) {
            retryFailure.addSuppressed(firstFailure);
            devLog("dev_evidence_search stage=dense attempt=2 outcome=failed scopeCount={} failureClass={}",
                    request.scope().size(), retryFailure.getClass().getName());
            throw new EvidenceSearchUnavailableException(retryFailure);
        }
    }

    private static List<EvidenceCandidate> validate(EvidenceSearchRequest request, List<EvidenceCandidate> candidates) {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "retriever candidates must not be null"));
        if (candidates.size() > request.denseLimit() || candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("dense retriever returned invalid candidate list");
        }
        if (candidates.stream().anyMatch(candidate -> request.scope().stream().noneMatch(scope ->
                scope.documentId().equals(candidate.documentId())
                        && scope.extractionVersion() == candidate.extractionVersion()
                        && scope.documentType() == candidate.documentType()))) {
            throw new IllegalStateException("retriever returned a candidate outside the authorized document scope");
        }
        return candidates;
    }

    private static void devLog(String pattern, Object... arguments) {
        if (Boolean.parseBoolean(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")))) LOGGER.info(pattern, arguments);
    }
}

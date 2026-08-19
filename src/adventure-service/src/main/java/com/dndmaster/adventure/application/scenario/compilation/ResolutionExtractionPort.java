package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;

public interface ResolutionExtractionPort {
    List<ResolutionCandidate> extract(ResolutionExtractionRequest request);

    default ResolutionCandidate retryCandidate(CandidateRetryRequest request) {
        return extract(new ResolutionExtractionRequest(request.operationId(), request.excerpts(), request.schemaVersion(), request.promptVersion()))
                .stream().findFirst().orElse(request.failedCandidate());
    }

    record ResolutionExtractionRequest(
            String operationId,
            List<SourceExcerpt> excerpts,
            String schemaVersion,
            String promptVersion) {}

    record SourceExcerpt(KnowledgeDocumentId documentId, long extractionVersion, String locator, String text) {}
    record CandidateRetryRequest(String operationId, ResolutionCandidate failedCandidate, List<SourceExcerpt> excerpts, String schemaVersion, String promptVersion, int attempt, List<String> diagnostics) { }
}

package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;

public interface ResolutionExtractionPort {
    List<ResolutionCandidate> extract(ResolutionExtractionRequest request);

    default ResolutionCandidate retryCandidate(CandidateRetryRequest request) {
        return extract(new ResolutionExtractionRequest(request.operationId(), request.excerpts(), request.schemaVersion(), request.promptVersion(),
                        request.failedCandidate(), request.attempt(), request.diagnostics()))
                .stream().findFirst().orElse(request.failedCandidate());
    }

    record ResolutionExtractionRequest(
            String operationId,
            List<SourceExcerpt> excerpts,
            String schemaVersion,
            String promptVersion,
            ResolutionCandidate failedCandidate,
            int attempt,
            List<String> diagnostics) {
        public ResolutionExtractionRequest(String operationId, List<SourceExcerpt> excerpts,
                String schemaVersion, String promptVersion) {
            this(operationId, excerpts, schemaVersion, promptVersion, null, 0, List.of());
        }
        public ResolutionExtractionRequest {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
            if (attempt < 0) throw new IllegalArgumentException("attempt must not be negative");
        }
    }

    record SourceExcerpt(String documentType, KnowledgeDocumentId documentId, long extractionVersion, String locator, String text) {
        public SourceExcerpt(KnowledgeDocumentId documentId, long extractionVersion, String locator, String text) {
            this("STORYBOOK", documentId, extractionVersion, locator, text);
        }

        public SourceExcerpt {
            if (documentType == null || documentType.isBlank()) {
                throw new IllegalArgumentException("source excerpt document type must not be blank");
            }
        }
    }
    record CandidateRetryRequest(String operationId, ResolutionCandidate failedCandidate, List<SourceExcerpt> excerpts, String schemaVersion, String promptVersion, int attempt, List<String> diagnostics) { }
}

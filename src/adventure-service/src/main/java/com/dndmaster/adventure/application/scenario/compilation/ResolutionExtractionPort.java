package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.PublishedEvidenceProvenance;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface ResolutionExtractionPort {
    List<ResolutionCandidate> extract(ResolutionExtractionRequest request);

    default ResolutionCandidate retryCandidate(CandidateRetryRequest request) {
        return extract(new ResolutionExtractionRequest(request.soloPlayerId(), request.operationId(), request.excerpts(), request.schemaVersion(), request.promptVersion(),
                        request.failedCandidate(), request.attempt(), request.diagnostics()))
                .stream().findFirst().orElse(request.failedCandidate());
    }

    record ResolutionExtractionRequest(
            UUID soloPlayerId,
            String operationId,
            List<SourceExcerpt> excerpts,
            String schemaVersion,
            String promptVersion,
            ResolutionCandidate failedCandidate,
            int attempt,
            List<String> diagnostics) {
        public ResolutionExtractionRequest(UUID soloPlayerId, String operationId, List<SourceExcerpt> excerpts,
                String schemaVersion, String promptVersion) {
            this(soloPlayerId, operationId, excerpts, schemaVersion, promptVersion, null, 0, List.of());
        }
        public ResolutionExtractionRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "solo player id must not be null");
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
            if (attempt < 0) throw new IllegalArgumentException("attempt must not be negative");
        }
    }

    record SourceExcerpt(String documentType, PublishedEvidenceProvenance provenance, String text) {
        public SourceExcerpt(KnowledgeDocumentId documentId, long extractionVersion, String locator, String text) {
            this("STORYBOOK", new PublishedEvidenceProvenance(documentId, extractionVersion, 1,
                    List.of(), List.of(), null, locator), text);
        }

        public SourceExcerpt(String documentType, KnowledgeDocumentId documentId, long extractionVersion,
                String locator, String text) {
            this(documentType, new PublishedEvidenceProvenance(documentId, extractionVersion, 1,
                    List.of(), List.of(), null, locator), text);
        }

        public SourceExcerpt {
            if (documentType == null || documentType.isBlank()) {
                throw new IllegalArgumentException("source excerpt document type must not be blank");
            }
            provenance = java.util.Objects.requireNonNull(provenance, "source excerpt provenance must not be null");
        }

        public KnowledgeDocumentId documentId() { return provenance.documentId(); }
        public long extractionVersion() { return provenance.extractionVersion(); }
        public String locator() { return provenance.locator(); }

        public boolean isPublishedEvidence() {
            return provenance.extractionVersion() > 0 && provenance.pageNumber() > 0
                    && text != null && !text.isBlank();
        }
    }

    record CandidateRetryRequest(UUID soloPlayerId, String operationId, ResolutionCandidate failedCandidate, List<SourceExcerpt> excerpts, String schemaVersion, String promptVersion, int attempt, List<String> diagnostics) {
        public CandidateRetryRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "solo player id must not be null");
        }
    }
}

package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;

public interface ResolutionExtractionPort {
    List<ResolutionCandidate> extract(ResolutionExtractionRequest request);

    record ResolutionExtractionRequest(
            String operationId,
            List<SourceExcerpt> excerpts,
            String schemaVersion,
            String promptVersion) {}

    record SourceExcerpt(KnowledgeDocumentId documentId, long extractionVersion, String locator, String text) {}
}

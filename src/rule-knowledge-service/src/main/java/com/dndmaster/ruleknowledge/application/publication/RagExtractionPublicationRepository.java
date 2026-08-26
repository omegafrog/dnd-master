package com.dndmaster.ruleknowledge.application.publication;

import java.util.List;

public interface RagExtractionPublicationRepository {
    void beginCandidate(RagExtractionPublicationRequest request);

    /** Inserts vectors and switches the document's public version in one database transaction. */
    void publish(RagExtractionPublicationRequest request, List<EmbeddedPublishedRagChunk> chunks);

    void fail(RagExtractionPublicationRequest request, ExtractionPublicationStatus status, String reason);
}

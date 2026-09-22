package com.dndmaster.adventure.application.knowledge;

import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.UUID;
import java.util.List;

public interface KnowledgeDocumentLookupPort {
    List<KnowledgeDocumentRecord> findOwnedDocuments(UUID ownerPlayerId);

    /** Returns only rulebooks currently published in the shared catalog. */
    default List<KnowledgeDocumentRecord> findPublishedSharedCatalogDocuments() {
        return List.of();
    }

    record KnowledgeDocumentRecord(
            KnowledgeDocumentId knowledgeDocumentId,
            KnowledgeDocumentStatus status,
            String originalFilename,
            String documentType,
            long extractionVersion) {}
}

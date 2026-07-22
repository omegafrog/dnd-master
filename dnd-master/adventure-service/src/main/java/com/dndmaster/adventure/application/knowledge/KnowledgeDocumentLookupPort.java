package com.dndmaster.adventure.application.knowledge;

import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;

public interface KnowledgeDocumentLookupPort {
    List<KnowledgeDocumentRecord> findOwnedDocuments(OwnerPlayerId ownerPlayerId);

    record KnowledgeDocumentRecord(
            KnowledgeDocumentId knowledgeDocumentId,
            KnowledgeDocumentStatus status,
            String originalFilename,
            String documentType) {}
}

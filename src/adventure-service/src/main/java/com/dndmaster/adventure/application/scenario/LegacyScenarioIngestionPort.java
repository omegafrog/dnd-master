package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;

public interface LegacyScenarioIngestionPort {
    ImportedKnowledgeDocument ingest(OwnerPlayerId ownerPlayerId, String originalFilename, byte[] content);

    record ImportedKnowledgeDocument(KnowledgeDocumentId knowledgeDocumentId, long extractionVersion, String status) {}
}

package com.dndmaster.ruleknowledge.application.definition;

import java.util.Optional;
import java.util.UUID;

public interface RulebookActionDefinitionRepository {
    Optional<RulebookActionDefinitionExtraction> find(UUID documentId, String extractionVersion);
    void save(RulebookActionDefinitionExtraction extraction);
    boolean claim(UUID documentId, String extractionVersion, int preprocessedPageCount, int indexedPageCount,
            int chunkCount, int sectionCount, String contractVersion);
    Optional<RulebookActionDefinitionBatch> findBatch(UUID documentId, String extractionVersion, int batchIndex);
    void saveBatch(RulebookActionDefinitionBatch batch);
}

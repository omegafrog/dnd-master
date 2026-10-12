package com.dndmaster.ruleknowledge.application.definition;

import java.util.List;
import java.util.UUID;

public record RulebookActionDefinitionBatch(UUID documentId, String extractionVersion, int batchIndex,
        List<String> sourceChunkIds, String status, List<RulebookActionDefinition> definitions, String failureCode,
        String contractVersion, List<RulebookActionCandidate> candidates, List<RulebookActionDefinitionResult> results) {
    public RulebookActionDefinitionBatch(UUID documentId, String extractionVersion, int batchIndex,
            List<String> sourceChunkIds, String status, List<RulebookActionDefinition> definitions, String failureCode) {
        this(documentId, extractionVersion, batchIndex, sourceChunkIds, status, definitions, failureCode, null, List.of(), List.of());
    }
    public RulebookActionDefinitionBatch(UUID documentId, String extractionVersion, int batchIndex,
            List<String> sourceChunkIds, String status, List<RulebookActionDefinition> definitions, String failureCode,
            String contractVersion) {
        this(documentId, extractionVersion, batchIndex, sourceChunkIds, status, definitions, failureCode, contractVersion, List.of(), List.of());
    }
    public RulebookActionDefinitionBatch(UUID documentId, String extractionVersion, int batchIndex,
            List<String> sourceChunkIds, String status, List<RulebookActionDefinition> definitions, String failureCode,
            String contractVersion, List<RulebookActionCandidate> candidates) {
        this(documentId, extractionVersion, batchIndex, sourceChunkIds, status, definitions, failureCode, contractVersion, candidates, List.of());
    }
    public RulebookActionDefinitionBatch {
        if (documentId == null || extractionVersion == null || extractionVersion.isBlank() || batchIndex < 0
                || status == null || status.isBlank()) throw new IllegalArgumentException("batch identity and status are required");
        sourceChunkIds = sourceChunkIds == null ? List.of() : List.copyOf(sourceChunkIds);
        definitions = definitions == null ? List.of() : List.copyOf(definitions);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        results = results == null ? List.of() : List.copyOf(results);
    }
}

package com.dndmaster.ruleknowledge.application.definition;

import java.util.List;
import java.util.UUID;

public record RulebookActionDefinitionExtraction(UUID documentId, String extractionVersion, String status,
        int preprocessedPageCount, int indexedPageCount, int scannedChunkCount, int scannedSectionCount,
        List<RulebookActionDefinition> definitions, String failureCode, String contractVersion,
        List<RulebookActionQueryCoverage> queryCoverage, boolean evidenceLimitReached, int candidateEvidenceCount,
        List<RulebookActionDefinitionCoverage> actionCoverage, List<RulebookActionSharedRule> sharedRules) {
    public RulebookActionDefinitionExtraction(UUID documentId, String extractionVersion, String status,
            int preprocessedPageCount, int indexedPageCount, int scannedChunkCount, int scannedSectionCount,
            List<RulebookActionDefinition> definitions, String failureCode) {
        this(documentId, extractionVersion, status, preprocessedPageCount, indexedPageCount, scannedChunkCount,
                scannedSectionCount, definitions, failureCode, null, List.of(), false, 0, List.of(), List.of());
    }
    public RulebookActionDefinitionExtraction(UUID documentId, String extractionVersion, String status,
            int preprocessedPageCount, int indexedPageCount, int scannedChunkCount, int scannedSectionCount,
            List<RulebookActionDefinition> definitions, String failureCode, String contractVersion) {
        this(documentId, extractionVersion, status, preprocessedPageCount, indexedPageCount, scannedChunkCount,
                scannedSectionCount, definitions, failureCode, contractVersion, List.of(), false, 0, List.of(), List.of());
    }
    public RulebookActionDefinitionExtraction(UUID documentId, String extractionVersion, String status,
            int preprocessedPageCount, int indexedPageCount, int scannedChunkCount, int scannedSectionCount,
            List<RulebookActionDefinition> definitions, String failureCode, String contractVersion,
            List<RulebookActionQueryCoverage> queryCoverage, boolean evidenceLimitReached, int candidateEvidenceCount) {
        this(documentId, extractionVersion, status, preprocessedPageCount, indexedPageCount, scannedChunkCount,
                scannedSectionCount, definitions, failureCode, contractVersion, queryCoverage, evidenceLimitReached,
                candidateEvidenceCount, List.of(), List.of());
    }
    public RulebookActionDefinitionExtraction(UUID documentId, String extractionVersion, String status,
            int preprocessedPageCount, int indexedPageCount, int scannedChunkCount, int scannedSectionCount,
            List<RulebookActionDefinition> definitions, String failureCode, String contractVersion,
            List<RulebookActionQueryCoverage> queryCoverage, boolean evidenceLimitReached, int candidateEvidenceCount,
            List<RulebookActionDefinitionCoverage> actionCoverage) {
        this(documentId, extractionVersion, status, preprocessedPageCount, indexedPageCount, scannedChunkCount,
                scannedSectionCount, definitions, failureCode, contractVersion, queryCoverage, evidenceLimitReached,
                candidateEvidenceCount, actionCoverage, List.of());
    }
    public RulebookActionDefinitionExtraction {
        if (documentId == null || extractionVersion == null || extractionVersion.isBlank() || status == null || status.isBlank())
            throw new IllegalArgumentException("extraction identity and status are required");
        if (preprocessedPageCount < 0 || indexedPageCount < 0 || scannedChunkCount < 0 || scannedSectionCount < 0)
            throw new IllegalArgumentException("scan counts cannot be negative");
        if (candidateEvidenceCount < 0) throw new IllegalArgumentException("candidate evidence count cannot be negative");
        definitions = definitions == null ? List.of() : List.copyOf(definitions);
        queryCoverage = queryCoverage == null ? List.of() : List.copyOf(queryCoverage);
        actionCoverage = actionCoverage == null ? List.of() : List.copyOf(actionCoverage);
        sharedRules = sharedRules == null ? List.of() : List.copyOf(sharedRules);
    }
}

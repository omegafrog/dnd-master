package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionApplicationService;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionRequest;
import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Converts the selected player-action evidence in a confirmed runtime scope to the existing runtime form. */
public final class RuntimePlayerActionEvidenceAcquirer {
    private final EvidenceAcquisitionApplicationService acquisitionService;

    public RuntimePlayerActionEvidenceAcquirer(EvidenceAcquisitionApplicationService acquisitionService) {
        this.acquisitionService = Objects.requireNonNull(acquisitionService, "evidence acquisition service must not be null");
    }

    public List<RuntimeEvidence> acquire(EvidenceSearchScope scope, String action) {
        Objects.requireNonNull(scope, "evidence search scope must not be null");
        var result = acquisitionService.acquire(new EvidenceAcquisitionRequest("PLAYER_ACTION", action, List.of(), scope));
        Map<UUID, EvidenceSearchScope.Document> documents = new LinkedHashMap<>();
        scope.documents().forEach(document -> documents.put(document.id(), document));
        Map<UUID, EvidenceCandidate> candidates = new LinkedHashMap<>();
        result.candidates().forEach(candidate -> candidates.put(candidate.id(), candidate));
        return result.decision().selectedEvidenceIds().stream()
                .map(candidates::get)
                .filter(Objects::nonNull)
                .map(candidate -> runtimeEvidence(candidate, documents))
                .toList();
    }

    private static RuntimeEvidence runtimeEvidence(EvidenceCandidate candidate,
                                                    Map<UUID, EvidenceSearchScope.Document> documents) {
        UUID documentId;
        try {
            documentId = UUID.fromString(candidate.documentId());
        } catch (IllegalArgumentException invalidId) {
            throw new IllegalArgumentException("selected evidence document id is invalid", invalidId);
        }
        EvidenceSearchScope.Document document = documents.get(documentId);
        if (document == null || !document.type().equals(candidate.documentType())) {
            throw new IllegalArgumentException("selected evidence is outside the confirmed runtime document scope");
        }
        RuntimeEvidenceType type = RuntimeEvidenceType.valueOf(document.type());
        return new RuntimeEvidence(type, new KnowledgeDocumentId(documentId), document.extractionVersion(),
                candidate.locator(), candidate.excerpt());
    }
}

package com.dndmaster.adventure.application.runtime;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Selects a session-scoped, stage/intent-scoped evidence pack for one GM turn. */
public final class RuntimeEvidenceSelector {
    private final RuntimeEvidenceSearchPort searchPort;

    public RuntimeEvidenceSelector(RuntimeEvidenceSearchPort searchPort) {
        this.searchPort = Objects.requireNonNull(searchPort, "search port must not be null");
    }

    public RuntimeEvidenceSelection select(RuntimeEvidenceSearchRequest request, List<RuntimeEvidence> resolutionEvidence) {
        return select(request, resolutionEvidence, request.knowledgeDocumentIds());
    }

    public RuntimeEvidenceSelection select(RuntimeEvidenceSearchRequest request, List<RuntimeEvidence> resolutionEvidence,
                                           List<UUID> rulebookDocumentIds) {
        Objects.requireNonNull(request, "request must not be null");
        resolutionEvidence = List.copyOf(Objects.requireNonNull(resolutionEvidence, "resolution evidence must not be null"));
        rulebookDocumentIds = List.copyOf(Objects.requireNonNull(rulebookDocumentIds, "rulebook document ids must not be null"));
        int perSourceLimit = request.limit();
        List<RuntimeEvidence> storybook = search(request.forType(RuntimeEvidenceType.STORYBOOK, perSourceLimit),
                RuntimeEvidenceType.STORYBOOK, request, perSourceLimit);
        List<RuntimeEvidence> rulebook = rulebookDocumentIds.isEmpty() ? List.of()
                : search(request.withDocumentIds(rulebookDocumentIds, RuntimeEvidenceType.RULEBOOK, perSourceLimit),
                        RuntimeEvidenceType.RULEBOOK, request, perSourceLimit);
        List<RuntimeEvidence> resolution = resolutionEvidence.stream()
                .filter(evidence -> evidence != null && evidence.evidenceType() == RuntimeEvidenceType.RESOLUTION)
                .filter(evidence -> request.knowledgeDocumentIds().contains(evidence.knowledgeDocumentId().value()))
                .toList();

        EvidencePack pack = new EvidencePack(storybook, rulebook, resolution);
        EnumMap<RuntimeEvidenceType, Integer> counts = new EnumMap<>(RuntimeEvidenceType.class);
        counts.put(RuntimeEvidenceType.STORYBOOK, storybook.size());
        counts.put(RuntimeEvidenceType.RULEBOOK, rulebook.size());
        counts.put(RuntimeEvidenceType.RESOLUTION, resolution.size());
        return new RuntimeEvidenceSelection(pack, new RuntimeEvidenceSelectionMetrics(
                pack.totalEvidenceCount(), counts, request.contextKey(), request.actionIntent()));
    }

    private List<RuntimeEvidence> search(RuntimeEvidenceSearchRequest request, RuntimeEvidenceType expected,
                                         RuntimeEvidenceSearchRequest original, int limit) {
        return searchPort.search(request).stream()
                .filter(Objects::nonNull)
                .filter(evidence -> evidence.evidenceType() == expected)
                .filter(evidence -> request.knowledgeDocumentIds().contains(evidence.knowledgeDocumentId().value()))
                .limit(limit)
                .toList();
    }

}

package com.dndmaster.adventure.application.runtime;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Selects a session-scoped, stage/intent-scoped evidence pack for one GM turn. */
public final class RuntimeEvidenceSelector {
    public static final int MAX_EVIDENCE = 8;
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
        int max = Math.min(request.limit(), MAX_EVIDENCE);
        List<RuntimeEvidence> storybook = search(request.forType(RuntimeEvidenceType.STORYBOOK, max), RuntimeEvidenceType.STORYBOOK, request, max);
        if (storybook.isEmpty()) {
            throw new RuntimeEvidenceSelectionException(new RuntimeEvidenceSelectionViolation(
                    "MISSING_STORYBOOK", "current-stage STORYBOOK evidence is unavailable"));
        }

        int remaining = max - storybook.size();
        List<RuntimeEvidence> rulebook = List.of();
        if (remaining > 0 && !rulebookDocumentIds.isEmpty() && requiresRulebook(request.actionIntent())) {
            rulebook = search(request.withDocumentIds(rulebookDocumentIds, RuntimeEvidenceType.RULEBOOK, remaining),
                    RuntimeEvidenceType.RULEBOOK, request, remaining);
        }
        remaining -= rulebook.size();
        List<RuntimeEvidence> resolution = resolutionEvidence.stream()
                .filter(evidence -> evidence != null && evidence.evidenceType() == RuntimeEvidenceType.RESOLUTION)
                .filter(evidence -> request.knowledgeDocumentIds().contains(evidence.knowledgeDocumentId().value()))
                .limit(Math.max(0, remaining))
                .toList();

        EvidencePack pack = new EvidencePack(storybook, rulebook, resolution);
        EnumMap<RuntimeEvidenceType, Integer> counts = new EnumMap<>(RuntimeEvidenceType.class);
        counts.put(RuntimeEvidenceType.STORYBOOK, storybook.size());
        counts.put(RuntimeEvidenceType.RULEBOOK, rulebook.size());
        counts.put(RuntimeEvidenceType.RESOLUTION, resolution.size());
        return new RuntimeEvidenceSelection(pack, new RuntimeEvidenceSelectionMetrics(
                pack.totalEvidenceCount(), counts, request.stageKey(), request.actionIntent()));
    }

    private List<RuntimeEvidence> search(RuntimeEvidenceSearchRequest request, RuntimeEvidenceType expected,
                                         RuntimeEvidenceSearchRequest original, int limit) {
        return searchPort.search(request).stream()
                .filter(Objects::nonNull)
                .filter(evidence -> evidence.evidenceType() == expected)
                .filter(evidence -> original.knowledgeDocumentIds().contains(evidence.knowledgeDocumentId().value()))
                .limit(limit)
                .toList();
    }

    private static boolean requiresRulebook(String intent) {
        String normalized = intent.toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        return normalized.equals("RULE") || normalized.equals("MIXED")
                || normalized.contains("RULE_QUESTION") || normalized.contains("ADJUDICATION");
    }
}

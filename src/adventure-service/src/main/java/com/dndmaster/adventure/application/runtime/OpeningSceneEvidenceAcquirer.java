package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionApplicationService;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionRequest;
import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import com.dndmaster.adventure.evidence.EvidenceSufficiencyPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Acquires the minimum story evidence needed to prepare the first playable scene. */
public final class OpeningSceneEvidenceAcquirer {
    private static final EvidenceSufficiencyPolicy POLICY = new com.dndmaster.adventure.evidence.OpeningSceneEvidenceSufficiencyPolicy();
    private static final String OPENING_QUERY =
            "Find the first playable opening situation: the earliest numbered location where the party arrives and can act. "
                    + "Return the immediate scene, location, why the party is there, the immediate problem, and visible things they can respond to. "
                    + "Exclude summary sections, later locations, hazards, monster statistics, combat rules, and damage procedures.";

    private final EvidenceAcquisitionApplicationService acquisitionService;

    public OpeningSceneEvidenceAcquirer(EvidenceAcquisitionApplicationService acquisitionService) {
        this.acquisitionService = Objects.requireNonNull(acquisitionService, "evidence acquisition service must not be null");
    }

    public Result acquire(OwnerPlayerId ownerPlayerId, UUID sessionId, ScenarioPackage scenarioPackage) {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(scenarioPackage, "scenario package must not be null");
        List<EvidenceSearchScope.Document> documents = scenarioPackage.documents().stream()
                .filter(document -> document.role() == ScenarioBundleDocumentRole.MAIN_SCENARIO)
                .filter(document -> POLICY.documentTypes().contains(document.documentType().toUpperCase(java.util.Locale.ROOT)))
                .map(document -> new EvidenceSearchScope.Document(
                        document.knowledgeDocumentId().value(), document.extractionVersion(), document.documentType()))
                .toList();
        if (documents.isEmpty()) {
            return insufficient(scenarioPackage, "the selected scenario package has no STORYBOOK opening source");
        }

        EvidenceSearchScope scope = new EvidenceSearchScope(
                ownerPlayerId.value(), sessionId, scenarioPackage.packageId(), "opening", "STORY",
                documents, List.of());
        var acquisition = acquisitionService.acquire(new EvidenceAcquisitionRequest(
                POLICY.policyId(), OPENING_QUERY, List.of(), scope));
        if (!acquisition.decision().sufficient()) {
            return insufficient(scenarioPackage, acquisition.decision().missing());
        }

        LinkedHashMap<UUID, EvidenceCandidate> candidates = new LinkedHashMap<>();
        acquisition.candidates().forEach(candidate -> candidates.put(candidate.id(), candidate));
        List<EvidenceCandidate> selected = acquisition.decision().selectedEvidenceIds().stream()
                .map(candidates::get)
                .filter(Objects::nonNull)
                .toList();
        if (selected.isEmpty()) {
            return insufficient(scenarioPackage, "the opening sufficiency decision selected no evidence");
        }
        return new Result(scenarioPackage.packageId(), true, selected, "",
                POLICY.finalInsufficiency());
    }

    private static Result insufficient(ScenarioPackage scenarioPackage, String missing) {
        return new Result(scenarioPackage.packageId(), false, List.of(), missing,
                POLICY.finalInsufficiency());
    }

    public record Result(UUID scenarioPackageId, boolean sufficient, List<EvidenceCandidate> selectedEvidence,
                         String missing, EvidenceSufficiencyPolicy.FinalInsufficiency finalInsufficiency) {
        public Result {
            Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
            selectedEvidence = List.copyOf(Objects.requireNonNull(selectedEvidence, "selected evidence must not be null"));
            missing = missing == null ? "" : missing.trim();
            Objects.requireNonNull(finalInsufficiency, "final insufficiency must not be null");
            if (sufficient && selectedEvidence.isEmpty()) throw new IllegalArgumentException("sufficient opening result requires evidence");
            if (!sufficient && missing.isBlank()) throw new IllegalArgumentException("insufficient opening result requires missing information");
        }
    }
}

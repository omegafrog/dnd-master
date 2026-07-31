package com.dndmaster.adventure.domain.adventure;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CampaignPlan(
        UUID planId,
        SessionId sessionId,
        UUID scenarioPackageId,
        long scenarioPackageRevision,
        long revision,
        String overview,
        List<CampaignDocumentRevision> documents,
        List<CharacterSheetId> characterSheetIds,
        List<CampaignPlanEvidence> evidence,
        List<CampaignStage> stages) {
    public CampaignPlan {
        planId = Objects.requireNonNull(planId, "plan id must not be null");
        sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (scenarioPackageRevision <= 0) throw new IllegalArgumentException("scenario package revision must be positive");
        if (revision <= 0) throw new IllegalArgumentException("campaign plan revision must be positive");
        overview = required(overview, "overview");
        documents = List.copyOf(Objects.requireNonNull(documents, "documents must not be null"));
        characterSheetIds = List.copyOf(Objects.requireNonNull(characterSheetIds, "character sheet ids must not be null"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        stages = List.copyOf(Objects.requireNonNull(stages, "stages must not be null"));
        if (documents.isEmpty()) throw new IllegalArgumentException("campaign plan requires storybook document revisions");
        if (characterSheetIds.isEmpty()) throw new IllegalArgumentException("campaign plan requires active character sheets");
        if (evidence.isEmpty()) throw new IllegalArgumentException("campaign plan requires storybook evidence");
        if (stages.isEmpty()) throw new IllegalArgumentException("campaign plan requires stages");

        Set<String> documentKeys = new HashSet<>();
        for (CampaignDocumentRevision document : documents) {
            Objects.requireNonNull(document, "documents must not contain null");
            if (!documentKeys.add(document.knowledgeDocumentId().value() + ":" + document.extractionVersion())) {
                throw new IllegalArgumentException("campaign plan document revisions must be unique");
            }
        }
        if (new HashSet<>(characterSheetIds).size() != characterSheetIds.size()) {
            throw new IllegalArgumentException("campaign plan character sheets must be unique");
        }

        Set<UUID> evidenceIds = new HashSet<>();
        for (CampaignPlanEvidence item : evidence) {
            Objects.requireNonNull(item, "evidence must not contain null");
            if (!documentKeys.contains(item.knowledgeDocumentId().value() + ":" + item.extractionVersion())) {
                throw new IllegalArgumentException("campaign evidence must belong to a persisted document revision");
            }
            if (!evidenceIds.add(item.evidenceId())) {
                throw new IllegalArgumentException("campaign evidence ids must be unique");
            }
        }

        for (int index = 0; index < stages.size(); index++) {
            CampaignStage stage = Objects.requireNonNull(stages.get(index), "stages must not contain null");
            if (stage.order() != index + 1) throw new IllegalArgumentException("campaign stage order must be contiguous");
            if (!evidenceIds.containsAll(stage.evidenceIds())) {
                throw new IllegalArgumentException("campaign stage cites unknown evidence");
            }
        }
    }

    public boolean matches(
            long currentScenarioPackageRevision,
            List<CampaignDocumentRevision> currentDocuments,
            List<CharacterSheetId> currentCharacterSheetIds) {
        return scenarioPackageRevision == currentScenarioPackageRevision
                && documents.equals(currentDocuments)
                && characterSheetIds.equals(currentCharacterSheetIds);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}

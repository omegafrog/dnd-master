package com.dndmaster.adventure.domain.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Immutable compilation input captured from one bundle revision. */
public record ScenarioCompilationInputSnapshot(
        ScenarioBundleId bundleId,
        long bundleRevision,
        List<StorybookInput> storybooks,
        UUID primaryStorybookId,
        String integrationPrompt,
        ScenarioCreativity creativity) {
    public ScenarioCompilationInputSnapshot {
        bundleId = Objects.requireNonNull(bundleId, "bundle id is required");
        if (bundleRevision <= 0) throw new IllegalArgumentException("bundle revision must be positive");
        storybooks = List.copyOf(Objects.requireNonNull(storybooks, "storybooks are required"));
        if (storybooks.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("storybooks cannot contain null");
        integrationPrompt = integrationPrompt == null ? "" : integrationPrompt.trim();
        creativity = creativity == null ? ScenarioCreativity.CONSERVATIVE : creativity;
    }

    public static ScenarioCompilationInputSnapshot capture(
            ScenarioBundleId bundleId,
            long bundleRevision,
            List<ScenarioBundleDocumentSelection> documents,
            UUID requestedPrimaryStorybookId,
            String integrationPrompt,
            ScenarioCreativity creativity) {
        Objects.requireNonNull(documents, "documents are required");
        List<StorybookInput> storybooks = documents.stream()
                .filter(Objects::nonNull)
                .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                .map(document -> new StorybookInput(document.knowledgeDocumentId().value(), document.extractionVersion(),
                        document.role(), document.documentType()))
                .toList();
        UUID primary = requestedPrimaryStorybookId;
        if (primary == null && storybooks.size() == 1) primary = storybooks.getFirst().documentId();
        return new ScenarioCompilationInputSnapshot(bundleId, bundleRevision, storybooks, primary,
                integrationPrompt, creativity);
    }

    public List<ScenarioCompilationDiagnostic> validate() {
        List<ScenarioCompilationDiagnostic> diagnostics = new ArrayList<>();
        if (storybooks.isEmpty()) {
            diagnostics.add(ScenarioCompilationDiagnostic.blocking("STORYBOOK_REQUIRED",
                    "at least one Storybook is required"));
        }
        if (storybooks.size() > 1 && primaryStorybookId == null) {
            diagnostics.add(ScenarioCompilationDiagnostic.blocking("PRIMARY_STORYBOOK_REQUIRED",
                    "a Primary Storybook is required when multiple Storybooks are selected"));
        }
        if (primaryStorybookId != null && storybooks.stream().noneMatch(source -> source.documentId().equals(primaryStorybookId))) {
            diagnostics.add(ScenarioCompilationDiagnostic.blocking("PRIMARY_STORYBOOK_NOT_SELECTED",
                    "the Primary Storybook must be one of the selected Storybooks"));
        }
        return List.copyOf(diagnostics);
    }

    public record StorybookInput(UUID documentId, long extractionVersion, ScenarioBundleDocumentRole role, String documentType) {
        public StorybookInput {
            documentId = Objects.requireNonNull(documentId, "storybook document id is required");
            if (extractionVersion <= 0) throw new IllegalArgumentException("storybook extraction version must be positive");
            role = Objects.requireNonNull(role, "storybook role is required");
            if (documentType == null || documentType.isBlank()) throw new IllegalArgumentException("storybook document type is required");
        }
    }
}

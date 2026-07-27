package com.dndmaster.adventure.domain.scenario;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ScenarioSourceBundleRevision(long revision, List<ScenarioBundleDocumentSelection> documents) {
    public ScenarioSourceBundleRevision {
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be positive");
        }
        documents = List.copyOf(Objects.requireNonNull(documents, "documents must not be null"));
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("documents must not be empty");
        }
        var seen = new HashSet<>();
        for (ScenarioBundleDocumentSelection document : documents) {
            Objects.requireNonNull(document, "documents must not contain null");
            if (!seen.add(document.knowledgeDocumentId())) {
                throw new IllegalArgumentException("documents must be unique");
            }
        }
    }
}

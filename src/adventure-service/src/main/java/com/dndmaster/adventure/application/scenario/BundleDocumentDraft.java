package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import java.util.Objects;

public record BundleDocumentDraft(KnowledgeDocumentId knowledgeDocumentId, ScenarioBundleDocumentRole role) {
    public BundleDocumentDraft {
        knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
        role = Objects.requireNonNull(role, "role must not be null");
    }
}

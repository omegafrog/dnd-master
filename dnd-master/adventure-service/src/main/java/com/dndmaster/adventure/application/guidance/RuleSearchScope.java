package com.dndmaster.adventure.application.guidance;

import com.dndmaster.adventure.domain.inquiry.RulebookId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Objects;

public record RuleSearchScope(boolean ready, List<KnowledgeDocumentId> selectedKnowledgeDocuments) {
    public RuleSearchScope {
        selectedKnowledgeDocuments = List.copyOf(Objects.requireNonNull(
                selectedKnowledgeDocuments, "selected knowledge documents must not be null"));
        if (ready && selectedKnowledgeDocuments.isEmpty()) {
            throw new IllegalArgumentException("a ready scope needs selected knowledge documents");
        }
    }

    public List<RulebookId> selectedRulebooks() {
        return selectedKnowledgeDocuments.stream().map(documentId -> new RulebookId(documentId.value())).toList();
    }
}

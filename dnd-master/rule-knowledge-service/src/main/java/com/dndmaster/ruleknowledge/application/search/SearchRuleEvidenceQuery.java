package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.List;
import java.util.Objects;

public record SearchRuleEvidenceQuery(
        OwnerPlayerId owner,
        List<KnowledgeDocumentId> selectedKnowledgeDocuments,
        String situation,
        QueryIntent queryIntent,
        int limit) {

    public SearchRuleEvidenceQuery {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(selectedKnowledgeDocuments, "selectedKnowledgeDocuments must not be null");
        if (selectedKnowledgeDocuments.isEmpty()) {
            throw new IllegalArgumentException("at least one selected knowledge document is required");
        }
        Objects.requireNonNull(situation, "situation must not be null");
        if (situation.isBlank()) {
            throw new IllegalArgumentException("situation must not be blank");
        }
        Objects.requireNonNull(queryIntent, "queryIntent must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        selectedKnowledgeDocuments = List.copyOf(selectedKnowledgeDocuments);
    }
}

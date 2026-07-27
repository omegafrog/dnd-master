package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.util.Objects;

public record StorySourceScope(KnowledgeDocumentId documentId, long extractionVersion) {
    public StorySourceScope {
        Objects.requireNonNull(documentId, "document id must not be null");
        if (extractionVersion < 0) {
            throw new IllegalArgumentException("extraction version must not be negative");
        }
    }
}

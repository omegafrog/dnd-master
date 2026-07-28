package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.util.Objects;

public record CharacterContextDocumentScope(KnowledgeDocumentId documentId, long extractionVersion) {
    public CharacterContextDocumentScope {
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (extractionVersion < 0) throw new IllegalArgumentException("extractionVersion must not be negative");
    }
}

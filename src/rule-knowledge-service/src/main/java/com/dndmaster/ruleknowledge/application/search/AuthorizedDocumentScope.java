package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.util.Objects;

/** A caller-authorized document and its currently usable published extraction. */
public record AuthorizedDocumentScope(
        KnowledgeDocumentId documentId, long extractionVersion, DocumentType documentType) {
    public AuthorizedDocumentScope {
        Objects.requireNonNull(documentId, "document id must not be null");
        Objects.requireNonNull(documentType, "document type must not be null");
        if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
        if (documentType != DocumentType.RULEBOOK && documentType != DocumentType.STORYBOOK) {
            throw new IllegalArgumentException("document type must be rulebook or storybook");
        }
    }
}

package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.*;

public record CharacterContextSearchHit(
        KnowledgeDocumentId documentId, DocumentType documentType, long extractionVersion,
        String locator, String excerpt, double similarity) {
    public CharacterContextEvidence toEvidence() {
        return new CharacterContextEvidence(documentId, documentType, extractionVersion, locator, excerpt, similarity);
    }
}

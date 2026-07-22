package com.dndmaster.adventure.application.knowledge;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public final class UnindexedKnowledgeDocumentSelectionException extends IllegalStateException {
    public UnindexedKnowledgeDocumentSelectionException(KnowledgeDocumentId knowledgeDocumentId) {
        super("selected knowledge document is not indexed: " + knowledgeDocumentId.value());
    }
}

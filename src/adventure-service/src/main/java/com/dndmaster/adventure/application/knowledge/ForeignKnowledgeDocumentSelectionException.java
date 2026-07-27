package com.dndmaster.adventure.application.knowledge;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public final class ForeignKnowledgeDocumentSelectionException extends IllegalArgumentException {
    public ForeignKnowledgeDocumentSelectionException() {
        super("selected knowledge document does not belong to the owner");
    }
}

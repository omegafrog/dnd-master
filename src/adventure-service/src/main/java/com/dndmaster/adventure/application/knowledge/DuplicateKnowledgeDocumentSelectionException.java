package com.dndmaster.adventure.application.knowledge;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class DuplicateKnowledgeDocumentSelectionException extends IllegalArgumentException {
    public DuplicateKnowledgeDocumentSelectionException() {
        super("session knowledge selection must not contain duplicates");
    }
}

package com.dndmaster.adventure.application.knowledge;

public enum KnowledgeDocumentStatus implements com.dndmaster.adventure.domain.scenario.KnowledgeDocumentStatus {
    UPLOADED,
    QUEUED,
    PROCESSING,
    NEEDS_INPUT,
    NEEDS_REVIEW,
    EXTRACTED,
    INDEXED,
    PARTIAL_AWAITING_CONFIRMATION,
    PARTIAL_CONFIRMED,
    FAILED,
    REJECTED
}

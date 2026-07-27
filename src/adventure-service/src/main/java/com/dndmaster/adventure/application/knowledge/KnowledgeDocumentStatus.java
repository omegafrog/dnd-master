package com.dndmaster.adventure.application.knowledge;

public enum KnowledgeDocumentStatus implements com.dndmaster.adventure.domain.scenario.KnowledgeDocumentStatus {
    UPLOADED,
    NEEDS_INPUT,
    EXTRACTED,
    INDEXED,
    PARTIAL_AWAITING_CONFIRMATION,
    PARTIAL_CONFIRMED,
    REJECTED
}

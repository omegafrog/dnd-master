package com.dndmaster.ruleknowledge.domain.rulebook;

public enum ProcessingStatus {
    QUEUED,
    PROCESSING,
    INDEXED,
    FAILED,
    NEEDS_INPUT,
    UPLOADED,
    EXTRACTED,
    PARTIAL_AWAITING_CONFIRMATION,
    PARTIAL_CONFIRMED,
    REJECTED
}

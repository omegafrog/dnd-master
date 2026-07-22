package com.dndmaster.ruleknowledge.domain.rulebook;

public enum ProcessingStatus {
    QUEUED,
    PROCESSING,
    INDEXED,
    FAILED,
    UPLOADED,
    EXTRACTED,
    PARTIAL_AWAITING_CONFIRMATION,
    PARTIAL_CONFIRMED,
    REJECTED
}

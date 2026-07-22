package com.dndmaster.ruleknowledge.domain.rulebook;

public enum ProcessingStatus {
    UPLOADED,
    EXTRACTED,
    INDEXED,
    PARTIAL_AWAITING_CONFIRMATION,
    PARTIAL_CONFIRMED,
    REJECTED
}

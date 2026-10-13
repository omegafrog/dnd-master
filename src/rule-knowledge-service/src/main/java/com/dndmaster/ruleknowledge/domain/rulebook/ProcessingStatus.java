package com.dndmaster.ruleknowledge.domain.rulebook;

public enum ProcessingStatus {
    QUEUED,
    PROCESSING,
    VALIDATED,
    INDEXED,
    INDEXED_WITH_WARNINGS,
    FAILED,
    NEEDS_REVIEW,
    NEEDS_INPUT,
    UPLOADED,
    EXTRACTED,
    PARTIAL_AWAITING_CONFIRMATION,
    PARTIAL_CONFIRMED,
    REJECTED;

    public boolean isSearchable() {
        return this == INDEXED || this == INDEXED_WITH_WARNINGS || this == PARTIAL_CONFIRMED;
    }
}

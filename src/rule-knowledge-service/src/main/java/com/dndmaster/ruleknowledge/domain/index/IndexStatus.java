package com.dndmaster.ruleknowledge.domain.index;

public enum IndexStatus {
    PENDING,
    EMBEDDING,
    READY,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
}

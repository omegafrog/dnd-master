package com.dndmaster.gmeval.tuning;

/** Closed taxonomy used to justify tuning and audit excluded examples. */
public enum TuningFailureCategory {
    SECRET_LEAK,
    RULE_CONTRADICTION,
    AGENCY_VIOLATION,
    UNRESOLVED_HALLUCINATION,
    PERMISSION_UNCLEAR
}

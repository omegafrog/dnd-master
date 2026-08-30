package com.dndmaster.adventure.application.runtime;

public enum RuntimeTurnFailureCode {
    PROVIDER_TIMEOUT,
    PROVIDER_UNAVAILABLE,
    JSON_INVALID,
    CITATION_INVALID,
    JUDGMENT_INVALID,
    NARRATION_INVALID,
    SAFETY_FAILURE,
    VERSION_CONFLICT,
    UNKNOWN
}

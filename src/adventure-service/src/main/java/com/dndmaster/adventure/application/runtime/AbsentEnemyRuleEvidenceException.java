package com.dndmaster.adventure.application.runtime;

/** Signals that the pinned rule sources contain no evidence to prepare a complete enemy profile. */
public final class AbsentEnemyRuleEvidenceException extends RuntimeException {
    public AbsentEnemyRuleEvidenceException(String message) { super(message); }
}

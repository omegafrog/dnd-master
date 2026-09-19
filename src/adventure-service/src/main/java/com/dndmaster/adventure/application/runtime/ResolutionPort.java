package com.dndmaster.adventure.application.runtime;

public interface ResolutionPort {
    ResolutionResult resolve(CheckSelection selection, int systemRoll);

    /** Typed boundary for a player-submitted spatial check. */
    default PlayerCheckResult resolvePlayerCheck(PlayerCheckRequest request) {
        throw new UnsupportedOperationException("player check resolution is unavailable");
    }

    record PlayerCheckRequest(String ruleReference, int difficulty, int rollTotal) {}
    record PlayerCheckResult(int total, boolean success) {}
}

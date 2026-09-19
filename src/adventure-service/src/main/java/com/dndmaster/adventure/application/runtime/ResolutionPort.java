package com.dndmaster.adventure.application.runtime;

public interface ResolutionPort {
    ResolutionResult resolve(CheckSelection selection, int systemRoll);

    /** Typed boundary for a player-submitted spatial check. */
    default PlayerCheckResult resolvePlayerCheck(PlayerCheckRequest request) {
        throw new UnsupportedOperationException("player check resolution is unavailable");
    }

    record PlayerCheckRequest(String ruleReference, String diceExpression, int modifier,
            Integer difficulty, int rollTotal) {
        public PlayerCheckRequest(String ruleReference, int difficulty, int rollTotal) {
            this(ruleReference, "1d20", 0, difficulty, rollTotal);
        }

        public TypedCheckRule rule() {
            return new TypedCheckRule(ruleReference, diceExpression, modifier, difficulty);
        }
    }

    record PlayerCheckResult(String ruleReference, String diceExpression, int modifier,
            Integer difficulty, int total, boolean success) {
        public PlayerCheckResult(int total, boolean success) {
            this("", "", 0, null, total, success);
        }
    }
}

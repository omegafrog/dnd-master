package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.SemanticVerdict;
import com.dndmaster.adventure.domain.adventure.SemanticVerdictType;

public final class StoryPlanVerdictPolicy {
    private StoryPlanVerdictPolicy() {}
    public enum Decision { ACCEPT, READY_WITH_WARNING, RETRY, BLOCK }
    public static Decision decide(SemanticVerdict verdict, int attempt, int maxAttempts) {
        if (verdict == null || verdict.failureCode().equals("JUDGE_UNAVAILABLE")) return attempt < maxAttempts ? Decision.RETRY : Decision.BLOCK;
        return switch (verdict.type()) {
            case COMPATIBLE -> Decision.ACCEPT;
            case UNCERTAIN -> Decision.READY_WITH_WARNING;
            case CONTRADICTORY -> attempt < maxAttempts ? Decision.RETRY : Decision.BLOCK;
        };
    }
}

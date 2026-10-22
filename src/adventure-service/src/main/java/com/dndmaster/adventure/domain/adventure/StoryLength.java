package com.dndmaster.adventure.domain.adventure;

public enum StoryLength {
    SHORT(0.25), NORMAL(0.5), LONG(1.0);

    private final double optionalSituationRatio;

    StoryLength(double optionalSituationRatio) { this.optionalSituationRatio = optionalSituationRatio; }

    public int optionalSituationAllowance(int requiredSituationCount) {
        if (requiredSituationCount < 0) throw new IllegalArgumentException("required situation count must not be negative");
        return (int) Math.ceil(requiredSituationCount * optionalSituationRatio);
    }
}

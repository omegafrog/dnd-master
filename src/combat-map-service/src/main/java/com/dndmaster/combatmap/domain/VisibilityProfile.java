package com.dndmaster.combatmap.domain;

/** Presentation-safe finite vision envelope used when no ruleset sight range is supplied. */
public record VisibilityProfile(int maxRangeCells) {
    public static final VisibilityProfile DEFAULT = new VisibilityProfile(6);

    public VisibilityProfile {
        if (maxRangeCells < 1) throw new IllegalArgumentException("visibility range must be positive");
    }
}

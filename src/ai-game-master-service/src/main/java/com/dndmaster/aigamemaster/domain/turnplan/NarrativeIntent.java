package com.dndmaster.aigamemaster.domain.turnplan;

public record NarrativeIntent(ScenePurpose scenePurpose, NarrativeTone tone, NarrativePacing pacing) {
    public NarrativeIntent {
        if (scenePurpose == null || tone == null || pacing == null) throw new IllegalArgumentException("narrative intent required");
    }
}

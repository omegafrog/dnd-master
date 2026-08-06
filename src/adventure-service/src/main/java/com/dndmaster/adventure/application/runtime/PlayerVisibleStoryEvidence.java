package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PlayerVisibleStoryEvidence {
    private PlayerVisibleStoryEvidence() {}

    public static List<RuntimeEvidence> project(List<RuntimeEvidence> evidence, Set<String> committedEvents, long gameTurn) {
        Objects.requireNonNull(evidence);
        Objects.requireNonNull(committedEvents);
        if (gameTurn < 0) throw new IllegalArgumentException("game turn must not be negative");
        return evidence.stream()
                .filter(item -> item.evidenceType() == RuntimeEvidenceType.STORYBOOK)
                .filter(item -> item.visibility().visibleToPlayer(item.disclosureEvent(), item.disclosureTurn(), committedEvents, gameTurn))
                .toList();
    }

    public static String redactNarration(String narration, List<RuntimeEvidence> evidence,
            Set<String> committedEvents, long gameTurn) {
        Objects.requireNonNull(narration);
        var visible = project(evidence, committedEvents, gameTurn);
        boolean leak = evidence.stream().filter(item -> item.evidenceType() == RuntimeEvidenceType.STORYBOOK)
                .filter(item -> !visible.contains(item))
                .anyMatch(item -> narration.toLowerCase(java.util.Locale.ROOT)
                        .contains(item.excerpt().toLowerCase(java.util.Locale.ROOT)));
        return leak ? "공개할 수 있는 장면 정보가 없습니다." : narration;
    }
}

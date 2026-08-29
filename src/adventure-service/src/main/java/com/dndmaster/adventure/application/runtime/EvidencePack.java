package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

// 한 턴에서 함께 다루는 STORYBOOK, RULEBOOK, RESOLUTION 근거 묶음이다.
public record EvidencePack(List<RuntimeEvidence> storybook, List<RuntimeEvidence> rulebook, List<RuntimeEvidence> resolution) {
    public EvidencePack {
        storybook = List.copyOf(Objects.requireNonNull(storybook, "storybook evidence must not be null"));
        rulebook = List.copyOf(Objects.requireNonNull(rulebook, "rulebook evidence must not be null"));
        resolution = List.copyOf(Objects.requireNonNull(resolution, "resolution evidence must not be null"));
        if (storybook.size() + rulebook.size() + resolution.size() > 8) {
            throw new IllegalArgumentException("evidence pack must contain at most eight items");
        }
    }

    public List<RuntimeEvidence> all() {
        return java.util.stream.Stream.of(storybook, rulebook, resolution).flatMap(List::stream).toList();
    }

    public int totalEvidenceCount() {
        return storybook.size() + rulebook.size() + resolution.size();
    }
}

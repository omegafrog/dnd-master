package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

// 출처를 보존한 규칙 근거와 판정 결과 근거를 함께 전달한다.
public record EvidencePack(List<RuntimeEvidence> storybook, List<RuntimeEvidence> rulebook, List<RuntimeEvidence> resolution) {
    public EvidencePack {
        storybook = List.copyOf(Objects.requireNonNull(storybook, "storybook evidence must not be null"));
        rulebook = List.copyOf(Objects.requireNonNull(rulebook, "rulebook evidence must not be null"));
        resolution = List.copyOf(Objects.requireNonNull(resolution, "resolution evidence must not be null"));
    }

    public List<RuntimeEvidence> all() {
        return java.util.stream.Stream.of(storybook, rulebook, resolution).flatMap(List::stream).toList();
    }

    public int totalEvidenceCount() {
        return storybook.size() + rulebook.size() + resolution.size();
    }

    public List<RuntimeEvidence> rules() {
        return java.util.stream.Stream.concat(storybook.stream(), rulebook.stream()).toList();
    }

    public EvidencePack prioritizingRulebook(List<RuntimeEvidence> additionalRulebook) {
        List<RuntimeEvidence> prioritized = java.util.stream.Stream.concat(
                        additionalRulebook == null ? java.util.stream.Stream.empty() : additionalRulebook.stream(),
                        rulebook.stream())
                .filter(Objects::nonNull).distinct().toList();
        return new EvidencePack(storybook, prioritized, resolution);
    }

    public EvidencePack prioritizingCombatEvidence(List<RuntimeEvidence> additionalEvidence) {
        List<RuntimeEvidence> additional = additionalEvidence == null ? List.of()
                : additionalEvidence.stream().filter(Objects::nonNull).distinct().toList();
        List<RuntimeEvidence> prioritizedStorybook = java.util.stream.Stream.concat(
                        additional.stream().filter(item -> item.evidenceType() == RuntimeEvidenceType.STORYBOOK),
                        storybook.stream())
                .distinct().toList();
        List<RuntimeEvidence> prioritizedRulebook = java.util.stream.Stream.concat(
                        additional.stream().filter(item -> item.evidenceType() == RuntimeEvidenceType.RULEBOOK),
                        rulebook.stream())
                .distinct().toList();
        List<RuntimeEvidence> prioritizedResolution = java.util.stream.Stream.concat(
                        additional.stream().filter(item -> item.evidenceType() == RuntimeEvidenceType.RESOLUTION),
                        resolution.stream())
                .distinct().toList();
        return new EvidencePack(prioritizedStorybook, prioritizedRulebook, prioritizedResolution);
    }
}

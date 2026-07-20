package com.dndmaster.adventure.application.guidance;

import com.dndmaster.adventure.domain.inquiry.CandidateRule;
import com.dndmaster.adventure.domain.inquiry.EvidenceStatus;
import com.dndmaster.adventure.domain.inquiry.RuleAnswer;
import java.util.List;
import java.util.Objects;

public record GuidanceComposition(EvidenceStatus status, RuleAnswer answer, List<CandidateRule> candidates) {
    public GuidanceComposition {
        Objects.requireNonNull(status, "evidence status must not be null");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
    }

    public static GuidanceComposition sufficient(RuleAnswer answer) {
        return new GuidanceComposition(EvidenceStatus.SUFFICIENT, Objects.requireNonNull(answer), List.of());
    }

    public static GuidanceComposition uncertain(EvidenceStatus status, List<CandidateRule> candidates) {
        return new GuidanceComposition(status, null, candidates);
    }
}

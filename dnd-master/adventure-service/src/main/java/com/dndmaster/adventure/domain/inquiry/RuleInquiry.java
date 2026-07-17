package com.dndmaster.adventure.domain.inquiry;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RuleInquiry {
    private final InquiryId id;
    private final AdventureId adventureId;
    private final RuleSetId ruleSetId;
    private final String situation;
    private EvidenceStatus evidenceStatus;
    private RuleAnswer answer;
    private List<CandidateRule> candidateRules = List.of();
    private CandidateRule selectedRule;

    public RuleInquiry(InquiryId id, AdventureId adventureId, RuleSetId ruleSetId, String situation) {
        this.id = Objects.requireNonNull(id, "inquiry id must not be null");
        this.adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        this.ruleSetId = Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        if (situation == null || situation.isBlank()) throw new IllegalArgumentException("situation must not be blank");
        this.situation = situation.trim();
    }

    public void presentAnswer(EvidenceStatus status, RuleAnswer ruleAnswer) {
        if (status != EvidenceStatus.SUFFICIENT) {
            throw new IllegalArgumentException("only sufficient evidence can be presented as an answer");
        }
        evidenceStatus = status;
        answer = Objects.requireNonNull(ruleAnswer, "answer must not be null");
        candidateRules = List.of();
        selectedRule = null;
    }

    public void discloseCandidates(EvidenceStatus status, List<CandidateRule> candidates) {
        if (status != EvidenceStatus.INSUFFICIENT && status != EvidenceStatus.CONFLICTING) {
            throw new IllegalArgumentException("candidate disclosure requires insufficient or conflicting evidence");
        }
        List<CandidateRule> disclosed = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        if (disclosed.isEmpty() || disclosed.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("all uncertain outcomes must disclose candidates");
        }
        evidenceStatus = status;
        answer = null;
        candidateRules = disclosed;
        selectedRule = null;
    }

    public void selectFinalRule(CandidateRule candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (!candidateRules.contains(candidate)) throw new UndisclosedCandidateSelectionException();
        selectedRule = candidate;
    }

    public InquiryId id() { return id; }
    public AdventureId adventureId() { return adventureId; }
    public RuleSetId ruleSetId() { return ruleSetId; }
    public String situation() { return situation; }
    public EvidenceStatus evidenceStatus() { return evidenceStatus; }
    public Optional<RuleAnswer> answer() { return Optional.ofNullable(answer); }
    public List<CandidateRule> candidateRules() { return candidateRules; }
    public Optional<CandidateRule> selectedRule() { return Optional.ofNullable(selectedRule); }
}

package com.dndmaster.adventure.application.guidance;

import com.dndmaster.adventure.domain.inquiry.CandidateRule;
import com.dndmaster.adventure.domain.inquiry.EvidenceStatus;
import com.dndmaster.adventure.domain.inquiry.InquiryId;
import com.dndmaster.adventure.domain.inquiry.RuleInquiry;
import com.dndmaster.adventure.domain.inquiry.SourceLocation;
import java.util.List;
import java.util.Objects;

public final class RuleGuidanceApplicationService {
    private final RuleInquiryRepository repository;
    private final RuleSetSearchScopePort scopePort;
    private final RuleEvidenceSearchPort searchPort;
    private final RuleAnswerCompositionPort compositionPort;

    public RuleGuidanceApplicationService(
            RuleInquiryRepository repository, RuleSetSearchScopePort scopePort,
            RuleEvidenceSearchPort searchPort, RuleAnswerCompositionPort compositionPort) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.scopePort = Objects.requireNonNull(scopePort, "scope port must not be null");
        this.searchPort = Objects.requireNonNull(searchPort, "search port must not be null");
        this.compositionPort = Objects.requireNonNull(compositionPort, "composition port must not be null");
    }

    public RuleInquiry answerInquiry(AnswerRuleInquiryCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        RuleSearchScope scope = scopePort.resolve(
                command.adventureId(), command.ruleSetId(), command.requestingOwner());
        if (!scope.ready()) throw new RuleGuidanceNotReadyException();

        List<RuleEvidence> evidence = searchPort.search(
                command.requestingOwner(), scope.selectedRulebooks(), command.situation());
        requireSelectedSources(evidence.stream().map(RuleEvidence::source).toList(), scope);
        GuidanceComposition composition = compositionPort.compose(command.situation(), List.copyOf(evidence));

        RuleInquiry inquiry = new RuleInquiry(
                command.inquiryId(), command.adventureId(), command.ruleSetId(), command.situation());
        if (composition.status() == EvidenceStatus.SUFFICIENT) {
            if (composition.answer() == null) throw new IllegalArgumentException("sufficient guidance requires an answer");
            requireSelectedSources(composition.answer().sources(), scope);
            inquiry.presentAnswer(composition.status(), composition.answer());
        } else {
            composition.candidates().forEach(candidate -> requireSelectedSources(candidate.sources(), scope));
            inquiry.discloseCandidates(composition.status(), composition.candidates());
        }
        repository.save(inquiry);
        return inquiry;
    }

    public RuleInquiry selectFinalRule(InquiryId inquiryId, CandidateRule candidate) {
        RuleInquiry inquiry = repository.findById(Objects.requireNonNull(inquiryId, "inquiry id must not be null"))
                .orElseThrow(RuleInquiryNotFoundException::new);
        inquiry.selectFinalRule(candidate);
        repository.save(inquiry);
        return inquiry;
    }

    private static void requireSelectedSources(List<SourceLocation> sources, RuleSearchScope scope) {
        boolean outOfScope = sources.stream()
                .anyMatch(source -> !scope.selectedRulebooks().contains(source.rulebookId()));
        if (outOfScope) throw new OutOfScopeRuleEvidenceException();
    }
}

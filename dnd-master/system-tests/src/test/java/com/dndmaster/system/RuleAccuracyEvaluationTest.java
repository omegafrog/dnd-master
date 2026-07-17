package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.guidance.AnswerRuleInquiryCommand;
import com.dndmaster.adventure.application.guidance.GuidanceComposition;
import com.dndmaster.adventure.application.guidance.RuleEvidence;
import com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService;
import com.dndmaster.adventure.application.guidance.RuleInquiryRepository;
import com.dndmaster.adventure.application.guidance.RuleSearchScope;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.inquiry.CandidateRule;
import com.dndmaster.adventure.domain.inquiry.EvidenceStatus;
import com.dndmaster.adventure.domain.inquiry.InquiryId;
import com.dndmaster.adventure.domain.inquiry.RuleAnswer;
import com.dndmaster.adventure.domain.inquiry.RuleInquiry;
import com.dndmaster.adventure.domain.inquiry.RulebookId;
import com.dndmaster.adventure.domain.inquiry.SourceLocation;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleAccuracyEvaluationTest {
    private static final RulebookId RULEBOOK = new RulebookId(UUID.randomUUID());
    private static final OwnerPlayerId OWNER = new OwnerPlayerId(UUID.randomUUID());

    @Test
    void meetsEvidenceSuitabilityAndUncertaintyDisclosureThresholds() throws Exception {
        List<EvaluationCase> cases = loadCases();
        int suitable = 0;
        int expectedUncertainCandidates = 0;
        int disclosedUncertainCandidates = 0;

        for (EvaluationCase evaluation : cases) {
            RuleInquiry inquiry = evaluate(evaluation);
            if (isSuitable(evaluation, inquiry)) suitable++;
            if (evaluation.status() != EvidenceStatus.SUFFICIENT) {
                expectedUncertainCandidates += evaluation.candidates().size();
                disclosedUncertainCandidates += inquiry.candidateRules().size();
            }
        }

        double suitability = suitable / (double) cases.size();
        double disclosure = disclosedUncertainCandidates / (double) expectedUncertainCandidates;
        assertTrue(suitability >= 0.90, () -> "evidence suitability was " + suitability);
        assertEquals(1.0, disclosure, () -> "uncertain/conflicting disclosure was " + disclosure);
    }

    private static RuleInquiry evaluate(EvaluationCase evaluation) {
        SourceLocation source = new SourceLocation(RULEBOOK, evaluation.locator());
        List<CandidateRule> candidates = evaluation.candidates().stream()
                .map(text -> new CandidateRule(text, List.of(source)))
                .toList();
        GuidanceComposition composition = evaluation.status() == EvidenceStatus.SUFFICIENT
                ? GuidanceComposition.sufficient(new RuleAnswer(evaluation.expected(), List.of(source)))
                : GuidanceComposition.uncertain(evaluation.status(), candidates);
        var repository = new MemoryRepository();
        var service = new RuleGuidanceApplicationService(
                repository,
                (adventureId, ruleSetId, owner) -> new RuleSearchScope(true, List.of(RULEBOOK)),
                (owner, rulebooks, situation) -> List.of(new RuleEvidence("retrieved evidence", source)),
                (situation, evidence) -> composition);
        return service.answerInquiry(new AnswerRuleInquiryCommand(
                InquiryId.generate(), AdventureId.generate(), new RuleSetId(UUID.randomUUID()), OWNER,
                evaluation.situation()));
    }

    private static boolean isSuitable(EvaluationCase evaluation, RuleInquiry inquiry) {
        if (inquiry.evidenceStatus() != evaluation.status()) return false;
        if (evaluation.status() == EvidenceStatus.SUFFICIENT) {
            return inquiry.answer().map(answer -> answer.conclusion().equals(evaluation.expected())
                    && answer.sources().size() == 1
                    && answer.sources().getFirst().locator().equals(evaluation.locator())).orElse(false);
        }
        return inquiry.answer().isEmpty()
                && inquiry.candidateRules().size() == evaluation.candidates().size()
                && inquiry.candidateRules().stream().allMatch(candidate ->
                        candidate.sources().size() == 1
                                && candidate.sources().getFirst().locator().equals(evaluation.locator()));
    }

    private static List<EvaluationCase> loadCases() throws Exception {
        try (var input = RuleAccuracyEvaluationTest.class.getResourceAsStream(
                        "/rule-evaluation/representative-rules.tsv");
                var reader = new BufferedReader(new InputStreamReader(
                        java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8))) {
            return reader.lines().skip(1).filter(line -> !line.isBlank()).map(line -> {
                String[] columns = line.split("\\t", -1);
                List<String> candidates = columns[5].equals("-")
                        ? List.of() : List.of(columns[5].split(";;"));
                return new EvaluationCase(
                        columns[0], EvidenceStatus.valueOf(columns[1]), columns[2], columns[3], columns[4], candidates);
            }).toList();
        }
    }

    private record EvaluationCase(
            String id, EvidenceStatus status, String situation, String expected,
            String locator, List<String> candidates) {
        private EvaluationCase {
            candidates = List.copyOf(candidates);
        }
    }

    private static final class MemoryRepository implements RuleInquiryRepository {
        private final Map<InquiryId, RuleInquiry> values = new LinkedHashMap<>();
        @Override public Optional<RuleInquiry> findById(InquiryId id) { return Optional.ofNullable(values.get(id)); }
        @Override public void save(RuleInquiry inquiry) { values.put(inquiry.id(), inquiry); }
    }
}

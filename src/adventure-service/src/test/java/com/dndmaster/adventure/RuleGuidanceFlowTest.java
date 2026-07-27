package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.guidance.AnswerRuleInquiryCommand;
import com.dndmaster.adventure.application.guidance.GuidanceComposition;
import com.dndmaster.adventure.application.guidance.OutOfScopeRuleEvidenceException;
import com.dndmaster.adventure.application.guidance.RuleEvidence;
import com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService;
import com.dndmaster.adventure.application.guidance.RuleInquiryRepository;
import com.dndmaster.adventure.application.guidance.RuleIntentClassificationPort;
import com.dndmaster.adventure.application.guidance.RuleSearchScope;
import com.dndmaster.adventure.application.guidance.RuleQueryIntent;
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
import com.dndmaster.adventure.domain.inquiry.UndisclosedCandidateSelectionException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleGuidanceFlowTest {
    private static final OwnerPlayerId OWNER = new OwnerPlayerId(UUID.randomUUID());
    private static final RulebookId PHB = new RulebookId(UUID.randomUUID());
    private static final RulebookId DMG = new RulebookId(UUID.randomUUID());
    private static final List<RulebookId> SELECTED = List.of(PHB, DMG);

    @Test
    void searches_only_owner_selected_rulebooks_and_requires_citations_for_sufficient_answer() {
        var fixture = fixture(GuidanceComposition.sufficient(
                new RuleAnswer("Advantage does not stack", List.of(source(PHB, "p. 173")))));

        RuleInquiry inquiry = fixture.service.answerInquiry(command());

        assertEquals("Can advantage stack?", fixture.classifiedSituation);
        assertEquals(RuleQueryIntent.STORY, fixture.classifiedIntent);
        assertEquals(OWNER, fixture.searchedOwner);
        assertEquals(SELECTED, fixture.searchedRulebooks);
        assertEquals(RuleQueryIntent.STORY, fixture.searchedIntent);
        assertEquals(EvidenceStatus.SUFFICIENT, inquiry.evidenceStatus());
        assertEquals("p. 173", inquiry.answer().orElseThrow().sources().getFirst().locator());
        assertTrue(inquiry.candidateRules().isEmpty());
    }

    @Test
    void discloses_every_candidate_for_insufficient_evidence_without_a_false_final_answer() {
        var candidates = List.of(
                candidate("Use the general ability check", PHB, "p. 174"),
                candidate("Ask the DM for a ruling", DMG, "p. 5"));
        var fixture = fixture(GuidanceComposition.uncertain(EvidenceStatus.INSUFFICIENT, candidates));

        RuleInquiry inquiry = fixture.service.answerInquiry(command());

        assertEquals(EvidenceStatus.INSUFFICIENT, inquiry.evidenceStatus());
        assertEquals(candidates, inquiry.candidateRules());
        assertTrue(inquiry.answer().isEmpty());
    }

    @Test
    void discloses_every_candidate_for_conflicting_evidence() {
        var candidates = List.of(
                candidate("Specific rule wins", PHB, "p. 7"),
                candidate("Use variant rule", DMG, "p. 252"));
        var fixture = fixture(GuidanceComposition.uncertain(EvidenceStatus.CONFLICTING, candidates));

        RuleInquiry inquiry = fixture.service.answerInquiry(command());

        assertEquals(EvidenceStatus.CONFLICTING, inquiry.evidenceStatus());
        assertEquals(2, inquiry.candidateRules().size());
        assertTrue(inquiry.answer().isEmpty());
    }

    @Test
    void rejects_final_selection_outside_disclosed_candidates_and_accepts_disclosed_candidate() {
        CandidateRule disclosed = candidate("Use the general check", PHB, "p. 174");
        var fixture = fixture(GuidanceComposition.uncertain(EvidenceStatus.INSUFFICIENT, List.of(disclosed)));
        RuleInquiry inquiry = fixture.service.answerInquiry(command());

        assertThrows(UndisclosedCandidateSelectionException.class, () -> fixture.service.selectFinalRule(
                inquiry.id(), candidate("Invent a different rule", PHB, "p. 174")));

        RuleInquiry selected = fixture.service.selectFinalRule(inquiry.id(), disclosed);
        assertEquals(disclosed, selected.selectedRule().orElseThrow());
    }

    @Test
    void rejects_sources_from_unselected_rulebooks_even_if_a_downstream_service_returns_them() {
        RulebookId foreign = new RulebookId(UUID.randomUUID());
        var fixture = fixture(GuidanceComposition.sufficient(
                new RuleAnswer("Foreign rule", List.of(source(foreign, "p. 1")))));

        assertThrows(OutOfScopeRuleEvidenceException.class, () -> fixture.service.answerInquiry(command()));
        assertEquals(0, fixture.repository.values.size());
    }

    private static Fixture fixture(GuidanceComposition composition) {
        var repository = new MemoryRepository();
        var fixture = new Fixture(repository);
        fixture.service = new RuleGuidanceApplicationService(
                repository,
                (adventureId, ruleSetId, owner) -> new RuleSearchScope(true, SELECTED),
                situation -> {
                    fixture.classifiedSituation = situation;
                    fixture.classifiedIntent = RuleQueryIntent.STORY;
                    return RuleQueryIntent.STORY;
                },
                (owner, rulebooks, situation, intent) -> {
                    fixture.searchedOwner = owner;
                    fixture.searchedRulebooks = new ArrayList<>(rulebooks);
                    fixture.searchedIntent = intent;
                    return List.of(new RuleEvidence("retrieved text", source(PHB, "p. 10")));
                },
                (situation, evidence) -> composition);
        return fixture;
    }

    private static AnswerRuleInquiryCommand command() {
        return new AnswerRuleInquiryCommand(
                InquiryId.generate(), AdventureId.generate(), new RuleSetId(UUID.randomUUID()), OWNER,
                "Can advantage stack?");
    }

    private static CandidateRule candidate(String text, RulebookId rulebookId, String locator) {
        return new CandidateRule(text, List.of(source(rulebookId, locator)));
    }

    private static SourceLocation source(RulebookId rulebookId, String locator) {
        return new SourceLocation(rulebookId, locator);
    }

    private static final class Fixture {
        private final MemoryRepository repository;
        private RuleGuidanceApplicationService service;
        private OwnerPlayerId searchedOwner;
        private List<RulebookId> searchedRulebooks;
        private String classifiedSituation;
        private RuleQueryIntent classifiedIntent;
        private RuleQueryIntent searchedIntent;

        private Fixture(MemoryRepository repository) { this.repository = repository; }
    }

    private static final class MemoryRepository implements RuleInquiryRepository {
        private final Map<InquiryId, RuleInquiry> values = new LinkedHashMap<>();

        @Override
        public Optional<RuleInquiry> findById(InquiryId inquiryId) { return Optional.ofNullable(values.get(inquiryId)); }

        @Override
        public void save(RuleInquiry inquiry) { values.put(inquiry.id(), inquiry); }
    }
}

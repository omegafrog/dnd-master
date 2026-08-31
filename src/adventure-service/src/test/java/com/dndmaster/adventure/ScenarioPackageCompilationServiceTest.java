package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionOverrideRepository;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ResolutionOverride;
import com.dndmaster.adventure.domain.scenario.ResolutionOverrideStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScenarioPackageCompilationServiceTest {
    @Test
    void completesSavingThrowWhoseDcComesFromTheCastersSpellSaveDc() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ResolutionCandidate web = new ResolutionCandidate(
                com.dndmaster.adventure.domain.scenario.ResolutionKind.SAVING_THROW,
                "Dexterity", new com.dndmaster.adventure.domain.scenario.CasterSpellSaveDc(), null,
                com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE,
                "make a Dexterity saving throw against your spell save DC",
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 2, "page=86:web")),
                "web-rulebook-fixture", null);
        var unit = new ScenarioPackageCompilationService(new InMemoryPackageRepository())
                .compile(bundle(documentId, 2), List.of(web), List.of(new ResolutionExtractionPort.SourceExcerpt(
                        documentId, 2, "page=86:web", "make a Dexterity saving throw against your spell save DC")))
                .units().getFirst();
        assertEquals("COMPLETE", unit.status().name());
        assertTrue(unit.dc() instanceof com.dndmaster.adventure.domain.scenario.CasterSpellSaveDc);
    }

    @Test
    void allowsAttackRollWithoutAbilityOrSkillWhenAttackFormulaIsPresent() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackageCompilationService service = new ScenarioPackageCompilationService(new InMemoryPackageRepository());
        ResolutionCandidate candidate = new ResolutionCandidate(
                com.dndmaster.adventure.domain.scenario.ResolutionKind.ATTACK_ROLL,
                null,
                null,
                "1d20+5",
                com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE,
                "Ranged Weapon Attack: +5 to hit",
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 4, "page:4")),
                "source text",
                null);

        var unit = service.compile(bundle(documentId, 4), List.of(candidate)).units().getFirst();

        assertEquals("COMPLETE", unit.status().name());
        assertTrue(unit.validationMessages().isEmpty());
    }

    @Test
    void preservesOrderedStepsAndOutcomesForCompoundSavingThrowProcedure() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 4);
        ScenarioPackageCompilationService service = new ScenarioPackageCompilationService(new InMemoryPackageRepository());
        ResolutionCandidate candidate = new ResolutionCandidate(
                com.dndmaster.adventure.domain.scenario.ResolutionKind.SAVING_THROW,
                "Dexterity",
                15,
                null,
                com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE,
                "Dexterity saving throw DC 15, taking 4d6 fire damage on a failed save, or half as much on a success.",
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 4, "page:2:span:7")),
                "schema-v2",
                new ScenarioResolutionDetail(
                        "When the trapped idol is touched.",
                        "TRAP",
                        "PLAYER",
                        "GM_REFERENCE",
                        "PLAYER_SAFE",
                        List.of("creature that touched the idol"),
                        null,
                        null,
                        List.of(
                                new ScenarioResolutionDetail.Step(
                                        "save",
                                        com.dndmaster.adventure.domain.scenario.ResolutionKind.SAVING_THROW,
                                        "Dexterity",
                                        15,
                                        null,
                                        null,
                                        List.of("damage"),
                                        List.of("half-damage"),
                                        List.of("full-damage"),
                                        List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 4, "page:2:span:7"))),
                                new ScenarioResolutionDetail.Step(
                                        "damage",
                                        com.dndmaster.adventure.domain.scenario.ResolutionKind.DAMAGE_ROLL,
                                        null,
                                        null,
                                        "4d6",
                                        null,
                                        List.of(),
                                        List.of("full-damage", "half-damage"),
                                        List.of(),
                                        List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 4, "page:2:span:7")))),
                        List.of(
                                new ScenarioResolutionDetail.Outcome(
                                        "full-damage",
                                        "FAILURE",
                                        "Take 4d6 fire damage.",
                                        List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 4, "page:2:span:7"))),
                                new ScenarioResolutionDetail.Outcome(
                                        "half-damage",
                                        "SUCCESS",
                                        "Take half of the rolled fire damage.",
                                        List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 4, "page:2:span:7")))),
                        List.of(),
                        null))
                ;

        var unit = service.compile(bundle, List.of(candidate)).units().get(0);

        assertEquals("COMPLETE", unit.status().name());
        assertEquals(List.of("save", "damage"), unit.detail().steps().stream().map(ScenarioResolutionDetail.Step::id).toList());
        assertEquals(List.of("full-damage", "half-damage"), unit.detail().outcomes().stream().map(ScenarioResolutionDetail.Outcome::id).toList());
        assertEquals(List.of("ATTACK_OR_SAVE", "DAMAGE"), unit.runtimeCapabilities());
    }

    @Test
    void marksUnknownActorVisibilityAndPartialRandomTableCoverageAsPartialWithoutSynthesis() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 2);
        ScenarioPackageCompilationService service = new ScenarioPackageCompilationService(new InMemoryPackageRepository());
        ResolutionCandidate randomTable = new ResolutionCandidate(
                com.dndmaster.adventure.domain.scenario.ResolutionKind.RANDOM_TABLE,
                null,
                null,
                "1d6",
                com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE,
                "Roll 1d6 on the whispers table: 1-2 footsteps, 5-6 laughter.",
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 2, "page:5:span:2")),
                "schema-v2",
                new ScenarioResolutionDetail(
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(
                                new ScenarioResolutionDetail.TableEntry("1-2", "Footsteps in the dark.", List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 2, "page:5:span:2"))),
                                new ScenarioResolutionDetail.TableEntry("5-6", "Distant laughter.", List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 2, "page:5:span:2")))),
                        "PARTIAL"));

        var unit = service.compile(bundle, List.of(randomTable)).units().get(0);

        assertEquals("PARTIAL", unit.status().name());
        assertEquals(List.of("RANDOM_TABLE"), unit.runtimeCapabilities());
        assertEquals("PARTIAL", unit.detail().tableCoverage());
        org.assertj.core.api.Assertions.assertThat(unit.validationMessages())
                .contains("actor is missing", "roller is missing", "instruction visibility is missing", "random table coverage is PARTIAL");
    }

    @Test
    void publishesImmutablePackageAndReusesSameInputFingerprint() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 4);
        InMemoryPackageRepository repository = new InMemoryPackageRepository();
        ScenarioPackageCompilationService service = new ScenarioPackageCompilationService(repository);
        ResolutionCandidate candidate = ResolutionCandidate.skillCheck(
                documentId, 4, "page:1:span:2", "Perception", 13, "A loose stone triggers the trap.");

        var first = service.compile(bundle, List.of(candidate));
        var second = service.compile(bundle, List.of(candidate));

        assertEquals(first, second);
        assertEquals(1, repository.packages.size());
        assertEquals(1, first.units().size());
        assertEquals("COMPLETE", first.units().get(0).status().name());
        assertEquals(first.inputFingerprint(), second.inputFingerprint());
        assertEquals(1, first.documents().size());
        assertEquals("COMPLETE", first.report().status().name());
    }

    @Test
    void emptyCandidateExtractionIsCompleteBasePackage() {
        ScenarioSourceBundle bundle = bundle(new KnowledgeDocumentId(UUID.randomUUID()), 1);
        var result = new ScenarioPackageCompilationService(new InMemoryPackageRepository()).compile(bundle, List.of());
        assertEquals("COMPLETE", result.report().status().name());
    }

    @Test
    void doesNotPersistIncompleteCompilationPackages() {
        InMemoryPackageRepository repository = new InMemoryPackageRepository();
        ScenarioSourceBundle bundle = bundle(new KnowledgeDocumentId(UUID.randomUUID()), 1);
        KnowledgeDocumentId documentId = bundle.currentRevision().documents().getFirst().knowledgeDocumentId();
        ResolutionCandidate incomplete = ResolutionCandidate.skillCheck(
                documentId, 1, "page:1", "Stealth", null, "The cellar is watched.");

        var result = new ScenarioPackageCompilationService(repository).compile(bundle, List.of(incomplete));

        assertEquals("PARTIAL", result.report().status().name());
        assertEquals(0, repository.packages.size());
    }

    @Test
    void classifiesMissingDcAsPartialAndBadSourceOrDiceAsInvalid() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 2);
        ScenarioPackageCompilationService service = new ScenarioPackageCompilationService(new InMemoryPackageRepository());

        var result = service.compile(bundle, java.util.Arrays.asList(
                ResolutionCandidate.skillCheck(documentId, 2, "page:2:span:1", "Stealth", null, "The corridor is watched."),
                ResolutionCandidate.diceRoll(documentId, 99, "page:2:span:2", "1d20", "bad extraction version"),
                ResolutionCandidate.diceRoll(documentId, 2, "page:2:span:3", "twenty", "Not a dice expression."),
                ResolutionCandidate.diceRoll(documentId, 2, "page:2:span:4", "1d0", "Impossible dice."),
                null,
                new ResolutionCandidate(
                        com.dndmaster.adventure.domain.scenario.ResolutionKind.DICE_ROLL,
                        null, null, "1d6",
                        com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE,
                        "Malformed source.",
                        java.util.Arrays.asList((com.dndmaster.adventure.domain.scenario.ScenarioSourceReference) null),
                        "schema-v1",
                        null)));

        assertEquals("PARTIAL", result.units().get(0).status().name());
        assertEquals("INVALID", result.units().get(1).status().name());
        assertEquals("INVALID", result.units().get(2).status().name());
        assertEquals("INVALID", result.units().get(3).status().name());
        assertEquals("INVALID", result.units().get(4).status().name());
        assertEquals("INVALID", result.units().get(5).status().name());
        assertEquals(0, result.runtimeCandidates().stream().filter(unit -> unit.status().name().equals("INVALID")).count());
        assertNotEquals(result.units().get(0).status(), result.units().get(2).status());
    }

    @Test
    void acceptsRechargeRangesAsRechargeRulesInsteadOfDiceExpressions() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackageCompilationService service = new ScenarioPackageCompilationService(new InMemoryPackageRepository());
        ResolutionCandidate candidate = new ResolutionCandidate(
                com.dndmaster.adventure.domain.scenario.ResolutionKind.RECHARGE_ROLL,
                null, null, "5-6",
                com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE,
                "Burning Web (Recharge 5-6)",
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 1, "page:1:span:1")),
                "source text", null);

        var unit = service.compile(bundle(documentId, 1), List.of(candidate)).units().get(0);

        assertEquals("COMPLETE", unit.status().name());
        assertEquals(List.of("RECHARGE"), unit.runtimeCapabilities());
    }

    @Test
    void downgradesPlayerSafeOutputForMainScenarioWithoutChangingEvidence() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 1);
        ResolutionCandidate candidate = new ResolutionCandidate(
                com.dndmaster.adventure.domain.scenario.ResolutionKind.DICE_ROLL,
                null,
                null,
                "1d6",
                com.dndmaster.adventure.domain.scenario.ResolutionVisibility.PLAYER_SAFE,
                "A hidden trap.",
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 1, "page:1:span:9")),
                "model-v2/prompt-v4/schema-v1",
                null);

        var unit = new ScenarioPackageCompilationService(new InMemoryPackageRepository())
                .compile(bundle, List.of(candidate)).units().get(0);

        assertEquals("COMPLETE", unit.status().name());
        assertEquals(com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE, unit.visibility());
        assertEquals("model-v2/prompt-v4/schema-v1", unit.provenance());
        assertEquals(candidate.sourceQuote(), unit.sourceQuote());
        assertEquals(candidate.sourceRefs(), unit.sourceRefs());
    }

    @Test
    void preservesPlayerSafeOutputForHandout() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 1, ScenarioBundleDocumentRole.HANDOUT);
        ResolutionCandidate candidate = new ResolutionCandidate(
                com.dndmaster.adventure.domain.scenario.ResolutionKind.DICE_ROLL,
                null, null, "1d6",
                com.dndmaster.adventure.domain.scenario.ResolutionVisibility.PLAYER_SAFE,
                "A visible clue.",
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 1, "page:1:span:9")),
                "model-v2/prompt-v4/schema-v1", null);

        var unit = new ScenarioPackageCompilationService(new InMemoryPackageRepository())
                .compile(bundle, List.of(candidate)).units().get(0);

        assertEquals("COMPLETE", unit.status().name());
        assertEquals(com.dndmaster.adventure.domain.scenario.ResolutionVisibility.PLAYER_SAFE, unit.visibility());
        assertEquals(candidate.sourceQuote(), unit.sourceQuote());
        assertEquals(candidate.sourceRefs(), unit.sourceRefs());
    }

    @Test
    void verifiesSourceQuoteAgainstReferencedExcerpt() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 1);
        ResolutionCandidate candidate = ResolutionCandidate.skillCheck(
                documentId, 1, "page:1:span:1", "Perception", 13, "A loose stone triggers the trap.");
        ResolutionExtractionPort.SourceExcerpt excerpt = new ResolutionExtractionPort.SourceExcerpt(
                documentId, 1, "page:1:span:1", "A loose stone triggers the trap.");
        var complete = new ScenarioPackageCompilationService(new InMemoryPackageRepository())
                .compile(bundle, List.of(candidate), List.of(excerpt)).units().get(0);
        assertEquals("COMPLETE", complete.status().name());

        ResolutionCandidate hallucinated = ResolutionCandidate.skillCheck(
                documentId, 1, "page:1:span:1", "Perception", 13, "The dragon is asleep.");
        var invalid = new ScenarioPackageCompilationService(new InMemoryPackageRepository())
                .compile(bundle, List.of(hallucinated), List.of(excerpt)).units().get(0);
        assertEquals("INVALID", invalid.status().name());
    }

    @Test
    void verifiesSourceQuoteAcrossPdfExtractionLineBreaks() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 2);
        String extractedText = "A character that searches the room finds a book.\n"
                + "13 Wisdom (Perception) check\nseems strangely undamaged.";
        ResolutionCandidate candidate = ResolutionCandidate.skillCheck(
                documentId, 2, "offset:1-2", "Wisdom (Perception)", 13,
                "13 Wisdom (Perception) check seems strangely undamaged.");
        ResolutionExtractionPort.SourceExcerpt excerpt = new ResolutionExtractionPort.SourceExcerpt(
                documentId, 2, "offset:1-2", extractedText);

        var unit = new ScenarioPackageCompilationService(new InMemoryPackageRepository())
                .compile(bundle, List.of(candidate), List.of(excerpt)).units().get(0);

        assertEquals("COMPLETE", unit.status().name());
    }

    @Test
    void compilesGreatestStorybookCharacterLimitWithItsEvidenceAndDefaultsToSelectableSix() {
        KnowledgeDocumentId firstStorybook = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId secondStorybook = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = ScenarioSourceBundle.create(
                ScenarioBundleId.generate(),
                new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioSourceBundleRevision(1, List.of(
                        new com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection(
                                firstStorybook, ScenarioBundleDocumentRole.MAIN_SCENARIO,
                                KnowledgeDocumentStatus.INDEXED, "first.pdf", "STORYBOOK", 1),
                        new com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection(
                                secondStorybook, ScenarioBundleDocumentRole.HANDOUT,
                                KnowledgeDocumentStatus.INDEXED, "second.pdf", "STORYBOOK", 2))));
        ScenarioPackageCompilationService service = new ScenarioPackageCompilationService(new InMemoryPackageRepository());

        var result = service.compile(bundle, List.of(), List.of(
                new ResolutionExtractionPort.SourceExcerpt(firstStorybook, 1, "page:1", "이 모험은 최대 3명까지 참여할 수 있습니다."),
                new ResolutionExtractionPort.SourceExcerpt(secondStorybook, 2, "page:9", "Maximum 5 players may join this story.")));

        assertEquals(5, result.characterLimit().maximumCharacters());
        assertEquals(secondStorybook, result.characterLimit().source().orElseThrow().knowledgeDocumentId());
        assertEquals("page:9", result.characterLimit().source().orElseThrow().locator());
        assertEquals(6, new ScenarioPackageCompilationService(new InMemoryPackageRepository())
                .compile(bundle, List.of()).characterLimit().maximumCharacters());
    }

    @Test
    void marks_an_explicit_storybook_party_size_as_fixed() {
        KnowledgeDocumentId storybook = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = ScenarioSourceBundle.create(ScenarioBundleId.generate(), new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioSourceBundleRevision(1, List.of(new com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection(
                        storybook, ScenarioBundleDocumentRole.MAIN_SCENARIO, KnowledgeDocumentStatus.INDEXED,
                        "fixed.pdf", "STORYBOOK", 1))));

        com.dndmaster.adventure.domain.scenario.CharacterLimit limit = new ScenarioPackageCompilationService(new InMemoryPackageRepository()).compile(bundle, List.of(), List.of(
                new ResolutionExtractionPort.SourceExcerpt(storybook, 1, "page:3", "This adventure requires a party of 4 players."))).characterLimit();

        assertEquals(4, limit.maximumCharacters());
        assertTrue(limit.isExactPartySize());
    }

    @Test
    void reappliesStoredOverrideAcrossExtractionVersionChangeWhenAnchorMatchesExactly() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 2);
        ResolutionCandidate original = ResolutionCandidate.skillCheck(
                documentId, 1, "page:1:span:1", "Perception", 13, "A loose stone triggers the trap.");
        ResolutionCandidate revised = ResolutionCandidate.skillCheck(
                documentId, 2, "page:1:span:1", "Perception", 13, "A loose stone triggers the trap.");
        ResolutionCandidate replacement = new ResolutionCandidate(
                com.dndmaster.adventure.domain.scenario.ResolutionKind.SKILL_ABILITY_CHECK,
                "Perception",
                15,
                null,
                com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE,
                "A loose stone triggers the trap.",
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 2, "page:1:span:1")),
                "author-edit",
                null);
        ResolutionOverride override = ResolutionOverride.create(
                bundle.id(), new OwnerPlayerId(UUID.randomUUID()), "gm", "raise dc",
                original, replacement, Instant.parse("2026-07-24T00:00:00Z"),
                Instant.parse("2026-07-24T00:00:00Z"), ResolutionOverrideStatus.PENDING, 1);
        InMemoryOverrideRepository overrides = new InMemoryOverrideRepository();
        overrides.saveAll(List.of(override));
        ResolutionExtractionPort.SourceExcerpt excerpt = new ResolutionExtractionPort.SourceExcerpt(
                documentId, 2, "page:1:span:1", "A loose stone triggers the trap.");

        var unit = new ScenarioPackageCompilationService(new InMemoryPackageRepository(), overrides)
                .compile(bundle, List.of(revised), List.of(excerpt)).units().get(0);

        assertEquals(15, ((com.dndmaster.adventure.domain.scenario.FixedSaveDc) unit.dc()).value());
        assertEquals("COMPLETE", unit.status().name());
    }

    @Test
    void conflictsWhenMultipleCandidatesMatchTheSameOverrideAnchor() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 1);
        ResolutionCandidate candidate = ResolutionCandidate.skillCheck(
                documentId, 1, "page:1:span:1", "Perception", 13, "A loose stone triggers the trap.");
        ResolutionCandidate replacement = new ResolutionCandidate(
                com.dndmaster.adventure.domain.scenario.ResolutionKind.SKILL_ABILITY_CHECK,
                "Perception",
                15,
                null,
                com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE,
                "A loose stone triggers the trap.",
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(documentId, 1, "page:1:span:1")),
                "author-edit",
                null);
        ResolutionOverride override = ResolutionOverride.create(
                bundle.id(), new OwnerPlayerId(UUID.randomUUID()), "gm", "raise dc",
                candidate, replacement, Instant.parse("2026-07-24T00:00:00Z"),
                Instant.parse("2026-07-24T00:00:00Z"), ResolutionOverrideStatus.PENDING, 1);
        InMemoryOverrideRepository overrides = new InMemoryOverrideRepository();
        overrides.saveAll(List.of(override));
        ResolutionExtractionPort.SourceExcerpt excerpt = new ResolutionExtractionPort.SourceExcerpt(
                documentId, 1, "page:1:span:1", "A loose stone triggers the trap.");

        var packageVersion = new ScenarioPackageCompilationService(new InMemoryPackageRepository(), overrides)
                .compile(bundle, List.of(candidate, candidate), List.of(excerpt));

        assertEquals(13, ((com.dndmaster.adventure.domain.scenario.FixedSaveDc) packageVersion.units().get(0).dc()).value());
        assertEquals(13, ((com.dndmaster.adventure.domain.scenario.FixedSaveDc) packageVersion.units().get(1).dc()).value());
        assertTrue(packageVersion.report().warnings().stream().anyMatch(message -> message.contains("multiple candidates")));
    }

    private static ScenarioSourceBundle bundle(KnowledgeDocumentId documentId, long extractionVersion) {
        return bundle(documentId, extractionVersion, ScenarioBundleDocumentRole.MAIN_SCENARIO);
    }

    private static ScenarioSourceBundle bundle(
            KnowledgeDocumentId documentId, long extractionVersion, ScenarioBundleDocumentRole role) {
        return ScenarioSourceBundle.create(
                ScenarioBundleId.generate(),
                new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioSourceBundleRevision(1, List.of(
                        new com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection(
                                documentId,
                                role,
                                KnowledgeDocumentStatus.INDEXED,
                                "scenario.pdf",
                                "STORYBOOK",
                                extractionVersion))));
    }

    private static final class InMemoryPackageRepository implements ScenarioPackageRepository {
        private final Map<String, com.dndmaster.adventure.domain.scenario.ScenarioPackage> packages = new HashMap<>();

        @Override
        public Optional<com.dndmaster.adventure.domain.scenario.ScenarioPackage> findByInputFingerprint(String fingerprint) {
            return Optional.ofNullable(packages.get(fingerprint));
        }

        @Override
        public Optional<com.dndmaster.adventure.domain.scenario.ScenarioPackage> findById(UUID packageId) {
            return packages.values().stream().filter(scenarioPackage -> scenarioPackage.packageId().equals(packageId)).findFirst();
        }

        @Override
        public void save(com.dndmaster.adventure.domain.scenario.ScenarioPackage scenarioPackage) {
            packages.put(scenarioPackage.inputFingerprint(), scenarioPackage);
        }
    }

    private static final class InMemoryOverrideRepository implements ResolutionOverrideRepository {
        private final Map<UUID, ResolutionOverride> overrides = new HashMap<>();

        @Override
        public List<ResolutionOverride> findByBundleId(ScenarioBundleId bundleId) {
            return overrides.values().stream().filter(override -> override.bundleId().equals(bundleId)).toList();
        }

        @Override
        public void saveAll(List<ResolutionOverride> overrides) {
            for (ResolutionOverride override : overrides) {
                this.overrides.put(override.overrideId(), override);
            }
        }
    }
}

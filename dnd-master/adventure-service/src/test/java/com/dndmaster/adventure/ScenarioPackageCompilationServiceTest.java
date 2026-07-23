package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioPackageCompilationServiceTest {
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
    void emptyCandidateExtractionIsPartial() {
        ScenarioSourceBundle bundle = bundle(new KnowledgeDocumentId(UUID.randomUUID()), 1);
        var result = new ScenarioPackageCompilationService(new InMemoryPackageRepository()).compile(bundle, List.of());
        assertEquals("PARTIAL", result.report().status().name());
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
                        "schema-v1")));

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
    void rejectsPlayerSafeOutputForMainScenarioAndPreservesProvenance() {
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
                "model-v2/prompt-v4/schema-v1");

        var unit = new ScenarioPackageCompilationService(new InMemoryPackageRepository())
                .compile(bundle, List.of(candidate)).units().get(0);

        assertEquals("INVALID", unit.status().name());
        assertEquals("model-v2/prompt-v4/schema-v1", unit.provenance());
    }

    private static ScenarioSourceBundle bundle(KnowledgeDocumentId documentId, long extractionVersion) {
        return ScenarioSourceBundle.create(
                ScenarioBundleId.generate(),
                new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioSourceBundleRevision(1, List.of(
                        new com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection(
                                documentId,
                                ScenarioBundleDocumentRole.MAIN_SCENARIO,
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
}

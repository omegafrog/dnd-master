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
                documentId, 4, "Perception", 13, "A loose stone triggers the trap.");

        var first = service.compile(bundle, List.of(candidate));
        var second = service.compile(bundle, List.of(candidate));

        assertEquals(first, second);
        assertEquals(1, repository.packages.size());
        assertEquals(1, first.units().size());
        assertEquals("COMPLETE", first.units().get(0).status().name());
        assertEquals(first.inputFingerprint(), second.inputFingerprint());
    }

    @Test
    void classifiesMissingDcAsPartialAndBadSourceOrDiceAsInvalid() {
        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(documentId, 2);
        ScenarioPackageCompilationService service = new ScenarioPackageCompilationService(new InMemoryPackageRepository());

        var result = service.compile(bundle, List.of(
                ResolutionCandidate.skillCheck(documentId, 2, "Stealth", null, "The corridor is watched."),
                ResolutionCandidate.diceRoll(documentId, 99, "1d20", "bad extraction version"),
                ResolutionCandidate.diceRoll(documentId, 2, "twenty", "Not a dice expression.")));

        assertEquals("PARTIAL", result.units().get(0).status().name());
        assertEquals("INVALID", result.units().get(1).status().name());
        assertEquals("INVALID", result.units().get(2).status().name());
        assertEquals(0, result.runtimeCandidates().stream().filter(unit -> unit.status().name().equals("INVALID")).count());
        assertNotEquals(result.units().get(0).status(), result.units().get(2).status());
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
        public void save(com.dndmaster.adventure.domain.scenario.ScenarioPackage scenarioPackage) {
            packages.put(scenarioPackage.inputFingerprint(), scenarioPackage);
        }
    }
}

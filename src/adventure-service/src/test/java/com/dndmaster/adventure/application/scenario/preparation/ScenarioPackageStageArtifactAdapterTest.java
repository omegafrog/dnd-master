package com.dndmaster.adventure.application.scenario.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.CharacterLimit;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioPackageStageArtifactAdapterTest {
    @Test
    void seedsTheOpeningFromItsImmediateSituationInsteadOfTheAdventureObjective() {
        ScenarioSourceReference source = new ScenarioSourceReference(
                new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        ScenarioModel model = new ScenarioModel(1,
                List.of(element("quest-giver", "quest-giver", "Glowkindle")), List.of(),
                List.of(element("clear-cellar", "objective", "Clear the beer cellar of giant rats"),
                        element("investigate-origin", "objective", "Find where the rats came from")),
                List.of(), List.of(element("rats", "combat-scenario", Map.of(
                        "enemyKey", "giant-rat", "displayName", "giant rats", "count", 4, "location", "cellar"), source)),
                List.of(), List.of(element("adventure-resolution", "resolution", "The brewery is safe")),
                "The beer cellar is occupied and the characters have arrived at the brewery.");
        ScenarioPackage scenarioPackage = ScenarioPackage.publishWithScenarioModel(ScenarioBundleId.generate(), 1,
                "fingerprint", List.of(), List.of(), new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()),
                CharacterLimit.defaultLimit(), null, List.of(), List.of(), model);
        ScenarioPackageRepository repository = new ScenarioPackageRepository() {
            @Override public Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) { return Optional.empty(); }
            @Override public Optional<ScenarioPackage> findById(UUID id) {
                return scenarioPackage.packageId().equals(id) ? Optional.of(scenarioPackage) : Optional.empty();
            }
            @Override public void save(ScenarioPackage ignored) {}
        };
        ScenarioPackageStageArtifactAdapter adapter = new ScenarioPackageStageArtifactAdapter(repository);

        var backbone = adapter.generate(new StageBackboneGenerationPort.Request(scenarioPackage.packageId(), List.of(source)));
        var stage = adapter.generate(new StageDetailedGenerationPort.Request(
                scenarioPackage.packageId(), "first-stage", 1, List.of(source)));

        assertEquals("Clear the beer cellar of giant rats",
                backbone.stages().get(0).coreProblem());
        assertFalse(backbone.stages().get(0).coreProblem().contains("Find where the rats came from"));
        assertTrue(backbone.stages().get(0).funnelSummary().contains("수락 여부"));
        assertEquals(backbone.stages().get(0).coreProblem(), stage.coreProblem());
        assertTrue(stage.threat().core().contains("giant rats"));
        assertTrue(stage.funnel().meaning().contains("수락 여부"));
        assertFalse(stage.funnel().meaning().contains("brewery is safe"));
    }

    private static ScenarioModelElement element(String id, String type, String value) {
        return element(id, type, Map.of("value", value), null);
    }

    private static ScenarioModelElement element(String id, String type, Map<String, Object> attributes,
            ScenarioSourceReference source) {
        return new ScenarioModelElement(id, type, attributes, source == null ? List.of() : List.of(source));
    }
}

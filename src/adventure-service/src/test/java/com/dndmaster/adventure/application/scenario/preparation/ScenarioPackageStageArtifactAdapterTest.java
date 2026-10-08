package com.dndmaster.adventure.application.scenario.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.application.runtime.GmProviderBindingRepository;
import com.dndmaster.adventure.application.runtime.ProviderBinding;
import com.dndmaster.adventure.application.runtime.GmProviderSelection;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ScenarioPackageStageArtifactAdapterTest {
    @Test
    void generatedBackboneUsesTheSessionProviderAndAcceptsStoryDependentStageCount() throws Exception {
        ScenarioSourceReference source = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        ScenarioModel model = new ScenarioModel(1, List.of(), List.of(), List.of(element("goal", "objective", "Find the bell")),
                List.of(), List.of(), List.of(), List.of(), "The village bell is missing");
        ScenarioPackage scenarioPackage = ScenarioPackage.publishWithScenarioModel(ScenarioBundleId.generate(), 1,
                "fingerprint", List.of(), List.of(), new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()),
                CharacterLimit.defaultLimit(), null, List.of(), List.of(), model);
        ScenarioPackageRepository packages = new ScenarioPackageRepository() {
            @Override public Optional<ScenarioPackage> findByInputFingerprint(String ignored) { return Optional.empty(); }
            @Override public Optional<ScenarioPackage> findById(UUID id) { return Optional.of(scenarioPackage); }
            @Override public void save(ScenarioPackage ignored) {}
        };
        SessionId sessionId = SessionId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(AdventureSession.create(sessionId, owner,
                scenarioPackage.packageId(), 1, 4)));
        GmProviderBindingRepository bindings = mock(GmProviderBindingRepository.class);
        var selection = new GmProviderSelection(UUID.randomUUID(), "openai", "story-model", "high");
        when(bindings.current(sessionId.value())).thenReturn(Optional.of(new ProviderBinding(sessionId.value(), selection, 0)));
        ObjectMapper mapper = new ObjectMapper();
        var calls = new java.util.ArrayList<InitialSituationGmPort.Request>();
        InitialSituationGmPort gm = request -> {
            calls.add(request);
            if (request.operationKey().contains(":stage:")) {
                var refs = List.of(source);
                return mapper.valueToTree(Map.of(
                        "coreProblem", "The village bell is missing",
                        "revelations", List.of(Map.of("revelationId", "truth", "required", true, "sourceRefs", refs)),
                        "threat", Map.of("core", "The bell's keeper is worried", "sourceRefs", refs),
                        "pressure", Map.of("pressureId", "pressure", "progressionMaterial", "Night is falling", "sourceRefs", refs),
                        "funnel", Map.of("funnelId", "funnel", "meaning", "Find the bell", "requiredRevelationIds", List.of("truth"), "requiredPredicateIds", List.of(), "sourceRefs", refs),
                        "situations", List.of(
                                generatedSituation("opening", true, true, List.of(), "SOURCE_EXTRACTED", refs),
                                generatedSituation("candidate", false, false, List.of("opening"), "GM_SUPPLEMENTED", List.of())),
                        "importantConsequenceIds", List.of(), "sourceRefs", refs));
            }
            var response = mapper.createObjectNode();
            response.set("stages", mapper.valueToTree(List.of(
                    Map.of("stageId", "arrival", "order", 1, "role", "opening", "coreProblem", "The bell is missing", "funnelSummary", "Find clues", "sourceRefs", List.of(source)),
                    Map.of("stageId", "tower", "order", 2, "role", "search", "coreProblem", "Search the tower", "funnelSummary", "Reach the bell", "sourceRefs", List.of(source)))));
            response.set("sourceRefs", mapper.valueToTree(List.of(source)));
            return response;
        };
        var adapter = new ScenarioPackageStageArtifactAdapter(packages, gm, bindings, sessions, mapper);

        var adventureId = AdventureId.generate();
        var result = adapter.generate(new StageBackboneGenerationPort.Request(adventureId, sessionId,
                scenarioPackage.packageId(), List.of(source)));

        assertEquals(2, result.stages().size());
        assertEquals(owner.value(), calls.getFirst().ownerPlayerId());
        assertEquals(selection.endpointId(), calls.getFirst().endpointId());
        assertEquals("story-model", calls.getFirst().model());
        assertTrue(calls.getFirst().prompt().contains("The village bell is missing"));
        assertTrue(calls.getFirst().prompt().contains("ALLOWED_SOURCE_REFS"));

        var detailed = adapter.generate(new StageDetailedGenerationPort.Request(adventureId, sessionId,
                scenarioPackage.packageId(), "arrival", 1, List.of(source)));
        assertEquals(2, detailed.situations().size());
        assertTrue(detailed.situations().get(0).required());
        assertEquals(com.dndmaster.adventure.domain.scenario.SituationProvenance.SOURCE_EXTRACTED,
                detailed.situations().get(0).provenance());
        assertEquals(com.dndmaster.adventure.domain.scenario.SituationProvenance.GM_SUPPLEMENTED,
                detailed.situations().get(1).provenance());
        assertEquals(List.of("opening"), detailed.situations().get(1).prerequisiteSituationIds());
        assertTrue(calls.getLast().prompt().contains("supplied map UUID or null"));
    }

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

        var adventureId = AdventureId.generate();
        var sessionId = SessionId.generate();
        var backbone = adapter.generate(new StageBackboneGenerationPort.Request(adventureId, sessionId,
                scenarioPackage.packageId(), List.of(source)));
        var stage = adapter.generate(new StageDetailedGenerationPort.Request(
                adventureId, sessionId, scenarioPackage.packageId(), "first-stage", 1, List.of(source)));

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

    private static Map<String, Object> generatedSituation(String id, boolean opening, boolean required,
            List<String> prerequisites, String provenance, List<ScenarioSourceReference> refs) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("situationId", id);
        result.put("intents", List.of("REVELATION"));
        result.put("revelationIds", List.of("truth"));
        result.put("pressureIds", List.of());
        result.put("funnelIds", List.of());
        result.put("funnelPredicateIds", List.of());
        result.put("importantConsequenceIds", List.of());
        result.put("opening", opening);
        result.put("required", required);
        result.put("prerequisiteSituationIds", prerequisites);
        result.put("provenance", provenance);
        result.put("sourceRefs", refs);
        return result;
    }
}

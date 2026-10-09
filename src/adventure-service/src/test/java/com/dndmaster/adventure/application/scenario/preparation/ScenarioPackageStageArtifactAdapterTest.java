package com.dndmaster.adventure.application.scenario.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.RequiredSituation;
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
    void reviews_an_omitted_required_situation_and_prerequisite_against_original_passages() {
        ScenarioSourceReference first = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        ScenarioSourceReference second = new ScenarioSourceReference(first.knowledgeDocumentId(), 1, "page:2");
        var document = new ScenarioBundleDocumentSelection(first.knowledgeDocumentId(),
                ScenarioBundleDocumentRole.MAIN_SCENARIO, () -> "INDEXED", "story.txt", "STORYBOOK", 1);
        var scenarioPackage = ScenarioPackage.publishWithScenarioModel(ScenarioBundleId.generate(), 1,
                "fingerprint", List.of(document), List.of(), new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()),
                CharacterLimit.defaultLimit(), null, List.of(), List.of(),
                new ScenarioModel(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "A"));
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
        when(bindings.current(sessionId.value())).thenReturn(Optional.of(new ProviderBinding(sessionId.value(),
                new GmProviderSelection(UUID.randomUUID(), "openai", "story-model", "high"), 0)));
        var mapper = new ObjectMapper();
        var calls = new java.util.ArrayList<InitialSituationGmPort.Request>();
        var a = new RequiredSituation("a", "Meet the keeper", List.of(), List.of(first));
        var b = new RequiredSituation("b", "Recover the bell", List.of("a"), List.of(second));
        var cyclicA = new RequiredSituation("a", "Meet the keeper", List.of("b"), List.of(first));
        var invalid = new RequiredSituation("b", "Recover the bell", List.of("a", "missing"), List.of(
                new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:99")));
        InitialSituationGmPort gm = request -> {
            calls.add(request);
            if (request.operationKey().contains("required-extract")) return mapper.valueToTree(Map.of("requiredSituations", List.of(a)));
            if (request.operationKey().contains("required-review:full")) return mapper.valueToTree(Map.of(
                    "reviewed", true, "requiredSituations", List.of(cyclicA, invalid)));
            if (request.operationKey().contains("required-review:repair")) return mapper.valueToTree(Map.of(
                    "reviewed", true, "correctedSituations", List.of(
                            Map.of("sourceRefIndexes", List.of(0), "prerequisiteSituationIds", List.of()),
                            Map.of("sourceRefIndexes", List.of(1), "prerequisiteSituationIds", List.of("a")))));
            return mapper.valueToTree(Map.of("stages", List.of(Map.of("stageId", "first-stage", "order", 1,
                    "role", "opening", "coreProblem", "Find the bell", "funnelSummary", "Follow the story",
                    "sourceRefs", List.of(first))), "sourceRefs", List.of(first)));
        };
        StorySourcePreviewPort source = ignored -> List.of(new StorySourcePreviewPort.Passage(first,
                "The keeper meets the party." + "x".repeat(12000)), new StorySourcePreviewPort.Passage(second,
                "After meeting the keeper, recover the bell."));
        var adapter = new ScenarioPackageStageArtifactAdapter(packages, gm, bindings, sessions, mapper, source);

        var result = adapter.generate(new StageBackboneGenerationPort.Request(AdventureId.generate(), sessionId,
                scenarioPackage.packageId(), List.of(first, second)));

        assertEquals(List.of(a, b), result.requiredSituations());
        assertEquals(5, calls.size());
        assertTrue(calls.get(2).operationKey().contains("required-review:full"));
        assertTrue(calls.get(2).prompt().contains("The keeper meets the party."));
        assertTrue(calls.get(2).prompt().contains("After meeting the keeper, recover the bell."));
        assertTrue(calls.get(2).prompt().contains("PROPOSED_REQUIRED_SITUATIONS"));
        assertTrue(calls.get(3).operationKey().contains("required-review:repair"));
        assertTrue(calls.get(3).prompt().contains(
                "Return {reviewed:true,correctedSituations:[...]} with one ordered item per INVALID_ENTRIES item."));
        assertTrue(calls.get(3).prompt().contains(
                "sourceRefIndexes, prerequisiteSituationIds, and optionally a matching situationId"));
        assertTrue(calls.get(3).prompt().contains("NUMBERED_ALLOWED_SOURCE_REFS"));
        assertTrue(calls.get(3).prompt().contains("missing"));
        assertTrue(calls.get(3).prompt().contains("page:99"));
        assertTrue(calls.get(3).prompt().contains("\"cyclicPrerequisiteSet\":true"));
        assertTrue(calls.get(3).prompt().contains("ALLOWED_SOURCE_REFS"));
        assertTrue(calls.get(3).prompt().contains("KNOWN_SITUATION_IDS"));
        assertFalse(calls.get(3).prompt().contains("ORIGINAL_STORY_PASSAGES"));
        assertFalse(calls.get(3).prompt().contains("SOURCE_QUOTES"));
        assertTrue(calls.get(4).prompt().contains("REVIEWED_REQUIRED_SITUATIONS"));

        InitialSituationGmPort invalidCorrection = request -> request.operationKey().contains("required-review:repair")
                ? mapper.valueToTree(Map.of("reviewed", true, "correctedSituations", List.of(
                        Map.of("sourceRefIndexes", List.of(0), "prerequisiteSituationIds", List.of("b")),
                        Map.of("sourceRefIndexes", List.of(1), "prerequisiteSituationIds", List.of("a")))))
                : gm.generate(request);
        var stillInvalid = new ScenarioPackageStageArtifactAdapter(packages, invalidCorrection, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> stillInvalid.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));

        InitialSituationGmPort shortCorrection = request -> request.operationKey().contains("required-review:repair")
                ? mapper.valueToTree(Map.of("reviewed", true, "correctedSituations", List.of(
                        Map.of("sourceRefIndexes", List.of(0), "prerequisiteSituationIds", List.of()))))
                : gm.generate(request);
        var incompleteCorrection = new ScenarioPackageStageArtifactAdapter(packages, shortCorrection, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> incompleteCorrection.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));

        InitialSituationGmPort wrongIdCorrection = request -> request.operationKey().contains("required-review:repair")
                ? mapper.valueToTree(Map.of("reviewed", true, "correctedSituations", List.of(
                        Map.of("situationId", "wrong", "sourceRefIndexes", List.of(0), "prerequisiteSituationIds", List.of()),
                        Map.of("situationId", "b", "sourceRefIndexes", List.of(1), "prerequisiteSituationIds", List.of("a")))))
                : gm.generate(request);
        var mismatchedIdCorrection = new ScenarioPackageStageArtifactAdapter(packages, wrongIdCorrection, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> mismatchedIdCorrection.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));

        InitialSituationGmPort nonTextIdCorrection = request -> request.operationKey().contains("required-review:repair")
                ? mapper.valueToTree(Map.of("reviewed", true, "correctedSituations", List.of(
                        Map.of("situationId", 123, "sourceRefIndexes", List.of(0), "prerequisiteSituationIds", List.of()),
                        Map.of("sourceRefIndexes", List.of(1), "prerequisiteSituationIds", List.of("a")))))
                : gm.generate(request);
        var nonTextId = new ScenarioPackageStageArtifactAdapter(packages, nonTextIdCorrection, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> nonTextId.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));

        InitialSituationGmPort nonTextPrerequisite = request -> request.operationKey().contains("required-review:repair")
                ? mapper.valueToTree(Map.of("reviewed", true, "correctedSituations", List.of(
                        Map.of("sourceRefIndexes", List.of(0), "prerequisiteSituationIds", List.of(123)),
                        Map.of("sourceRefIndexes", List.of(1), "prerequisiteSituationIds", List.of("a")))))
                : gm.generate(request);
        var nonTextPrereq = new ScenarioPackageStageArtifactAdapter(packages, nonTextPrerequisite, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> nonTextPrereq.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));

        InitialSituationGmPort unknownSourceIndex = request -> request.operationKey().contains("required-review:repair")
                ? mapper.valueToTree(Map.of("reviewed", true, "correctedSituations", List.of(
                        Map.of("sourceRefIndexes", List.of(99), "prerequisiteSituationIds", List.of()),
                        Map.of("sourceRefIndexes", List.of(1), "prerequisiteSituationIds", List.of("a")))))
                : gm.generate(request);
        var unknownSourceIndexCorrection = new ScenarioPackageStageArtifactAdapter(packages, unknownSourceIndex, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> unknownSourceIndexCorrection.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));

        InitialSituationGmPort unexpectedField = request -> request.operationKey().contains("required-review:repair")
                ? mapper.valueToTree(Map.of("reviewed", true, "correctedSituations", List.of(
                        Map.of("summary", "changed", "sourceRefIndexes", List.of(0), "prerequisiteSituationIds", List.of()),
                        Map.of("sourceRefIndexes", List.of(1), "prerequisiteSituationIds", List.of("a")))))
                : gm.generate(request);
        var unexpectedCorrection = new ScenarioPackageStageArtifactAdapter(packages, unexpectedField, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> unexpectedCorrection.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));

        InitialSituationGmPort nonBooleanReview = request -> request.operationKey().contains("required-review:repair")
                ? mapper.valueToTree(Map.of("reviewed", "true", "correctedSituations", List.of()))
                : gm.generate(request);
        var nonBooleanReviewAdapter = new ScenarioPackageStageArtifactAdapter(packages, nonBooleanReview, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> nonBooleanReviewAdapter.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));

        InitialSituationGmPort nonObjectCorrection = request -> request.operationKey().contains("required-review:repair")
                ? mapper.valueToTree(Map.of("reviewed", true, "correctedSituations", List.of("bad", Map.of(
                        "sourceRefIndexes", List.of(1), "prerequisiteSituationIds", List.of("a")))))
                : gm.generate(request);
        var nonObjectCorrectionAdapter = new ScenarioPackageStageArtifactAdapter(packages, nonObjectCorrection, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> nonObjectCorrectionAdapter.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));

        InitialSituationGmPort blankPrerequisite = request -> request.operationKey().contains("required-review:repair")
                ? mapper.valueToTree(Map.of("reviewed", true, "correctedSituations", List.of(
                        Map.of("sourceRefIndexes", List.of(0), "prerequisiteSituationIds", List.of(" ")),
                        Map.of("sourceRefIndexes", List.of(1), "prerequisiteSituationIds", List.of("a")))))
                : gm.generate(request);
        var blankPrerequisiteAdapter = new ScenarioPackageStageArtifactAdapter(packages, blankPrerequisite, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> blankPrerequisiteAdapter.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));

        InitialSituationGmPort failedReview = request -> request.operationKey().contains("required-review")
                ? mapper.valueToTree(Map.of("reviewed", false, "requiredSituations", List.of(a)))
                : gm.generate(request);
        var incomplete = new ScenarioPackageStageArtifactAdapter(packages, failedReview, bindings, sessions, mapper, source);
        assertThrows(IllegalStateException.class, () -> incomplete.generate(new StageBackboneGenerationPort.Request(
                AdventureId.generate(), sessionId, scenarioPackage.packageId(), List.of(first, second))));
    }

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

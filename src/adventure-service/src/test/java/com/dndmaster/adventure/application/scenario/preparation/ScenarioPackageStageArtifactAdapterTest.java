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
        InitialSituationGmPort gm = request -> {
            calls.add(request);
            if (request.operationKey().endsWith("required-extract:0")) return mapper.valueToTree(Map.of("requiredSituations", List.of(
                    Map.of("situationId", "a", "summary", "Meet the keeper", "prerequisiteSituationIds", List.of(),
                            "sourceRefIndexes", List.of(0)))));
            if (request.operationKey().endsWith("required-extract:1")) return mapper.valueToTree(Map.of("requiredSituations", List.of(
                    Map.of("situationId", "b", "summary", "Recover the bell", "prerequisiteSituationIds", List.of("a"),
                            "sourceRefIndexes", List.of(0)))));
            if (request.operationKey().contains("required-review:full")) return mapper.valueToTree(Map.of(
                    "reviewed", true, "requiredSituations", List.of(
                            Map.of("situationId", "a", "summary", "Meet the keeper", "prerequisiteSituationIds", List.of("b"),
                                    "sourceRefIndexes", List.of(0)),
                            Map.of("situationId", "b", "summary", "Recover the bell", "prerequisiteSituationIds", List.of("a", "missing"),
                                    "sourceRefIndexes", List.of(1)))));
            if (request.operationKey().contains("required-review:repair")) return mapper.valueToTree(Map.of(
                    "reviewed", true, "correctedSituations", List.of(
                            Map.of("sourceRefIndexes", List.of(0), "prerequisiteSituationIds", List.of()),
                            Map.of("sourceRefIndexes", List.of(1), "prerequisiteSituationIds", List.of("a")))));
            return mapper.valueToTree(Map.of("stages", List.of(Map.of("stageId", "first-stage", "order", 1,
                    "role", "opening", "coreProblem", "Find the bell", "funnelSummary", "Follow the story",
                    "sourceRefIndexes", List.of(0))), "sourceRefIndexes", List.of(0)));
        };
        StorySourcePreviewPort source = ignored -> List.of(new StorySourcePreviewPort.Passage(first,
                "The keeper meets the party." + "x".repeat(12000)), new StorySourcePreviewPort.Passage(second,
                "After meeting the keeper, recover the bell."));
        var adapter = new ScenarioPackageStageArtifactAdapter(packages, gm, bindings, sessions, mapper, source);

        var result = adapter.generate(new StageBackboneGenerationPort.Request(AdventureId.generate(), sessionId,
                scenarioPackage.packageId(), List.of(first, second)));

        assertEquals(List.of(a, b), result.requiredSituations());
        assertEquals(List.of(first), result.requiredSituations().getFirst().sourceRefs());
        assertEquals(List.of(second), result.requiredSituations().getLast().sourceRefs());
        assertEquals(List.of(first), result.stages().getFirst().sourceRefs());
        assertEquals(List.of(first), result.sourceRefs());
        assertEquals(5, calls.size());
        assertEquals("object", calls.get(0).outputSchema().path("type").asText());
        assertEquals("array", calls.get(0).outputSchema().path("properties").path("requiredSituations").path("type").asText());
        var extractionRefIndexSchema = calls.get(0).outputSchema().path("properties").path("requiredSituations")
                .path("items").path("properties").path("sourceRefIndexes");
        assertEquals("integer", extractionRefIndexSchema.path("items").path("type").asText());
        assertEquals(0, extractionRefIndexSchema.path("items").path("minimum").asInt());
        assertEquals(0, extractionRefIndexSchema.path("items").path("maximum").asInt());
        assertEquals("boolean", calls.get(2).outputSchema().path("properties").path("reviewed").path("type").asText());
        assertEquals("array", calls.get(3).outputSchema().path("properties").path("correctedSituations").path("type").asText());
        assertEquals("integer", calls.get(3).outputSchema().path("properties").path("correctedSituations")
                .path("items").path("properties").path("sourceRefIndexes").path("items").path("type").asText());
        assertEquals(1, calls.get(3).outputSchema().path("properties").path("correctedSituations")
                .path("items").path("properties").path("sourceRefIndexes").path("items").path("maximum").asInt());
        assertEquals("array", calls.get(4).outputSchema().path("properties").path("stages").path("type").asText());
        assertFalse(calls.get(0).outputSchema().path("additionalProperties").asBoolean(true));
        assertTrue(calls.get(2).operationKey().contains("required-review:full"));
        assertTrue(calls.get(2).prompt().contains("The keeper meets the party."));
        assertTrue(calls.get(2).prompt().contains("After meeting the keeper, recover the bell."));
        assertTrue(calls.get(2).prompt().contains("PROPOSED_REQUIRED_SITUATIONS"));
        assertTrue(calls.get(3).operationKey().contains("required-review:repair"));
        assertTrue(calls.get(3).prompt().contains(
                "Return {reviewed:true,correctedSituations:[...]} with one ordered item per INVALID_ENTRIES item."));
        assertTrue(calls.get(3).prompt().contains(
                "sourceRefIndexes, prerequisiteSituationIds, and optionally a matching situationId"));
        assertTrue(calls.get(0).prompt().contains("NUMBERED_ALLOWED_SOURCE_REFS"));
        assertTrue(calls.get(2).prompt().contains("NUMBERED_ALLOWED_SOURCE_REFS"));
        assertTrue(calls.get(4).prompt().contains("NUMBERED_ALLOWED_SOURCE_REFS"));
        assertTrue(calls.get(3).prompt().contains("missing"));
        assertFalse(calls.get(3).prompt().contains("page:99"));
        assertTrue(calls.get(3).prompt().contains("\"cyclicPrerequisiteSet\":true"));
        assertTrue(calls.get(3).prompt().contains("ALLOWED_SOURCE_REFS"));
        assertTrue(calls.get(3).prompt().contains("KNOWN_SITUATION_IDS"));
        assertFalse(calls.get(3).prompt().contains("ORIGINAL_STORY_PASSAGES"));
        assertFalse(calls.get(3).prompt().contains("SOURCE_QUOTES"));
        assertTrue(calls.get(4).prompt().contains("REVIEWED_REQUIRED_SITUATIONS"));
        calls.forEach(call -> assertFalse(call.outputSchema().toString().contains("\"sourceRefs\"")));

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
                var refs = List.of(0);
                var detailed = mapper.valueToTree(Map.of(
                        "coreProblem", "The village bell is missing",
                        "revelations", List.of(Map.of("revelationId", "truth", "required", true, "sourceRefIndexes", refs)),
                        "threat", Map.of("core", "The bell's keeper is worried", "sourceRefIndexes", refs),
                        "pressure", Map.of("pressureId", "pressure", "progressionMaterial", "Night is falling", "sourceRefIndexes", refs),
                        "funnel", Map.of("funnelId", "funnel", "meaning", "Find the bell", "requiredRevelationIndexes", List.of(0), "requiredPredicateIds", List.of("predicate-open"), "sourceRefIndexes", refs),
                        "situations", List.of(
                                generatedSituation("opening", true, List.of(), "SOURCE_EXTRACTED", refs),
                                generatedSituation("candidate", false, List.of("opening"), "GM_SUPPLEMENTED", List.of()),
                                generatedSituation("link", false, List.of("candidate"), "GM_SUPPLEMENTED", List.of())),
                        "importantConsequenceIds", List.of("consequence"), "sourceRefIndexes", refs));
                if (request.operationKey().endsWith(":stage:tower")) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) detailed).putNull("openingSituationIndex");
                } else {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) detailed).put("openingSituationIndex", 0);
                }
                return detailed;
            }
            List<ScenarioSourceReference> allowed = request.prompt().contains("ALLOWED_SOURCE_REFS=[]")
                    ? List.of() : List.of(source);
            var response = mapper.createObjectNode();
            response.set("stages", mapper.valueToTree(List.of(
                    Map.of("stageId", "arrival", "order", 1, "role", "opening", "coreProblem", "The bell is missing", "funnelSummary", "Find clues", "sourceRefIndexes", allowed.stream().map(ignored -> 0).toList()),
                    Map.of("stageId", "tower", "order", 2, "role", "search", "coreProblem", "Search the tower", "funnelSummary", "Reach the bell", "sourceRefIndexes", allowed.stream().map(ignored -> 0).toList()))));
            response.set("sourceRefIndexes", mapper.valueToTree(allowed.stream().map(ignored -> 0).toList()));
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
        assertEquals("object", calls.getFirst().outputSchema().path("type").asText());
        assertEquals("object", calls.getFirst().outputSchema().path("properties").path("stages").path("items").path("type").asText());
        var schemaMethod = ScenarioPackageStageArtifactAdapter.class.getDeclaredMethod("outputSchema", String.class, List.class);
        schemaMethod.setAccessible(true);
        var emptyAllowedRefsSchema = (com.fasterxml.jackson.databind.JsonNode) schemaMethod.invoke(adapter,
                "required-extract:empty", List.of());
        var emptyRequiredSourceRefs = emptyAllowedRefsSchema.path("properties").path("requiredSituations")
                .path("items").path("properties").path("sourceRefIndexes");
        assertEquals(0, emptyRequiredSourceRefs.path("maxItems").asInt());
        assertFalse(emptyRequiredSourceRefs.has("minItems"));

        var detailed = adapter.generate(new StageDetailedGenerationPort.Request(adventureId, sessionId,
                scenarioPackage.packageId(), "arrival", 1, List.of(source),
                List.of(new RequiredSituation("opening", "The village bell is missing", List.of(), List.of(source))), true));
        assertEquals(3, detailed.situations().size());
        assertEquals(List.of("truth"), detailed.situations().getFirst().revelationIds());
        assertEquals(List.of("pressure"), detailed.situations().getFirst().pressureIds());
        assertEquals(List.of("funnel"), detailed.situations().getFirst().funnelIds());
        assertEquals(List.of("predicate-open"), detailed.situations().getFirst().funnelPredicateIds());
        assertEquals(List.of("consequence"), detailed.situations().getFirst().importantConsequenceIds());
        assertEquals(List.of("truth"), detailed.funnel().requiredRevelationIds());
        assertTrue(detailed.situations().get(0).required());
        assertEquals("opening", detailed.situations().get(0).situationId());
        assertEquals(com.dndmaster.adventure.domain.scenario.SituationProvenance.SOURCE_EXTRACTED,
                detailed.situations().get(0).provenance());
        assertEquals(com.dndmaster.adventure.domain.scenario.SituationProvenance.GM_SUPPLEMENTED,
                detailed.situations().get(1).provenance());
        assertEquals(List.of("opening"), detailed.situations().get(1).prerequisiteSituationIds());
        assertEquals("gm-supplemented:7:arrival:candidate", detailed.situations().get(1).situationId());
        assertEquals(List.of("gm-supplemented:7:arrival:candidate"), detailed.situations().get(2).prerequisiteSituationIds());
        assertEquals("gm-supplemented:7:arrival:link", detailed.situations().get(2).situationId());
        assertTrue(detailed.situations().get(0).opening());
        assertFalse(detailed.situations().get(1).opening());
        assertTrue(calls.getLast().prompt().contains("supplied map UUID or null"));
        assertTrue(calls.getLast().prompt().contains("never invent separate target IDs"));
        assertEquals("array", calls.getLast().outputSchema().path("properties").path("situations").path("type").asText());
        assertEquals("integer", calls.getLast().outputSchema().path("properties").path("openingSituationIndex")
                .path("type").asText());
        assertEquals(0, calls.getLast().outputSchema().path("properties").path("openingSituationIndex")
                .path("minimum").asInt());
        assertFalse(calls.getLast().outputSchema().path("properties").path("openingSituationIndex").has("anyOf"));
        List<String> detailedRequired = mapper.convertValue(calls.getLast().outputSchema().path("required"),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        assertTrue(detailedRequired.contains("openingSituationIndex"));
        assertTrue(calls.getLast().prompt().contains("openingSituationIndex"));
        assertTrue(calls.getLast().prompt().contains("must select one situation"));
        var detailRefSchema = calls.getLast().outputSchema().path("properties").path("sourceRefIndexes");
        assertEquals("integer", detailRefSchema.path("items").path("type").asText());
        assertEquals(0, detailRefSchema.path("items").path("minimum").asInt());
        assertTrue(calls.getLast().prompt().contains("NUMBERED_ALLOWED_SOURCE_REFS"));
        assertEquals(List.of(source), detailed.sourceRefs());
        assertEquals(List.of(source), detailed.revelations().getFirst().sourceRefs());
        assertEquals(List.of(source), detailed.threat().sourceRefs());
        assertEquals(List.of(source), detailed.pressure().sourceRefs());
        assertEquals(List.of(source), detailed.funnel().sourceRefs());
        assertEquals(List.of(source), detailed.situations().getFirst().sourceRefs());
        assertEquals(List.of("string", "null"), mapper.convertValue(calls.getLast().outputSchema()
                .path("properties").path("situations").path("items").path("properties").path("mapDefinitionId").path("type"),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        assertEquals(1, calls.getLast().outputSchema().path("properties").path("situations").path("items")
                .path("properties").path("intents").path("minItems").asInt());
        assertEquals(1, calls.getLast().outputSchema().path("properties").path("coreProblem").path("minLength").asInt());
        assertEquals(1, calls.getLast().outputSchema().path("properties").path("situations").path("items")
                .path("properties").path("mapDefinitionId").path("minLength").asInt());
        assertFalse(calls.getLast().outputSchema().path("properties").path("situations").path("items")
                .path("properties").path("revelationIds").has("minItems"));
        var situationSchema = calls.getLast().outputSchema().path("properties").path("situations").path("items");
        assertTrue(calls.getLast().prompt().contains("funnelPredicateIndexes"));
        assertTrue(situationSchema.path("properties").has("funnelPredicateIndexes"));
        assertFalse(situationSchema.path("properties").has("funnelPredicateIds"));
        assertEquals("integer", situationSchema.path("properties").path("funnelPredicateIndexes").path("items")
                .path("type").asText());
        for (String target : List.of("revelationIndexes", "pressureIndexes", "funnelIndexes", "funnelPredicateIndexes",
                "importantConsequenceIndexes")) {
            assertEquals("integer", situationSchema.path("properties").path(target).path("items").path("type").asText());
        }
        assertEquals("integer", calls.getLast().outputSchema().path("properties").path("funnel").path("properties")
                .path("requiredRevelationIndexes").path("items").path("type").asText());
        assertEquals(5, situationSchema.path("anyOf").size());
        for (String target : List.of("revelationIndexes", "pressureIndexes", "funnelIndexes", "funnelPredicateIndexes",
                "importantConsequenceIndexes")) {
            var branch = java.util.stream.StreamSupport.stream(situationSchema.path("anyOf").spliterator(), false)
                    .filter(candidate -> candidate.path("properties").path(target).has("minItems"))
                    .findFirst().orElseThrow();
            assertEquals(1, branch.path("properties").path(target).path("minItems").asInt());
            assertEquals("integer", branch.path("properties").path(target).path("items").path("type").asText());
            assertFalse(branch.path("additionalProperties").asBoolean(true));
            assertEquals(branch.path("properties").size(), branch.path("required").size());
            var declared = new java.util.HashSet<String>();
            branch.path("properties").fieldNames().forEachRemaining(declared::add);
            var required = new java.util.HashSet<String>();
            branch.path("required").forEach(field -> required.add(field.asText()));
            assertEquals(declared, required);
        }
        var resolver = ScenarioPackageStageArtifactAdapter.class.getDeclaredMethod("resolveSituationTargetIndexes",
                com.fasterxml.jackson.databind.JsonNode.class);
        resolver.setAccessible(true);
        var invalidIndexResponse = mapper.valueToTree(Map.of(
                "revelations", List.of(Map.of("revelationId", "truth")),
                "pressure", Map.of("pressureId", "pressure"),
                "funnel", Map.of("funnelId", "funnel", "requiredRevelationIndexes", List.of(0),
                        "requiredPredicateIds", List.of("predicate-open")),
                "importantConsequenceIds", List.of("consequence"),
                "situations", List.of(Map.of("revelationIndexes", List.of(1), "pressureIndexes", List.of(),
                        "funnelIndexes", List.of(), "funnelPredicateIndexes", List.of(),
                        "importantConsequenceIndexes", List.of()))));
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> resolver.invoke(null, invalidIndexResponse));

        var openingResolver = ScenarioPackageStageArtifactAdapter.class.getDeclaredMethod("resolveOpeningSituationIndex",
                com.fasterxml.jackson.databind.JsonNode.class, boolean.class);
        openingResolver.setAccessible(true);
        var invalidOpeningIndex = mapper.valueToTree(Map.of("openingSituationIndex", 1,
                "situations", List.of(Map.of("opening", false))));
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> openingResolver.invoke(null, invalidOpeningIndex, false));

        var missingFirstOpening = mapper.createObjectNode();
        missingFirstOpening.putNull("openingSituationIndex");
        missingFirstOpening.set("situations", mapper.valueToTree(List.of(Map.of("situationId", "only"))));
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> openingResolver.invoke(null, missingFirstOpening, true));

        var identityResolver = ScenarioPackageStageArtifactAdapter.class.getDeclaredMethod(
                "resolveSupplementedSituationIds", com.fasterxml.jackson.databind.JsonNode.class, String.class, List.class);
        identityResolver.setAccessible(true);
        var ambiguousPrerequisite = mapper.valueToTree(Map.of("situations", List.of(
                Map.of("situationId", "reviewed", "required", true, "provenance", "SOURCE_EXTRACTED",
                        "prerequisiteSituationIds", List.of()),
                Map.of("situationId", "reviewed", "required", false, "provenance", "GM_SUPPLEMENTED",
                        "prerequisiteSituationIds", List.of()),
                Map.of("situationId", "link", "required", false, "provenance", "GM_SUPPLEMENTED",
                        "prerequisiteSituationIds", List.of("reviewed")))));
        var reviewedRequired = List.of(new RequiredSituation("reviewed", "Known story situation", List.of(), List.of(source)));
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> identityResolver.invoke(null, ambiguousPrerequisite, "arrival", reviewedRequired));

        var optionalStage = adapter.generate(new StageDetailedGenerationPort.Request(adventureId, sessionId,
                scenarioPackage.packageId(), "tower", 1, List.of(source)));
        assertEquals(List.of("integer", "null"), mapper.convertValue(calls.getLast().outputSchema()
                .path("properties").path("openingSituationIndex").path("type"),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        assertFalse(optionalStage.situations().get(0).opening());
        assertEquals("gm-supplemented:5:tower:candidate", optionalStage.situations().get(1).situationId());
        assertFalse(detailed.situations().get(1).situationId().equals(optionalStage.situations().get(1).situationId()));

        adapter.generate(new StageBackboneGenerationPort.Request(adventureId, sessionId,
                scenarioPackage.packageId(), List.of()));
        assertEquals(0, calls.getLast().outputSchema().path("properties").path("sourceRefIndexes").path("maxItems").asInt());
        assertFalse(calls.getLast().outputSchema().toString().contains("\"sourceRefs\""));
    }

    @Test
    void canonical_source_reference_indexes_reject_out_of_range_and_direct_objects() throws Exception {
        ScenarioSourceReference source = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        var mapper = new ObjectMapper();
        var resolver = ScenarioPackageStageArtifactAdapter.class.getDeclaredMethod(
                "resolveSourceRefIndexes", com.fasterxml.jackson.databind.JsonNode.class, List.class);
        resolver.setAccessible(true);

        var outside = mapper.valueToTree(Map.of("sourceRefIndexes", List.of(1)));
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> resolver.invoke(null, outside, List.of(source)));
        for (String malformed : List.of(
                "{\"sourceRefIndexes\":[-1]}", "{\"sourceRefIndexes\":[0.5]}",
                "{\"sourceRefIndexes\":[2147483648]}")) {
            assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> resolver.invoke(null, mapper.readTree(malformed), List.of(source)));
        }
        var directReference = mapper.valueToTree(Map.of("sourceRefs", List.of(source)));
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> resolver.invoke(null, directReference, List.of(source)));
        var repeated = mapper.valueToTree(Map.of("sourceRefIndexes", List.of(0, 0)));
        resolver.invoke(null, repeated, List.of(source));
        assertEquals(2, repeated.path("sourceRefs").size());
        assertEquals(source, mapper.convertValue(repeated.path("sourceRefs").get(0), ScenarioSourceReference.class));
        assertEquals(source, mapper.convertValue(repeated.path("sourceRefs").get(1), ScenarioSourceReference.class));
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

    private static Map<String, Object> generatedSituation(String id, boolean required,
            List<String> prerequisites, String provenance, List<Integer> refs) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("situationId", id);
        result.put("intents", List.of("REVELATION"));
        result.put("revelationIndexes", List.of(0));
        result.put("pressureIndexes", id.equals("opening") ? List.of(0) : List.of());
        result.put("funnelIndexes", id.equals("opening") ? List.of(0) : List.of());
        result.put("funnelPredicateIndexes", id.equals("opening") ? List.of(0) : List.of());
        result.put("importantConsequenceIndexes", id.equals("opening") ? List.of(0) : List.of());
        result.put("required", required);
        result.put("prerequisiteSituationIds", prerequisites);
        result.put("provenance", provenance);
        result.put("sourceRefIndexes", refs);
        return result;
    }
}

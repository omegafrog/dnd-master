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
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics;

class ScenarioPackageStageArtifactAdapterTest {
    @Test
    void 필수_시츄에이션은_명시적_끝이_있는_하나의_그래프와_합류를_허용한다() {
        var source = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        var root = new RequiredSituation("root", "시작", List.of(), false, List.of(source));
        var left = new RequiredSituation("left", "왼쪽 가지", List.of("root"), false, List.of(source));
        var right = new RequiredSituation("right", "오른쪽 가지", List.of("root"), false, List.of(source));
        var simpleEnding = new RequiredSituation("simple-ending", "단일 끝", List.of("root"), true, List.of(source));
        var ending = new RequiredSituation("ending", "끝", List.of("left", "right"), true, List.of(source));
        var passages = List.of(new StorySourcePreviewPort.Passage(source, "근거"));
        ScenarioPackageStageArtifactAdapter.validateRequiredSituations(List.of(root, simpleEnding), passages);
        ScenarioPackageStageArtifactAdapter.validateRequiredSituations(List.of(root, left, right, ending), passages);
    }

    @Test
    void 필수_시츄에이션은_여러_루트는_거부하고_여러_부모는_허용한다() {
        var source = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        var root = new RequiredSituation("root", "시작", List.of(), false, List.of(source));
        var otherRoot = new RequiredSituation("other", "별도 시작", List.of(), true, List.of(source));
        var child = new RequiredSituation("child", "가지", List.of("root"), false, List.of(source));
        var merged = new RequiredSituation("merged", "여러 부모", List.of("root", "child"), true, List.of(source));
        var passages = List.of(new StorySourcePreviewPort.Passage(source, "근거"));
        assertThrows(IllegalStateException.class, () -> ScenarioPackageStageArtifactAdapter
                .validateRequiredSituations(List.of(root, otherRoot), passages));
        ScenarioPackageStageArtifactAdapter.validateRequiredSituations(List.of(root, child, merged), passages);
    }

    @Test
    void 필수_시츄에이션은_루트와_떨어진_순환_가지를_거부한다() {
        var source = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        var root = new RequiredSituation("root", "시작", List.of(), true, List.of(source));
        var first = new RequiredSituation("first", "순환 첫째", List.of("second"), false, List.of(source));
        var second = new RequiredSituation("second", "순환 둘째", List.of("first"), false, List.of(source));
        assertThrows(IllegalStateException.class, () -> ScenarioPackageStageArtifactAdapter.validateRequiredSituations(
                List.of(root, first, second), List.of(new StorySourcePreviewPort.Passage(source, "근거"))));
    }

    @Test
    void 트리_검증_로그는_실패_종류와_기대값_실제값을_기록한다() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ScenarioPackageStageArtifactAdapter.class);
        var diagnostics = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        diagnostics.start();
        logger.addAppender(diagnostics);
        String previous = System.getProperty(DevelopmentDiagnostics.FLAG);
        try {
            System.setProperty(DevelopmentDiagnostics.FLAG, "true");
            필수_시츄에이션은_명시적_끝이_있는_하나의_그래프와_합류를_허용한다();
            필수_시츄에이션은_여러_루트는_거부하고_여러_부모는_허용한다();
            필수_시츄에이션은_루트와_떨어진_순환_가지를_거부한다();
        } finally {
            if (previous == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
            else System.setProperty(DevelopmentDiagnostics.FLAG, previous);
            logger.detachAppender(diagnostics);
        }
        String logs = diagnostics.list.stream().map(event -> event.getFormattedMessage())
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(logs.contains("expectedRootCount=1 actualRootCount=2"));
        assertTrue(logs.contains("multipleParentCount=1"));
        assertTrue(logs.contains("failureKind=required situation graph must have exactly one root nextAction=reject_preparation"));
        assertTrue(logs.contains("actualEndingCount=1"));
        assertTrue(logs.contains("failureKind=required situation graph must be acyclic nextAction=reject_preparation"));
        assertTrue(logs.contains("failureKind=none nextAction=continue_preparation"));
    }

    @Test
    void codex_detail_results_are_generated_for_each_requested_stage() throws Exception {
        var model = new ScenarioModel(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "Story");
        var scenarioPackage = ScenarioPackage.publishWithScenarioModel(ScenarioBundleId.generate(), 1,
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
                scenarioPackage.packageId(), 1, 2)));
        GmProviderBindingRepository bindings = mock(GmProviderBindingRepository.class);
        when(bindings.current(sessionId.value())).thenReturn(Optional.of(new ProviderBinding(sessionId.value(),
                new GmProviderSelection(UUID.randomUUID(), "codex-cli", "story-model", "medium"), 0)));
        ObjectMapper mapper = new ObjectMapper();
        var stageRequests = new java.util.ArrayList<InitialSituationGmPort.Request>();
        var stageResults = new java.util.ArrayDeque<com.fasterxml.jackson.databind.JsonNode>();
        InitialSituationGmPort gm = request -> {
            stageRequests.add(request);
            return stageResults.removeFirst();
        };
        var adapter = new ScenarioPackageStageArtifactAdapter(packages, gm, bindings, sessions, mapper);
        AdventureId adventureId = AdventureId.generate();
        var requests = List.of("first-stage", "second-stage").stream().map(stageId ->
                new StageDetailedGenerationPort.Request(adventureId, sessionId, scenarioPackage.packageId(),
                        stageId, 1, List.of())).toList();
        stageResults.add(batchDetail(mapper, "First stage"));
        stageResults.add(batchDetail(mapper, "Second stage"));

        List<com.dndmaster.adventure.domain.scenario.DetailedStage> results = adapter.generateAll(requests);

        assertEquals(List.of("first-stage", "second-stage"), results.stream()
                .map(com.dndmaster.adventure.domain.scenario.DetailedStage::stageId).toList());
        assertEquals(List.of("First stage", "Second stage"), results.stream()
                .map(com.dndmaster.adventure.domain.scenario.DetailedStage::coreProblem).toList());
        assertEquals(2, stageRequests.size());
        assertTrue(stageRequests.get(0).operationKey().endsWith(":stage:first-stage"));
        assertTrue(stageRequests.get(1).operationKey().endsWith(":stage:second-stage"));
        assertTrue(stageRequests.stream().allMatch(request -> request.outputSchema().path("properties")
                .path("optionalSituations").path("items").path("properties").has("intents")));
        assertTrue(stageRequests.stream().allMatch(request -> !request.outputSchema().toString().contains("openingSituationIndex")));

    }

    @Test
    void extracts_required_situations_from_rag_candidates_without_reading_or_reviewing_every_story_chunk() {
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
        InitialSituationGmPort gm = request -> {
            calls.add(request);
            if (request.operationKey().endsWith("required-extract")) return mapper.valueToTree(Map.of("requiredSituations", List.of(
                    Map.of("summary", "Meet the keeper", "prerequisiteSummaries", List.of(), "isEnding", false,
                            "sourceRefIds", List.of(sourceRefId(first))),
                    Map.of("summary", "Recover the bell", "prerequisiteSummaries", List.of("Meet the keeper"),
                            "isEnding", true, "sourceRefIds", List.of(sourceRefId(second))))));
            return mapper.valueToTree(Map.of("stages", List.of(Map.of(
                    "role", "opening", "coreProblem", "Find the bell", "funnelSummary", "Follow the story",
                    "requiredSituationSummaries", List.of("Meet the keeper", "Recover the bell"),
                    "sourceRefIds", List.of(sourceRefId(first)))), "sourceRefIds", List.of(sourceRefId(first))));
        };
        var storySearchCount = new java.util.concurrent.atomic.AtomicInteger();
        StorySourcePreviewPort source = (selected, ownerId, bundleId) -> {
            storySearchCount.incrementAndGet();
            return List.of(new StorySourcePreviewPort.Passage(first, "The keeper meets the party."),
                    new StorySourcePreviewPort.Passage(second, "Afterward, recover the bell."));
        };
        var adapter = new ScenarioPackageStageArtifactAdapter(packages, gm, bindings, sessions, mapper, source);

        var preparationEvidence = adapter.lookupForPreparation(scenarioPackage.packageId(), sessionId);
        assertEquals(1, storySearchCount.get());

        var adventureId = AdventureId.generate();
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ScenarioPackageStageArtifactAdapter.class);
        var diagnostics = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        diagnostics.start();
        logger.addAppender(diagnostics);
        String previousDiagnostics = System.getProperty(DevelopmentDiagnostics.FLAG);
        com.dndmaster.adventure.domain.scenario.StageBackbone result;
        try {
            System.setProperty(DevelopmentDiagnostics.FLAG, "true");
            result = adapter.generate(new StageBackboneGenerationPort.Request(adventureId, sessionId,
                    scenarioPackage.packageId(), preparationEvidence.sourceRefs(), preparationEvidence.storyPassages()));
        } finally {
            if (previousDiagnostics == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
            else System.setProperty(DevelopmentDiagnostics.FLAG, previousDiagnostics);
            logger.detachAppender(diagnostics);
        }

        assertEquals(List.of("Meet the keeper", "Recover the bell"), result.requiredSituations().stream()
                .map(RequiredSituation::summary).toList());
        assertTrue(result.requiredSituations().stream().allMatch(item -> {
            try { UUID.fromString(item.situationId()); return true; }
            catch (IllegalArgumentException invalid) { return false; }
        }));
        assertEquals(List.of(result.requiredSituations().getFirst().situationId()),
                result.requiredSituations().get(1).prerequisiteSituationIds());
        assertEquals("stage-1", result.stages().getFirst().stageId());
        assertEquals(result.requiredSituations().stream().map(RequiredSituation::situationId).toList(),
                result.stages().getFirst().requiredSituationIds());
        assertEquals(2, calls.size());
        assertTrue(calls.getFirst().operationKey().endsWith("required-extract"));
        assertTrue(calls.getFirst().prompt().contains("RETRIEVED_STORY_PASSAGES"));
        assertTrue(calls.getFirst().prompt().contains("not the complete story"));
        assertTrue(calls.getFirst().prompt().contains("one acyclic graph"));
        assertTrue(calls.getFirst().outputSchema().path("properties").path("requiredSituations")
                .path("items").path("properties").path("prerequisiteSummaries").path("type").asText().equals("array"));
        assertFalse(calls.getFirst().prompt().contains("ALL_PROPOSED_REQUIRED_SITUATIONS"));
        assertFalse(calls.stream().anyMatch(call -> call.operationKey().contains("required-review")));
        assertTrue(calls.getFirst().prompt().contains("The keeper meets the party."));
        assertTrue(calls.getFirst().prompt().contains("Afterward, recover the bell."));
        assertFalse(calls.getFirst().outputSchema().toString().contains("sourceRefs"));
        assertFalse(calls.getFirst().outputSchema().toString().contains("situationId"));
        assertFalse(calls.getFirst().outputSchema().toString().contains("prerequisiteSituationIds"));
        assertFalse(calls.get(1).outputSchema().toString().contains("stageId"));
        assertFalse(calls.get(1).outputSchema().toString().contains("requiredSituationIds"));
        String diagnosticLog = diagnostics.list.stream().map(event -> event.getFormattedMessage())
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(diagnosticLog.contains("task=required-extract expectedAgentIdentifierFields=none actualAgentIdentifierFields=none requiredSituationCount=2 prerequisiteCount=1 explicitEndingCount=1 idPolicy=server_assigned"));
        assertTrue(diagnosticLog.contains("assignmentReferenceField=requiredSituationSummaries"));
        assertTrue(diagnosticLog.contains("expectedRequiredSituationCount=2 actualRequiredSituationCount=2 stageCount=1"));
        assertEquals("array", calls.getFirst().outputSchema().path("properties").path("requiredSituations").path("type").asText());
        assertTrue(calls.getFirst().outputSchema().path("required").toString().contains("requiredSituations"));
        assertEquals(1, storySearchCount.get());
        assertEquals(List.of(first, second), result.sourceRefs());
    }

    @Test
    void detailed_stage_uses_server_assigned_ids_and_does_not_request_identifier_references() {
        var source = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        var encounter = new ScenarioModelElement("cellar-rats", "combat-scenario",
                Map.of("enemyKey", "giant-rat", "displayName", "Giant rats", "count", 2,
                        "location", "Cellar"), List.of(source));
        var model = new ScenarioModel(1, List.of(), List.of(), List.of(), List.of(), List.of(encounter), List.of(), List.of(), "Story");
        UUID mapId = UUID.randomUUID();
        var map = new com.dndmaster.adventure.domain.scenario.MapDefinition(mapId, "cellar-map", "page=1:image=1",
                new com.dndmaster.adventure.domain.scenario.MapDefinition.MapGrid(0, 0, 40, 0, "5 ft"),
                List.of(), List.of(), List.of(), new com.dndmaster.adventure.domain.scenario.MapSourceReference(
                        source.knowledgeDocumentId(), source.extractionVersion(), "page=1:image=1"), .95,
                com.dndmaster.adventure.domain.scenario.MapSafetyStatus.SAFE);
        var scenarioPackage = ScenarioPackage.publishWithScenarioModel(ScenarioBundleId.generate(), 1,
                "fingerprint", List.of(), List.of(), new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()),
                CharacterLimit.defaultLimit(), null, List.of(map), List.of(), model);
        ScenarioPackageRepository packages = new ScenarioPackageRepository() {
            @Override public Optional<ScenarioPackage> findByInputFingerprint(String ignored) { return Optional.empty(); }
            @Override public Optional<ScenarioPackage> findById(UUID id) { return Optional.of(scenarioPackage); }
            @Override public void save(ScenarioPackage ignored) {}
        };
        SessionId sessionId = SessionId.generate();
        var owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(AdventureSession.create(sessionId, owner,
                scenarioPackage.packageId(), 1, 2)));
        GmProviderBindingRepository bindings = mock(GmProviderBindingRepository.class);
        when(bindings.current(sessionId.value())).thenReturn(Optional.of(new ProviderBinding(sessionId.value(),
                new GmProviderSelection(UUID.randomUUID(), "openai", "story-model", "medium"), 0)));
        var mapper = new ObjectMapper();
        var calls = new java.util.ArrayList<InitialSituationGmPort.Request>();
        InitialSituationGmPort gm = request -> {
            calls.add(request);
            var response = mapper.createObjectNode();
            response.put("coreProblem", "Find the missing bell");
            response.putArray("revelations").addObject().put("required", true).putArray("sourceRefIds").add(sourceRefId(source));
            response.putObject("threat").put("core", "The keeper is worried").putArray("sourceRefIds").add(sourceRefId(source));
            response.putObject("pressure").put("progressionMaterial", "Night is falling").putArray("sourceRefIds").add(sourceRefId(source));
            response.putObject("funnel").put("meaning", "Find the bell").putArray("requiredPredicateLabels").add("keeper trusts party");
            response.with("funnel").putArray("sourceRefIds").add(sourceRefId(source));
            var requiredSituation = response.putArray("requiredSituations").addObject();
            requiredSituation.put("title", "The missing bell is hidden below");
            requiredSituation.put("description", "The bell's trail leads beneath the brewery, where the keeper is afraid to enter alone.");
            requiredSituation.put("mapNeeded", true).put("mapDefinitionId", mapId.toString());
            requiredSituation.putArray("intents").add("REVELATION");
            var optional = response.putArray("optionalSituations").addObject();
            optional.put("title", "A goblin scout is on patrol");
            optional.put("description", "A goblin scout circles the brewery yard, watching the cellar door.");
            optional.put("mapNeeded", false).putNull("mapDefinitionId");
            optional.putArray("intents").add("REVELATION");
            optional.putArray("sourceRefIds");
            response.putArray("importantConsequenceLabels").add("keeper's fate");
            response.putArray("sourceRefIds").add(sourceRefId(source));
            return response;
        };
        var adapter = new ScenarioPackageStageArtifactAdapter(packages, gm, bindings, sessions, mapper);
        var requiredSituation = new com.dndmaster.adventure.domain.scenario.RequiredSituation(
                "required-situation-id", "The bell is missing", List.of(), false, List.of(source));
        var request = new StageDetailedGenerationPort.Request(AdventureId.generate(), sessionId,
                scenarioPackage.packageId(), "arrival", 1, List.of(source), List.of(requiredSituation), true);

        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ScenarioPackageStageArtifactAdapter.class);
        var diagnostics = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        diagnostics.start();
        logger.addAppender(diagnostics);
        String previousDiagnostics = System.getProperty(DevelopmentDiagnostics.FLAG);
        com.dndmaster.adventure.domain.scenario.DetailedStage result;
        try {
            System.setProperty(DevelopmentDiagnostics.FLAG, "true");
            result = adapter.generate(request);
        } finally {
            if (previousDiagnostics == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
            else System.setProperty(DevelopmentDiagnostics.FLAG, previousDiagnostics);
            logger.detachAppender(diagnostics);
        }

        var schema = calls.getFirst().outputSchema().toString();
        var requiredSituationSchema = calls.getFirst().outputSchema().path("properties").path("requiredSituations");
        var requiredItemSchema = requiredSituationSchema.path("items");
        var situationSchema = calls.getFirst().outputSchema().path("properties").path("optionalSituations");
        var itemSchema = situationSchema.path("items");
        assertEquals(1, requiredSituationSchema.path("minItems").asInt());
        assertEquals(1, requiredSituationSchema.path("maxItems").asInt());
        assertFalse(requiredItemSchema.path("properties").has("sourceRefIds"));
        assertEquals(120, itemSchema.path("properties").path("title").path("maxLength").asInt());
        assertEquals(2000, itemSchema.path("properties").path("description").path("maxLength").asInt());
        assertFalse(itemSchema.path("properties").has("requiredSituationIndex"));
        assertFalse(calls.getFirst().prompt().contains("requiredSituationIndex"));
        assertEquals("boolean", itemSchema.path("properties").path("mapNeeded").path("type").asText());
        for (String forbidden : List.of("situationId", "revelationId", "pressureId", "funnelId",
                "requiredPredicateIds", "importantConsequenceIds", "prerequisiteSituationIds",
                "revelationIndexes", "requiredRevelationIndexes", "fixturePrerequisiteSituationIndexes",
                "requiredSituationIndex")) {
            assertFalse(schema.contains(forbidden), forbidden);
        }
        assertEquals("required-situation-id", result.situations().getFirst().situationId());
        assertTrue(result.situations().getFirst().required());
        assertTrue(result.situations().getFirst().opening());
        assertFalse(result.situations().getFirst().isEnding());
        assertTrue(result.situations().get(1).situationId().matches("[0-9a-f-]{36}"));
        assertEquals("A goblin scout is on patrol", result.situations().get(1).title());
        for (String id : List.of(result.revelations().getFirst().revelationId(), result.pressure().pressureId(),
                result.funnel().funnelId())) {
            UUID.fromString(id);
        }
        assertTrue(result.funnel().requiredPredicateIds().getFirst().startsWith(UUID.fromString(
                result.funnel().requiredPredicateIds().getFirst().split(":", 2)[0]).toString() + ":keeper trusts party"));
        assertTrue(result.importantConsequenceIds().getFirst().endsWith(":keeper's fate"));
        assertTrue(calls.getFirst().prompt().contains("Do not create IDs or array positions"));
        assertTrue(calls.getFirst().prompt().contains("zero, one, or several are valid"));
        assertTrue(calls.getFirst().prompt().contains("Do not name a situation after an abstract purpose"));
        assertTrue(calls.getFirst().prompt().contains("The server assigns every ID and relationship"));
        assertTrue(calls.getFirst().prompt().contains("combat encounter at a location covered by an available map"));
        assertTrue(calls.getFirst().prompt().contains("\"location\":\"Cellar\""));
        assertTrue(calls.getFirst().prompt().contains(mapId.toString()));
        assertTrue(result.situations().getFirst().mapNeeded());
        assertEquals(mapId, result.situations().getFirst().mapDefinitionId());
        assertEquals(1, calls.size());
        String diagnosticLog = diagnostics.list.stream().map(event -> event.getFormattedMessage())
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(diagnosticLog.contains("dev_initial_situation_ids outcome=completed"));
        assertTrue(diagnosticLog.contains("allowedMapCount=1 allowedMapNeededValues=[false, true]"));
        assertTrue(diagnosticLog.contains("outcome=reused_required"));
        assertTrue(diagnosticLog.contains("expectedAgentIdentifierFields=none actualAgentIdentifierFields=none"));
        assertTrue(diagnosticLog.contains("situationCount=2"));
        assertTrue(diagnosticLog.contains("relationPolicy=required_ids_reused_optional_ids_server_assigned"));
    }

    @Test
    void canonical_source_reference_ids_reject_unknown_ids_and_direct_objects() throws Exception {
        ScenarioSourceReference source = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        var mapper = new ObjectMapper();
        var resolver = ScenarioPackageStageArtifactAdapter.class.getDeclaredMethod(
                "resolveSourceRefIds", com.fasterxml.jackson.databind.JsonNode.class, List.class);
        resolver.setAccessible(true);

        var outside = mapper.valueToTree(Map.of("sourceRefIds", List.of(UUID.randomUUID().toString())));
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> resolver.invoke(null, outside, List.of(source)));
        for (String malformed : List.of("{\"sourceRefIds\":[1]}", "{\"sourceRefIds\":null}")) {
            assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> resolver.invoke(null, mapper.readTree(malformed), List.of(source)));
        }
        var directReference = mapper.valueToTree(Map.of("sourceRefs", List.of(source)));
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> resolver.invoke(null, directReference, List.of(source)));
        var repeated = mapper.valueToTree(Map.of("sourceRefIds", List.of(sourceRefId(source), sourceRefId(source))));
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

    private static String sourceRefId(ScenarioSourceReference source) {
        String identity = source.knowledgeDocumentId().value() + "|" + source.extractionVersion() + "|" + source.locator();
        return UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static com.fasterxml.jackson.databind.JsonNode batchDetail(ObjectMapper mapper, String coreProblem) {
        var detail = mapper.createObjectNode();
        detail.put("coreProblem", coreProblem);
        detail.putArray("revelations").addObject().put("required", true)
                .putArray("sourceRefIds");
        var threat = detail.putObject("threat");
        threat.put("core", "A threat").putArray("sourceRefIds");
        var pressure = detail.putObject("pressure");
        pressure.put("progressionMaterial", "Time passes")
                .putArray("sourceRefIds");
        var funnel = detail.putObject("funnel");
        funnel.put("meaning", "Continue");
        funnel.putArray("requiredPredicateLabels").add("predicate");
        funnel.putArray("sourceRefIds");
        detail.putArray("requiredSituations");
        detail.putArray("optionalSituations");
        detail.putArray("importantConsequenceLabels").add("consequence");
        detail.putArray("sourceRefIds");
        return detail;
    }
}

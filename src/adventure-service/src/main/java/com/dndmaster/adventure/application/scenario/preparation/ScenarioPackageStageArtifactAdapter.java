package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.RequiredSituation;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.SituationProvenance;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.stream.Stream;
import com.dndmaster.adventure.application.runtime.GmProviderBindingRepository;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Adapts the published Scenario Package into the minimum first-stage artifact request. */
public final class ScenarioPackageStageArtifactAdapter implements StorybookEvidenceLookupPort,
        StageBackboneGenerationPort, StageDetailedGenerationPort {
    private static final String FIRST_STAGE_ID = "first-stage";
    private static final int REQUIRED_SITUATION_SUMMARY_MAX_CODE_POINTS = 500;
    private final ScenarioPackageRepository packages;
    private final InitialSituationGmPort gm;
    private final GmProviderBindingRepository bindings;
    private final AdventureSessionRepository sessions;
    private final ObjectMapper mapper;
    private final StorySourcePreviewPort storySource;
    private final ScenarioMapImagePort mapImage;

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages) {
        this(packages, null, null, null, null, null, null);
    }

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages, InitialSituationGmPort gm,
            GmProviderBindingRepository bindings, AdventureSessionRepository sessions, ObjectMapper mapper) {
        this(packages, gm, bindings, sessions, mapper, null, null);
    }

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages, InitialSituationGmPort gm,
            GmProviderBindingRepository bindings, AdventureSessionRepository sessions, ObjectMapper mapper,
            StorySourcePreviewPort storySource) {
        this(packages, gm, bindings, sessions, mapper, storySource, null);
    }

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages, InitialSituationGmPort gm,
            GmProviderBindingRepository bindings, AdventureSessionRepository sessions, ObjectMapper mapper,
            StorySourcePreviewPort storySource, ScenarioMapImagePort mapImage) {
        this.packages = java.util.Objects.requireNonNull(packages, "scenario package repository must not be null");
        this.gm = gm;
        this.bindings = bindings;
        this.sessions = sessions;
        this.mapper = mapper;
        this.storySource = storySource;
        this.mapImage = mapImage;
    }

    @Override
    public List<ScenarioSourceReference> lookup(UUID scenarioPackageId) {
        return elements(loadPackage(scenarioPackageId)).flatMap(element -> element.sourceRefs().stream()).distinct().toList();
    }

    @Override
    public List<ScenarioSourceReference> lookup(UUID scenarioPackageId, SessionId sessionId) {
        return lookup(scenarioPackageId);
    }

    @Override
    public StorybookEvidenceLookupPort.PreparationEvidence lookupForPreparation(UUID scenarioPackageId, SessionId sessionId) {
        ScenarioPackage scenarioPackage = loadPackage(scenarioPackageId);
        var session = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("adventure session not found"));
        List<StorySourcePreviewPort.Passage> passages = sourcePassages(scenarioPackage, session.ownerPlayerId().value());
        List<ScenarioSourceReference> refs = Stream.concat(elements(scenarioPackage).flatMap(element -> element.sourceRefs().stream()),
                passages.stream().map(StorySourcePreviewPort.Passage::sourceRef)).distinct().toList();
        return new StorybookEvidenceLookupPort.PreparationEvidence(refs, passages);
    }

    @Override
    public boolean isScenarioBacked(UUID scenarioPackageId) {
        ScenarioPackage scenarioPackage = loadPackage(scenarioPackageId);
        return scenarioPackage.documents().stream().anyMatch(document -> document.role()
                == com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.MAIN_SCENARIO)
                || elements(scenarioPackage).anyMatch(element -> !element.sourceRefs().isEmpty());
    }

    @Override
    public java.util.Set<UUID> providedMapIds(UUID scenarioPackageId) {
        return loadPackage(scenarioPackageId).mapDefinitions().stream().map(com.dndmaster.adventure.domain.scenario.MapDefinition::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public StageBackbone generate(StageBackboneGenerationPort.Request request) {
        if (gm != null) {
            ScenarioPackage scenarioPackage = loadPackage(request.scenarioPackageId());
            List<RequiredSituation> required = extractRequiredSituations(request, scenarioPackage, request.storyPassages());
            List<List<String>> rules = lookupRules(scenarioPackage, request.sessionId(), required);
            devLog("dev_initial_situation_input task=backbone adventureId={} sessionId={} packageId={} retrievedPassageCount={} retrievedPassageChars={} requiredSituationCount={} requiredSituations={} ruleEvidence={} sourceRefCount={} scenarioContextChars={}",
                    request.adventureId().value(), request.sessionId().value(), request.scenarioPackageId(),
                    request.storyPassages().size(), request.storyPassages().stream().mapToInt(p -> p.text().length()).sum(),
                    required.size(), requiredSituationInventory(required), ruleEvidenceInventory(required, rules),
                    request.evidence().size(), promptStoryContext(scenarioPackage).length());
            JsonNode generated = generate(request.sessionId(), request.adventureId().value(),
                    request.scenarioPackageId(), "backbone", backbonePrompt(scenarioPackage, request.evidence(), required, rules),
                    request.evidence(), false, Set.of(), false, null, required);
            JsonNode resolved = generated.deepCopy();
            long startedAt = System.nanoTime();
            try {
                resolveSourceRefIds(resolved, request.evidence());
                assignBackboneIdsAndRelations(resolved, request, required);
                List<StageBackboneEntry> stages = mapper.convertValue(resolved.path("stages"),
                        mapper.getTypeFactory().constructCollectionType(List.class, StageBackboneEntry.class));
                StageBackbone result = new StageBackbone(request.scenarioPackageId(), 1, stages, request.evidence(), required);
                devLog("dev_initial_situation_backbone_materialize outcome=completed adventureId={} expectedRequiredSituationCount={} actualRequiredSituationCount={} stageCount={} elapsedMs={}",
                        request.adventureId().value(), required.size(), result.requiredSituations().size(), result.stages().size(),
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                return result;
            } catch (RuntimeException failure) {
                devLog("dev_initial_situation_backbone_materialize outcome=failed adventureId={} expectedRequiredSituationCount={} actualStageCount={} responseChars={} responseFingerprint={} failureClass={} elapsedMs={} nextAction=reject_preparation",
                        request.adventureId().value(), required.size(), resolved.path("stages").isArray() ? resolved.path("stages").size() : -1,
                        generated.toString().length(), com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics
                                .fingerprint(generated.toString()), failure.getClass().getSimpleName(),
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                throw failure;
            }
        }
        ScenarioPackage scenarioPackage = loadPackage(request.scenarioPackageId());
        String coreProblem = openingProblem(scenarioPackage);
        String funnel = openingGoal(scenarioPackage);
        return new StageBackbone(request.scenarioPackageId(), 1,
                List.of(new StageBackboneEntry(FIRST_STAGE_ID, 1, "opening", coreProblem, funnel, request.evidence())),
                request.evidence());
    }

    @Override
    public DetailedStage generate(StageDetailedGenerationPort.Request request) {
        if (gm != null) {
            ScenarioPackage scenarioPackage = loadPackage(request.scenarioPackageId());
            List<List<String>> rules = lookupRules(scenarioPackage, request.sessionId(), request.requiredSituations());
            List<com.dndmaster.adventure.domain.scenario.MapDefinition> usableMaps = scenarioPackage.mapDefinitions().stream()
                    .filter(com.dndmaster.adventure.domain.scenario.MapDefinition::autoActivatable)
                    .toList();
            Set<UUID> allowedMapIds = usableMaps.stream().map(com.dndmaster.adventure.domain.scenario.MapDefinition::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            var imageMap = usableMaps.stream().findFirst().orElse(null);
            String imageDataUri = imageMap == null || mapImage == null ? "" : mapImage.load(imageMap);
            UUID attachedMapImageId = imageDataUri.isBlank() || imageMap == null ? null : imageMap.id();
            String prompt = detailedPrompt(scenarioPackage, request.stageId(), request.evidence(), request.requiredSituations(),
                    request.alreadyAssignedRequiredSituationIds(), rules, attachedMapImageId);
            devLog("dev_initial_situation_input task=detailed adventureId={} sessionId={} packageId={} stageId={} openingSituationRequired={} requiredSituationCount={} requiredSituationSchemaItemCount={} requiredSituationSchemaSourceRefCounts={} requiredSituations={} alreadyAssignedRequiredSituationIds={} ruleEvidence={} sourceRefCount={} sourceRefs={} mapDefinitionCount={} usableMapDefinitionCount={} excludedMapDefinitions={} allowedMapIds={} scenarioContextChars={} promptChars={} promptFingerprint={} retrievedPassageTextIncluded=false backboneStageSummaryIncluded=false",
                    request.adventureId().value(), request.sessionId().value(), request.scenarioPackageId(), request.stageId(),
                    request.openingSituationRequired(), request.requiredSituations().size(), request.requiredSituations().size(),
                    request.requiredSituations().stream().map(item -> item.sourceRefs().size()).toList(),
                    requiredSituationInventory(request.requiredSituations()), request.alreadyAssignedRequiredSituationIds(),
                    ruleEvidenceInventory(request.requiredSituations(), rules), request.evidence().size(),
                    sourceReferenceInventory(request.evidence()), scenarioPackage.mapDefinitions().size(), allowedMapIds.size(),
                    scenarioPackage.mapDefinitions().stream().filter(definition -> !definition.autoActivatable())
                            .map(definition -> definition.id() + ":" + definition.safetyStatus() + ":confidence=" + definition.confidence()).toList(),
                    allowedMapIds.stream().sorted().map(UUID::toString).toList(), promptStoryContext(scenarioPackage).length(),
                    prompt.length(), com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(prompt));
            String task = "stage:" + request.stageId();
            JsonNode generated = generate(request.sessionId(), request.adventureId().value(),
                    request.scenarioPackageId(), task, prompt, request.evidence(), request.openingSituationRequired(),
                    allowedMapIds, false, null, request.requiredSituations(), imageDataUri);
            return materializeDetailedStage(request, generated, allowedMapIds);
        }
        ScenarioPackage scenarioPackage = loadPackage(request.scenarioPackageId());
        String coreProblem = openingProblem(scenarioPackage);
        String revelationId = scenarioPackage.scenarioModel().revelations().stream()
                .findFirst().map(ScenarioModelElement::elementId).orElse("first-stage-purpose");
        List<ScenarioSourceReference> evidence = request.evidence();
        return new DetailedStage(request.scenarioPackageId(), request.backboneRevision(), request.stageId(), 1,
                coreProblem, List.of(new RevelationDefinition(revelationId, true, evidence)),
                new ThreatDefinition(openingThreat(scenarioPackage), evidence),
                new PressureDefinition("pressure", openingGoal(scenarioPackage), evidence),
                new FunnelDefinition("funnel", openingGoal(scenarioPackage), List.of(revelationId), List.of(), evidence),
                List.of(new SituationDefinition("opening-situation", List.of(StageIntent.REVELATION), List.of(revelationId),
                        List.of(), List.of(), List.of(), List.of(), true, evidence),
                        fallbackCandidate("candidate-a", revelationId, evidence),
                        fallbackCandidate("candidate-b", revelationId, evidence)), List.of(), evidence);
    }

    @Override
    public List<DetailedStage> generateAll(List<StageDetailedGenerationPort.Request> requests) {
        if (requests.isEmpty() || gm == null) return StageDetailedGenerationPort.super.generateAll(requests);
        var binding = bindings.current(requests.getFirst().sessionId().value())
                .orElseThrow(() -> new IllegalStateException("GM provider selection not found"));
        if (!"codex-cli".equalsIgnoreCase(binding.selection().provider())) {
            return StageDetailedGenerationPort.super.generateAll(requests);
        }
        long startedAt = System.nanoTime();
        devLog("dev_initial_situation_batch outcome=started adventureId={} sessionId={} packageId={} expectedStageCount={} stageIds={} strategy=sequential_single_stage_requests",
                requests.getFirst().adventureId().value(), requests.getFirst().sessionId().value(),
                requests.getFirst().scenarioPackageId(), requests.size(),
                requests.stream().map(StageDetailedGenerationPort.Request::stageId).toList());
        List<DetailedStage> result = new ArrayList<>(requests.size());
        for (var request : requests) result.add(generate(request));
        devLog("dev_initial_situation_batch outcome=completed adventureId={} expectedStageCount={} actualStageCount={} elapsedMs={} strategy=sequential_single_stage_requests",
                requests.getFirst().adventureId().value(), requests.size(), result.size(),
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        return List.copyOf(result);
    }

    private DetailedStage materializeDetailedStage(StageDetailedGenerationPort.Request request, JsonNode generated,
            Set<UUID> allowedMapIds) {
        long startedAt = System.nanoTime();
        try {
            JsonNode detail = generated.deepCopy();
            var content = detail.deepCopy();
            resolveSourceRefIds(content, request.evidence());
            content = normalizeDetailedSituations(content, request);
            resolveRequiredSituations(content, request);
            assignServerIdsAndRelations(content, request);
            resolveSupplementedSituationIds(content);
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("scenarioPackageId", request.scenarioPackageId().toString());
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("backboneRevision", request.backboneRevision());
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("stageId", request.stageId());
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("revision", 1);
            DetailedStage result = mapper.convertValue(content, DetailedStage.class);
            devLog("dev_initial_situation_detail_materialize outcome=completed adventureId={} stageId={} expectedRequiredSituationCount={} actualSituationCount={} allowedMapCount={} elapsedMs={}",
                    request.adventureId().value(), request.stageId(), request.requiredSituations().size(), result.situations().size(),
                    allowedMapIds.size(), java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            return result;
        } catch (RuntimeException failure) {
            devLog("dev_initial_situation_detail_materialize outcome=failed adventureId={} stageId={} expectedRequiredSituationCount={} actualSituationCount={} responseChars={} responseFingerprint={} failureClass={} elapsedMs={} nextAction=reject_preparation",
                    request.adventureId().value(), request.stageId(), request.requiredSituations().size(),
                    generated.path("requiredSituations").isArray() && generated.path("optionalSituations").isArray()
                            ? generated.path("requiredSituations").size() + generated.path("optionalSituations").size() : -1,
                    generated.toString().length(), com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics
                            .fingerprint(generated.toString()), failure.getClass().getSimpleName(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            throw failure;
        }
    }

    private JsonNode normalizeDetailedSituations(JsonNode generated, StageDetailedGenerationPort.Request request) {
        JsonNode requiredDetails = generated.path("requiredSituations");
        JsonNode optionalDetails = generated.path("optionalSituations");
        if (!generated.isObject() || !requiredDetails.isArray() || !optionalDetails.isArray()
                || requiredDetails.size() != request.requiredSituations().size()) {
            throw new IllegalStateException("generated required and optional situation details are invalid");
        }
        var content = (com.fasterxml.jackson.databind.node.ObjectNode) generated;
        var situations = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < requiredDetails.size(); index++) {
            JsonNode item = requiredDetails.get(index);
            if (!item.isObject()) throw new IllegalStateException("generated required situation detail is invalid");
            var required = (com.fasterxml.jackson.databind.node.ObjectNode) item.deepCopy();
            required.set("sourceRefs", mapper.valueToTree(request.requiredSituations().get(index).sourceRefs()));
            situations.add(required);
        }
        optionalDetails.forEach(item -> situations.add(item.deepCopy()));
        content.remove("requiredSituations");
        content.remove("optionalSituations");
        content.set("situations", situations);
        return content;
    }

    private SituationDefinition fallbackCandidate(String id, String revelationId, List<ScenarioSourceReference> evidence) {
        return new SituationDefinition(id, List.of(StageIntent.REVELATION), List.of(revelationId),
                List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                evidence.isEmpty() ? SituationProvenance.GM_SUPPLEMENTED : SituationProvenance.SOURCE_EXTRACTED,
                null, evidence);
    }

    private JsonNode generate(SessionId sessionId, UUID adventureId, UUID packageId, String task, String taskPrompt,
            List<ScenarioSourceReference> allowedRefs) {
        return generate(sessionId, adventureId, packageId, task, taskPrompt, allowedRefs, false, Set.of(),
                false, null, List.of(), "");
    }

    private JsonNode generate(SessionId sessionId, UUID adventureId, UUID packageId, String task, String taskPrompt,
            List<ScenarioSourceReference> allowedRefs, boolean openingSituationRequired, Set<UUID> allowedMapIds,
            boolean useSubagents, JsonNode schemaOverride, List<RequiredSituation> requiredSituations) {
        return generate(sessionId, adventureId, packageId, task, taskPrompt, allowedRefs, openingSituationRequired,
                allowedMapIds, useSubagents, schemaOverride, requiredSituations, "");
    }

    private JsonNode generate(SessionId sessionId, UUID adventureId, UUID packageId, String task, String taskPrompt,
            List<ScenarioSourceReference> allowedRefs, boolean openingSituationRequired, Set<UUID> allowedMapIds,
            boolean useSubagents, JsonNode schemaOverride, List<RequiredSituation> requiredSituations,
            String imageDataUri) {
        var session = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("adventure session not found"));
        var binding = bindings.current(sessionId.value()).orElseThrow(() -> new IllegalStateException("GM provider selection not found"));
        String prompt = "ROLE=INITIAL_SITUATION_PREPARATION\nTASK=" + task + "\n" + taskPrompt
                + "\nOUTPUT_CONTRACT=Return exactly one JSON object matching the requested fields. No markdown.";
        JsonNode schema = schemaOverride == null
                ? outputSchema(task, allowedRefs, openingSituationRequired, allowedMapIds, requiredSituations) : schemaOverride;
        devLog("dev_initial_situation_agent_request task={} requestId={} provider={} model={} useSubagents={} promptChars={} promptFingerprint={} schemaChars={} allowedSourceRefCount={} openingSituationRequired={} allowedMapCount={} allowedMapNeededValues={} imageAttached={} imageDataUriChars={}",
                task, "initial-situation:" + adventureId + ":" + packageId + ":" + task, binding.selection().provider(),
                binding.selection().model(), useSubagents, prompt.length(),
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(prompt),
                schema.toString().length(), allowedRefs.size(), openingSituationRequired, allowedMapIds.size(),
                allowedMapIds.isEmpty() ? List.of(false) : List.of(false, true), !imageDataUri.isBlank(), imageDataUri.length());
        String requestId = "initial-situation:" + adventureId + ":" + packageId + ":" + task;
        long startedAt = System.nanoTime();
        try {
            JsonNode response = gm.generate(new InitialSituationGmPort.Request(session.ownerPlayerId().value(), sessionId.value(),
                    binding.selection().endpointId(), binding.selection().provider(), binding.selection().model(),
                    binding.selection().reasoning(), requestId, prompt, schema, useSubagents, List.of(),
                    openingSituationRequired, requiredSituations.size(), imageDataUri));
            devLog("dev_initial_situation_agent_response outcome=completed task={} requestId={} responseType={} topLevelFields={} responseChars={} responseFingerprint={} elapsedMs={}",
                    task, requestId, response.getNodeType(), jsonFields(response), response.toString().length(),
                    com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(response.toString()),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            return response;
        } catch (RuntimeException | Error failure) {
            devLog("dev_initial_situation_agent_response outcome=failed task={} requestId={} failureClass={} elapsedMs={}",
                    task, requestId, failure.getClass().getSimpleName(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            throw failure;
        }
    }

    private static List<String> jsonFields(JsonNode value) {
        if (!value.isObject()) return List.of();
        List<String> fields = new ArrayList<>();
        value.fieldNames().forEachRemaining(fields::add);
        return List.copyOf(fields);
    }

    private JsonNode outputSchema(String task, List<ScenarioSourceReference> allowedRefs) {
        return outputSchema(task, allowedRefs, false, Set.of(), List.of());
    }

    private JsonNode outputSchema(String task, List<ScenarioSourceReference> allowedRefs,
            boolean openingSituationRequired) {
        return outputSchema(task, allowedRefs, openingSituationRequired, Set.of(), List.of());
    }

    private JsonNode outputSchema(String task, List<ScenarioSourceReference> allowedRefs,
            boolean openingSituationRequired, Set<UUID> allowedMapIds) {
        return outputSchema(task, allowedRefs, openingSituationRequired, allowedMapIds, List.of());
    }

    private JsonNode outputSchema(String task, List<ScenarioSourceReference> allowedRefs,
            boolean openingSituationRequired, Set<UUID> allowedMapIds,
            List<RequiredSituation> requiredSituations) {
        Map<String, Object> properties;
        if (task.equals("required-extract")) {
            properties = Map.of("requiredSituations", arraySchema(requiredSituationSchema(allowedRefs)));
        } else if (task.equals("backbone")) {
            List<String> requiredSituationSummaries = requiredSituations.stream().map(RequiredSituation::summary).toList();
            properties = Map.of("stages", arraySchema(objectSchema(Map.of(
                            "role", stringSchema(), "coreProblem", stringSchema(), "funnelSummary", stringSchema(),
                            "requiredSituationSummaries", arraySchema(enumSchema(requiredSituationSummaries)),
                            "sourceRefIds", sourceRefIdsSchema(allowedRefs))), 1),
                    "sourceRefIds", sourceRefIdsSchema(allowedRefs));
        } else if (task.startsWith("stage:")) {
            Map<String, Object> situationProperties = Map.ofEntries(
                    Map.entry("title", boundedStringSchema(120)),
                    Map.entry("description", boundedStringSchema(2000)),
                    Map.entry("intents", arraySchema(enumSchema("REVELATION", "PRESSURE", "FUNNEL_OPPORTUNITY", "IMPORTANT_CONSEQUENCE"), 1)),
                    Map.entry("mapNeeded", mapNeededSchema(allowedMapIds)),
                    Map.entry("mapDefinitionId", nullableMapDefinitionSchema(allowedMapIds)),
                    Map.entry("combatScenarioIds", arraySchema(stringSchema())),
                    Map.entry("sourceRefIds", sourceRefIdsSchema(allowedRefs)));
            Map<String, Object> situation = objectSchema(situationProperties);
            Map<String, Object> requiredSituationProperties = new java.util.LinkedHashMap<>(situationProperties);
            requiredSituationProperties.remove("sourceRefIds");
            properties = Map.of("coreProblem", stringSchema(),
                    "revelations", arraySchema(objectSchema(Map.of("required", booleanSchema(),
                            "sourceRefIds", sourceRefIdsSchema(allowedRefs)))),
                    "threat", objectSchema(Map.of("core", stringSchema(), "sourceRefIds", sourceRefIdsSchema(allowedRefs))),
                    "pressure", objectSchema(Map.of("progressionMaterial", stringSchema(),
                            "sourceRefIds", sourceRefIdsSchema(allowedRefs))),
                    "funnel", objectSchema(Map.of("meaning", stringSchema(),
                            "requiredPredicateLabels", arraySchema(stringSchema()),
                            "sourceRefIds", sourceRefIdsSchema(allowedRefs))),
                    "requiredSituations", fixedArraySchema(objectSchema(requiredSituationProperties), requiredSituations.size()),
                    "optionalSituations", arraySchema(situation),
                    "importantConsequenceLabels", arraySchema(stringSchema()),
                    "sourceRefIds", sourceRefIdsSchema(allowedRefs));
        } else {
            throw new IllegalArgumentException("unsupported initial situation task schema");
        }
        return mapper.valueToTree(objectSchema(properties));
    }

    private static Map<String, Object> requiredSituationSchema(List<ScenarioSourceReference> allowedRefs) {
        return objectSchema(Map.of("summary", boundedStringSchema(REQUIRED_SITUATION_SUMMARY_MAX_CODE_POINTS),
                "prerequisiteSummaries", arraySchema(boundedStringSchema(REQUIRED_SITUATION_SUMMARY_MAX_CODE_POINTS)),
                "isEnding", booleanSchema(), "sourceRefIds", requiredSourceRefIdsSchema(allowedRefs)));
    }

    private static Map<String, Object> sourceRefIdsSchema(List<ScenarioSourceReference> allowedRefs) {
        return Map.of("type", "array", "items", Map.of("type", "string", "enum", allowedRefs.stream()
                .map(ScenarioPackageStageArtifactAdapter::sourceRefId).toList()));
    }

    private static Map<String, Object> requiredSourceRefIdsSchema(List<ScenarioSourceReference> allowedRefs) {
        return Map.of("type", "array", "items", Map.of("type", "string", "enum", allowedRefs.stream()
                .map(ScenarioPackageStageArtifactAdapter::sourceRefId).toList()), "minItems", 1);
    }

    private static String sourceRefId(ScenarioSourceReference reference) {
        String identity = reference.knowledgeDocumentId().value() + "|" + reference.extractionVersion() + "|" + reference.locator();
        return UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static void resolveSourceRefIds(JsonNode node, List<ScenarioSourceReference> allowedRefs) {
        if (node.isObject()) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            if (object.has("sourceRefs")) {
                throw new IllegalStateException("source references must use server-issued source reference IDs");
            }
            if (object.has("sourceRefIds")) {
                JsonNode ids = object.remove("sourceRefIds");
                if (!ids.isArray()) {
                    throw new IllegalStateException("source reference ids are invalid");
                }
                var sourceRefs = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
                Map<String, ScenarioSourceReference> allowed = allowedRefs.stream().collect(
                        java.util.stream.Collectors.toMap(ScenarioPackageStageArtifactAdapter::sourceRefId, ref -> ref));
                for (JsonNode id : ids) {
                    if (!id.isTextual() || !allowed.containsKey(id.textValue()))
                        throw new IllegalStateException("source reference id is outside the allowed list");
                    ScenarioSourceReference reference = allowed.get(id.textValue());
                    var resolved = sourceRefs.addObject();
                    resolved.putObject("knowledgeDocumentId")
                            .put("value", reference.knowledgeDocumentId().value().toString());
                    resolved.put("extractionVersion", reference.extractionVersion());
                    resolved.put("locator", reference.locator());
                }
                object.set("sourceRefs", sourceRefs);
            }
            object.elements().forEachRemaining(child -> resolveSourceRefIds(child, allowedRefs));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> resolveSourceRefIds(child, allowedRefs));
        }
    }

    private String allowedSourceRefs(List<ScenarioSourceReference> allowedRefs) {
        return json(allowedRefs.stream().map(reference -> Map.of(
                "sourceRefId", sourceRefId(reference), "sourceRef", reference)).toList());
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()),
                "additionalProperties", false);
    }

    private static Map<String, Object> arraySchema(Object items) {
        return Map.of("type", "array", "items", items);
    }

    private static Map<String, Object> arraySchema(Object items, int minItems) {
        return Map.of("type", "array", "items", items, "minItems", minItems);
    }

    private static Map<String, Object> fixedArraySchema(Object items, int itemCount) {
        return Map.of("type", "array", "items", items, "minItems", itemCount, "maxItems", itemCount);
    }

    private static Map<String, Object> indexSchema() { return Map.of("type", "integer", "minimum", 0); }

    private static Map<String, Object> stringSchema() { return Map.of("type", "string", "minLength", 1); }
    private static Map<String, Object> boundedStringSchema(int maxCodePoints) {
        return Map.of("type", "string", "minLength", 1, "maxLength", maxCodePoints);
    }
    private static Map<String, Object> nullableStringSchema() { return Map.of("type", List.of("string", "null"), "minLength", 1); }

    private static Map<String, Object> nullableMapDefinitionSchema(Set<UUID> allowedMapIds) {
        List<Object> allowedValues = new ArrayList<>();
        allowedMapIds.stream().sorted().map(UUID::toString).forEach(allowedValues::add);
        allowedValues.add(null);
        return Map.of("type", List.of("string", "null"), "enum", allowedValues, "minLength", 1);
    }
    private static Map<String, Object> mapNeededSchema(Set<UUID> allowedMapIds) {
        return allowedMapIds.isEmpty() ? Map.of("type", "boolean", "enum", List.of(false)) : booleanSchema();
    }
    private static Map<String, Object> booleanSchema() { return Map.of("type", "boolean"); }
    private static Map<String, Object> enumSchema(String... values) { return Map.of("type", "string", "enum", List.of(values)); }
    private static Map<String, Object> enumSchema(List<String> values) { return Map.of("type", "string", "enum", List.copyOf(values)); }

    private String backbonePrompt(ScenarioPackage scenarioPackage, List<ScenarioSourceReference> evidence,
            List<RequiredSituation> required, List<List<String>> rules) {
        return "Create the adventure story backbone from the reviewed story situations and retrieved rule evidence. "
                + "Include every story-essential required situation and only prerequisites explicit in the source. "
                + "Add as many intermediate candidate stages as the story needs; do not use a fixed count or impose unstated order. "
                + "Return stages in story presentation order. For each stage return role, coreProblem, funnelSummary, requiredSituationSummaries, and sourceRefIds. "
                + "Each requiredSituationSummaries item must exactly match one summary in REQUIRED_STORY_SITUATIONS. Assign every required situation exactly once across the stages. The server assigns stage order and all IDs. "
                + "Stage order is presentation only and must not add prerequisite relationships. "
                + "Each sourceRefIds item must be an allowed sourceRefId from ALLOWED_SOURCE_REFS; do not return source objects or array positions.\nSCENARIO_CONTEXT="
                + promptStoryContext(scenarioPackage) + "\nREQUIRED_STORY_SITUATIONS=" + json(required.stream().map(item -> Map.of(
                        "summary", item.summary(), "isEnding", item.isEnding(),
                        "prerequisiteSummaries", item.prerequisiteSituationIds().stream()
                                .map(id -> required.stream().filter(parent -> parent.situationId().equals(id))
                                        .map(RequiredSituation::summary).findFirst().orElseThrow()).toList())).toList())
                + "\nCORE_RULEBOOK_EVIDENCE_BY_SITUATION=" + json(rulesBySituation(required, rules))
                + "\nALLOWED_SOURCE_REFS=" + allowedSourceRefs(evidence);
    }

    private String detailedPrompt(ScenarioPackage scenarioPackage, String stageId, List<ScenarioSourceReference> evidence,
            List<RequiredSituation> required, List<String> alreadyAssigned, List<List<String>> rules,
            UUID attachedMapImageId) {
        return "Prepare the situation definitions and supporting details for stage " + stageId + " from the extracted required story situations and retrieved rule evidence. "
                + "Include every required situation assigned to this stage. Add optional intermediate situations only where the story supports a distinct choice or route; zero, one, or several are valid. "
                + "Give every situation a specific event name, such as 'A goblin scout is on patrol', and a short scene description that names relevant place, actor, current action, motive or likely response, and an opening for player involvement when supported. Do not name a situation after an abstract purpose, and do not prescribe the ending, player action, or solution. "
                + "Do not create IDs or array positions. Return the required situations in requiredSituations, exactly in the order listed below, and optional candidates only in optionalSituations. The server assigns every ID and relationship and preserves extracted source references for required situations. "
                + "Do not output identity, provenance, prerequisite, or opening fields. "
                + "Use intents only to describe which kinds of story material a situation advances; do not add unstated prerequisites or make optional ingredients mandatory. "
                + "Return exactly one detail in requiredSituations for every required story situation, in the supplied order. The server preserves each required situation's extracted source references. Put only optional intermediate candidates in optionalSituations; sourceRefIds must cite allowed passages for extracted claims and be empty for GM supplements. "
                + "For every situation, explicitly decide whether its location needs the tactical map to understand spatial movement or combat positioning. When a required situation is a combat encounter at a location covered by an available map, set mapNeeded=true and select that map's ID; do not mark it mapless only because the adventure is entering the location now. Set mapNeeded=false for scenes where spatial positioning is not needed or no map is available. Use the supplied combat encounter locations and map asset details to match them. "
                + "For each situation, set combatScenarioIds to exactly the supplied source-backed encounter IDs that occur in that situation. Set an empty array for a situation with no mapped enemies. Never infer an encounter from narration or invent an ID. "
                + "mapNeeded (true only when a supplied map UUID is selected; false when no map UUID is supplied), and mapDefinitionId "
                + "(one supplied map UUID or null; required when mapNeeded is true). Return the fields in the supplied output schema. "
                + "Use only these map IDs: " + json(scenarioPackage.mapDefinitions().stream()
                        .filter(com.dndmaster.adventure.domain.scenario.MapDefinition::autoActivatable)
                        .map(m -> m.id()).toList())
                + "\nCOMBAT_ENCOUNTERS=" + json(scenarioPackage.scenarioModel().combatScenarios().stream()
                        .map(encounter -> Map.of("scenarioId", encounter.scenarioId(), "enemyKey", encounter.enemyKey(),
                                "name", encounter.displayName(), "count", encounter.count(), "location", encounter.location(),
                                "sourceRefs", encounter.sourceRefs())).toList())
                + "\nAVAILABLE_TACTICAL_MAPS=" + json(scenarioPackage.mapDefinitions().stream()
                        .filter(com.dndmaster.adventure.domain.scenario.MapDefinition::autoActivatable)
                        .map(map -> Map.of("mapDefinitionId", map.id(), "assetId", map.assetId(),
                                "assetLocator", map.assetLocator(), "source", map.source())).toList())
                + "\nATTACHED_MAP_IMAGE_MAP_DEFINITION_ID=" + (attachedMapImageId == null ? "none" : attachedMapImageId)
                + (attachedMapImageId == null ? "" : "\nUse the attached map image when evaluating spatial movement and positioning. The image belongs to this mapDefinitionId.")
                + "\nREQUIRED_STORY_SITUATIONS (summary, explicit ending marker, prerequisite count): "
                + json(required.stream().map(item -> Map.of("summary", item.summary(), "isEnding", item.isEnding(),
                        "prerequisiteCount", item.prerequisiteSituationIds().size())).toList())
                + "\nSCENARIO_CONTEXT=" + promptStoryContext(scenarioPackage)
                + "\nCORE_RULEBOOK_EVIDENCE_BY_SITUATION=" + json(rulesBySituation(required, rules))
                + "\nALREADY_ASSIGNED_REQUIRED_SITUATIONS=" + alreadyAssigned.size()
                + "\nDo not emit required situations other than the exact required list above."
                + "\nALLOWED_SOURCE_REFS=" + allowedSourceRefs(evidence);
    }

    private static void devLog(String pattern, Object... args) {
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            org.slf4j.LoggerFactory.getLogger(ScenarioPackageStageArtifactAdapter.class).info(pattern, args);
        }
    }

    private static List<String> requiredSituationInventory(List<RequiredSituation> situations) {
        return situations.stream().map(item -> item.situationId() + ":summaryChars=" + item.summary().length()
                + ":prerequisites=" + item.prerequisiteSituationIds() + ":sourceRefs="
                + sourceReferenceInventory(item.sourceRefs())).toList();
    }

    private static List<String> ruleEvidenceInventory(List<RequiredSituation> situations, List<List<String>> rules) {
        if (situations.size() != rules.size()) throw new IllegalStateException("rule evidence does not match required situations");
        List<String> inventory = new ArrayList<>();
        for (int index = 0; index < situations.size(); index++) {
            inventory.add(situations.get(index).situationId() + ":excerptCount=" + rules.get(index).size()
                    + ":excerptChars=" + rules.get(index).stream().mapToInt(String::length).sum());
        }
        return List.copyOf(inventory);
    }

    private static List<String> sourceReferenceInventory(List<ScenarioSourceReference> refs) {
        return refs.stream().map(ref -> ref.knowledgeDocumentId().value() + ":v" + ref.extractionVersion()
                + ":" + ref.locator()).toList();
    }

    private List<List<String>> lookupRules(ScenarioPackage scenarioPackage, SessionId sessionId, List<RequiredSituation> required) {
        if (storySource == null || required.isEmpty()) return required.stream().map(ignored -> List.<String>of()).toList();
        UUID ownerId = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("adventure session not found"))
                .ownerPlayerId().value();
        List<ScenarioBundleDocumentSelection> rulebooks = scenarioPackage.documents().stream()
                .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType())).toList();
        return storySource.findApplicableRules(rulebooks, ownerId, scenarioPackage.bundleId().value(), required);
    }

    private static List<Map<String, Object>> rulesBySituation(List<RequiredSituation> situations, List<List<String>> evidence) {
        if (situations.size() != evidence.size()) throw new IllegalStateException("rule evidence does not match required situations");
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < situations.size(); index++) result.add(Map.of(
                "summary", situations.get(index).summary(), "ruleExcerpts", evidence.get(index)));
        return List.copyOf(result);
    }

    private static void assignServerIdsAndRelations(JsonNode detailed, StageDetailedGenerationPort.Request request) {
        if (!detailed.isObject() || !detailed.path("situations").isArray()) {
            throw new IllegalStateException("detailed situation content is invalid");
        }
        var revelations = (com.fasterxml.jackson.databind.node.ArrayNode) detailed.path("revelations");
        List<String> revelationIds = new ArrayList<>();
        List<String> requiredRevelationIds = new ArrayList<>();
        for (int index = 0; index < revelations.size(); index++) {
            var revelation = (com.fasterxml.jackson.databind.node.ObjectNode) revelations.get(index);
            String id = serverAssignedId(request, "revelation", index);
            revelation.put("revelationId", id);
            revelationIds.add(id);
            if (revelation.path("required").asBoolean()) requiredRevelationIds.add(id);
        }
        JsonNode pressure = detailed.path("pressure");
        JsonNode funnel = detailed.path("funnel");
        String pressureId = serverAssignedId(request, "pressure", 0);
        String funnelId = serverAssignedId(request, "funnel", 0);
        ((com.fasterxml.jackson.databind.node.ObjectNode) pressure).put("pressureId", pressureId);
        ((com.fasterxml.jackson.databind.node.ObjectNode) funnel).put("funnelId", funnelId);
        List<String> predicateLabels = textArray(funnel.path("requiredPredicateLabels"));
        List<String> predicateIds = idsFor(request, "predicate", predicateLabels);
        ((com.fasterxml.jackson.databind.node.ObjectNode) funnel).set("requiredPredicateIds", mapperArray(predicateIds));
        ((com.fasterxml.jackson.databind.node.ObjectNode) funnel).remove("requiredPredicateLabels");
        List<String> consequenceLabels = textArray(detailed.path("importantConsequenceLabels"));
        List<String> consequenceIds = idsFor(request, "consequence", consequenceLabels);
        ((com.fasterxml.jackson.databind.node.ObjectNode) detailed).set("importantConsequenceIds", mapperArray(consequenceIds));
        ((com.fasterxml.jackson.databind.node.ObjectNode) detailed).remove("importantConsequenceLabels");
        ((com.fasterxml.jackson.databind.node.ObjectNode) funnel).set("requiredRevelationIds", mapperArray(requiredRevelationIds));
        JsonNode situations = detailed.path("situations");
        int linkedTargetCount = 0;
        for (int index = 0; index < situations.size(); index++) {
            JsonNode situation = situations.get(index);
            if (!situation.isObject() || !situation.path("intents").isArray()) {
                throw new IllegalStateException("detailed situation content is invalid");
            }
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) situation;
            var intents = new java.util.HashSet<String>();
            situation.path("intents").forEach(intent -> intents.add(requiredText(intent)));
            List<String> situationRevelations = intents.contains("REVELATION") ? revelationIds : List.of();
            List<String> situationPressures = intents.contains("PRESSURE") ? List.of(pressureId) : List.of();
            List<String> situationFunnels = intents.contains("FUNNEL_OPPORTUNITY") ? List.of(funnelId) : List.of();
            List<String> situationPredicates = intents.contains("FUNNEL_OPPORTUNITY") ? predicateIds : List.of();
            List<String> situationConsequences = intents.contains("IMPORTANT_CONSEQUENCE") ? consequenceIds : List.of();
            if (situationRevelations.isEmpty() && situationPressures.isEmpty() && situationFunnels.isEmpty()
                    && situationPredicates.isEmpty() && situationConsequences.isEmpty()) {
                situationFunnels = List.of(funnelId);
            }
            object.set("revelationIds", mapperArray(situationRevelations));
            object.set("pressureIds", mapperArray(situationPressures));
            object.set("funnelIds", mapperArray(situationFunnels));
            object.set("funnelPredicateIds", mapperArray(situationPredicates));
            object.set("importantConsequenceIds", mapperArray(situationConsequences));
            linkedTargetCount += situationRevelations.size() + situationPressures.size() + situationFunnels.size()
                    + situationPredicates.size() + situationConsequences.size();
        }
        devLog("dev_initial_situation_ids outcome=completed adventureId={} stageId={} expectedAgentIdentifierFields=none actualAgentIdentifierFields=none revelationCount={} predicateCount={} consequenceCount={} situationCount={} linkedTargetCount={} relationPolicy=required_ids_reused_optional_ids_server_assigned",
                request.adventureId().value(), request.stageId(), revelations.size(), predicateIds.size(),
                consequenceIds.size(), situations.size(), linkedTargetCount);
    }

    private static String serverAssignedId(StageDetailedGenerationPort.Request request, String kind, int index) {
        String identity = request.adventureId().value() + "|" + request.stageId() + "|"
                + request.backboneRevision() + "|1|" + kind + "|" + index;
        return UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static List<String> idsFor(StageDetailedGenerationPort.Request request, String kind, List<String> labels) {
        return java.util.stream.IntStream.range(0, labels.size())
                .mapToObj(index -> serverAssignedId(request, kind, index) + ":" + labels.get(index)).toList();
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode mapperArray(List<String> values) {
        var array = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        values.forEach(array::add);
        return array;
    }

    private static void resolveSupplementedSituationIds(JsonNode detailed) {
        JsonNode situations = detailed.path("situations");
        if (!situations.isArray()) throw new IllegalStateException("detailed situation identities are invalid");
        for (int index = 0; index < situations.size(); index++) {
            JsonNode situation = situations.get(index);
            if (!situation.isObject() || !situation.path("provenance").isTextual()
                    || !situation.path("required").isBoolean() || !situation.path("situationId").isTextual()) {
                throw new IllegalStateException("detailed situation identities are invalid");
            }
        }
    }

    private void resolveRequiredSituations(JsonNode detailed, StageDetailedGenerationPort.Request request) {
        JsonNode situations = detailed.path("situations");
        if (!situations.isArray() || situations.size() < request.requiredSituations().size())
            throw new IllegalStateException("detailed situation count does not preserve all assigned required situations");
        int openingCount = 0;
        Set<String> allowedCombatScenarioIds = loadPackage(request.scenarioPackageId()).scenarioModel() == null ? Set.of()
                : loadPackage(request.scenarioPackageId()).scenarioModel().combatScenarios().stream()
                        .map(com.dndmaster.adventure.domain.scenario.CombatScenarioDefinition::scenarioId)
                        .collect(java.util.stream.Collectors.toSet());
        for (int index = 0; index < situations.size(); index++) {
            JsonNode situation = situations.get(index);
            if (!situation.isObject() || !situation.path("title").isTextual()
                    || !situation.path("description").isTextual() || !situation.path("sourceRefs").isArray()) {
                throw new IllegalStateException("detailed situation identities are invalid");
            }
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) situation;
            JsonNode combatScenarioIds = situation.path("combatScenarioIds");
            if (!combatScenarioIds.isArray()) throw new IllegalStateException("situation combat scenario associations are required");
            List<String> associatedCombatScenarios = textArray(combatScenarioIds);
            if (associatedCombatScenarios.stream().distinct().count() != associatedCombatScenarios.size()
                    || !allowedCombatScenarioIds.containsAll(associatedCombatScenarios))
                throw new IllegalStateException("situation references an unknown or duplicate combat scenario");
            List<ScenarioSourceReference> generatedRefs = mapper.convertValue(situation.path("sourceRefs"),
                    mapper.getTypeFactory().constructCollectionType(List.class, ScenarioSourceReference.class));
            boolean required = index < request.requiredSituations().size();
            object.put("required", required);
            object.put("opening", false);
            object.put("isEnding", false);
            if (required) {
                RequiredSituation assigned = request.requiredSituations().get(index);
                if (!java.util.Set.copyOf(generatedRefs).equals(java.util.Set.copyOf(assigned.sourceRefs()))) {
                    devLog("dev_initial_situation_source_refs outcome=mismatch adventureId={} stageId={} detailOrder={} situationId={} expectedRefCount={} actualRefCount={} expectedRefs={} actualRefs={} failureKind=required_source_reference_set_mismatch nextAction=reject_preparation",
                            request.adventureId().value(), request.stageId(), index, assigned.situationId(),
                            assigned.sourceRefs().size(), generatedRefs.size(), sourceReferenceInventory(assigned.sourceRefs()),
                            sourceReferenceInventory(generatedRefs));
                    throw new IllegalStateException("required situation source references do not match the extracted source");
                }
                boolean opening = request.openingSituationRequired() && assigned.prerequisiteSituationIds().isEmpty();
                if (opening) openingCount++;
                object.put("situationId", assigned.situationId());
                object.put("opening", opening);
                object.put("isEnding", assigned.isEnding());
                object.put("provenance", SituationProvenance.SOURCE_EXTRACTED.name());
                object.set("prerequisiteSituationIds", mapper.valueToTree(assigned.prerequisiteSituationIds()));
                object.set("sourceRefs", mapper.valueToTree(assigned.sourceRefs()));
                devLog("dev_initial_situation_ids outcome=reused_required adventureId={} stageId={} detailOrder={} expectedRequiredOrder={} situationId={} isEnding={} result=matched",
                        request.adventureId().value(), request.stageId(), index, index, assigned.situationId(),
                        assigned.isEnding());
            } else {
                object.put("situationId", serverAssignedId(request, "situation", index));
                object.put("provenance", generatedRefs.isEmpty()
                        ? SituationProvenance.GM_SUPPLEMENTED.name() : SituationProvenance.SOURCE_EXTRACTED.name());
                object.set("prerequisiteSituationIds", mapperArray(List.of()));
            }
        }
        if (request.openingSituationRequired() != (openingCount == 1))
            throw new IllegalStateException("prepared opening must be the unique extracted graph root");
        if (openingCount > 1) throw new IllegalStateException("prepared situation set has multiple openings");
        devLog("dev_initial_situation_ids outcome=assigned adventureId={} stageId={} expectedRequiredSituationCount={} actualRequiredSituationCount={} actualSituationCount={} openingCount={} result=success",
                request.adventureId().value(), request.stageId(), request.requiredSituations().size(),
                request.requiredSituations().size(), situations.size(), openingCount);
    }

    private static List<String> textArray(JsonNode values) {
        if (!values.isArray()) throw new IllegalStateException("detailed situation canonical targets are invalid");
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) result.add(requiredText(value));
        return List.copyOf(result);
    }

    private static String requiredText(JsonNode value) {
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("detailed situation canonical target is invalid");
        }
        return value.textValue();
    }


    private List<StorySourcePreviewPort.Passage> sourcePassages(ScenarioPackage scenarioPackage, UUID ownerId) {
        if (storySource == null) return List.of();
        return scenarioPackage.documents().stream()
                .filter(document -> document.role() == com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.MAIN_SCENARIO)
                .flatMap(document -> storySource.load(document, ownerId, scenarioPackage.bundleId().value()).stream()).toList();
    }

    private List<RequiredSituation> extractRequiredSituations(StageBackboneGenerationPort.Request request,
            ScenarioPackage scenarioPackage, List<StorySourcePreviewPort.Passage> passages) {
        if (passages.isEmpty()) {
            if (scenarioPackage.documents().stream().anyMatch(document -> document.role()
                    == com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.MAIN_SCENARIO)) {
                throw new IllegalStateException("story RAG returned no candidate passages");
            }
            return List.of();
        }
        List<ScenarioSourceReference> allowedRefs = passages.stream()
                .map(StorySourcePreviewPort.Passage::sourceRef).distinct().toList();
        long extractionStartedAt = System.nanoTime();
        JsonNode extracted = generate(request.sessionId(), request.adventureId().value(), request.scenarioPackageId(),
                "required-extract", "From only these RAG-retrieved story passages, extract story-essential situations and explicit prerequisites. "
                        + "Treat these as retrieved candidates, not the complete story; do not claim that no other required situation exists. "
                        + "Return each situation once with a unique concise summary, prerequisiteSummaries using exact predecessor summaries, isEnding true only when the source explicitly identifies an ending, and sourceRefIds citing supplied passages. "
                        + "The required situations must form one acyclic graph with exactly one root and at least one explicit ending. Multiple predecessors and merges are allowed. Every non-ending situation must reach an explicit ending; ending situations have no successors. Do not infer endings from missing successors. "
                        + "Do not create IDs, array positions, or source objects. Return {requiredSituations:[...]}.\n"
                        + "RETRIEVED_STORY_PASSAGES=" + json(passages)
                        + "\nALLOWED_SOURCE_REFS=" + allowedSourceRefs(allowedRefs), allowedRefs);
        try {
            List<RequiredSituation> required = requiredSituations(extracted, allowedRefs, request);
            validateRequiredSituations(required, passages);
            devLog("dev_initial_situation_extraction outcome=completed adventureId={} retrievedPassageCount={} actualRequiredSituationCount={} sourceRefCount={} responseChars={} elapsedMs={}",
                    request.adventureId().value(), passages.size(), required.size(), allowedRefs.size(), extracted.toString().length(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - extractionStartedAt));
            return required;
        } catch (RuntimeException failure) {
            devLog("dev_initial_situation_extraction outcome=failed adventureId={} retrievedPassageCount={} actualRequiredSituationCount={} sourceRefCount={} responseChars={} responseFingerprint={} failureClass={} elapsedMs={} nextAction=reject_preparation",
                    request.adventureId().value(), passages.size(), extracted.path("requiredSituations").isArray()
                            ? extracted.path("requiredSituations").size() : -1,
                    allowedRefs.size(), extracted.toString().length(), com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics
                            .fingerprint(extracted.toString()), failure.getClass().getSimpleName(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - extractionStartedAt));
            throw failure;
        }
    }

    private List<RequiredSituation> requiredSituations(JsonNode response, List<ScenarioSourceReference> allowedRefs,
            StageBackboneGenerationPort.Request request) {
        if (!response.isObject() || !response.path("requiredSituations").isArray()) {
            throw new IllegalStateException("GM omitted required situation list");
        }
        JsonNode resolved = response.path("requiredSituations").deepCopy();
        resolveSourceRefIds(resolved, allowedRefs);
        if (!resolved.isArray()) throw new IllegalStateException("GM omitted required situation list");
        List<String> ids = java.util.stream.IntStream.range(0, resolved.size())
                .mapToObj(index -> requiredSituationId(request.adventureId(), request.scenarioPackageId(), index)).toList();
        Map<String, String> idBySummary = new java.util.HashMap<>();
        for (int index = 0; index < resolved.size(); index++) {
            if (idBySummary.put(requiredText(resolved.get(index).path("summary")), ids.get(index)) != null)
                throw new IllegalStateException("required situation summaries must be unique");
        }
        List<RequiredSituation> required = new ArrayList<>();
        for (int index = 0; index < resolved.size(); index++) {
            JsonNode situation = resolved.get(index);
            JsonNode prerequisiteSummaries = situation.path("prerequisiteSummaries");
            if (!prerequisiteSummaries.isArray()) throw new IllegalStateException("required situation prerequisite summaries are invalid");
            List<String> prerequisites = new ArrayList<>();
            for (JsonNode prerequisiteSummary : prerequisiteSummaries) {
                if (!prerequisiteSummary.isTextual() || !idBySummary.containsKey(prerequisiteSummary.textValue()))
                    throw new IllegalStateException("required situation prerequisite summary is unknown");
                String prerequisiteId = idBySummary.get(prerequisiteSummary.textValue());
                if (prerequisiteId.equals(ids.get(index))) throw new IllegalStateException("required situation cannot depend on itself");
                prerequisites.add(prerequisiteId);
            }
            List<ScenarioSourceReference> sourceRefs = mapper.convertValue(situation.path("sourceRefs"),
                    mapper.getTypeFactory().constructCollectionType(List.class, ScenarioSourceReference.class));
            if (!situation.path("isEnding").isBoolean()) throw new IllegalStateException("required situation ending marker is invalid");
            required.add(new RequiredSituation(ids.get(index), requiredText(situation.path("summary")), prerequisites,
                    situation.path("isEnding").booleanValue(), sourceRefs));
        }
        devLog("dev_initial_situation_ids outcome=assigned adventureId={} task=required-extract expectedAgentIdentifierFields=none actualAgentIdentifierFields=none requiredSituationCount={} prerequisiteCount={} explicitEndingCount={} idPolicy=server_assigned",
                request.adventureId().value(), required.size(), required.stream().mapToInt(item -> item.prerequisiteSituationIds().size()).sum(),
                required.stream().filter(RequiredSituation::isEnding).count());
        return List.copyOf(required);
    }

    private static String requiredSituationId(AdventureId adventureId, UUID packageId, int index) {
        String identity = adventureId.value() + "|" + packageId + "|required-situation|" + index;
        return UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static void assignBackboneIdsAndRelations(JsonNode backbone, StageBackboneGenerationPort.Request request,
            List<RequiredSituation> requiredSituations) {
        JsonNode stages = backbone.path("stages");
        if (!backbone.isObject() || !stages.isArray() || stages.isEmpty()) {
            throw new IllegalStateException("generated story backbone is invalid");
        }
        for (int stageIndex = 0; stageIndex < stages.size(); stageIndex++) {
            JsonNode stage = stages.get(stageIndex);
            if (!stage.isObject() || !stage.path("requiredSituationSummaries").isArray()) {
                throw new IllegalStateException("generated story backbone assignment is invalid");
            }
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) stage;
            object.put("stageId", "stage-" + (stageIndex + 1));
            object.put("order", stageIndex + 1);
            var requiredIds = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            Set<String> stageRequiredIds = new java.util.HashSet<>();
            Map<String, RequiredSituation> allowed = requiredSituations.stream().collect(
                    java.util.stream.Collectors.toMap(RequiredSituation::summary, item -> item));
            for (JsonNode summary : stage.path("requiredSituationSummaries")) {
                if (!summary.isTextual() || !allowed.containsKey(summary.textValue()))
                    throw new IllegalStateException("generated story backbone summary is outside the required situation list");
                String id = allowed.get(summary.textValue()).situationId();
                if (!stageRequiredIds.add(id)) throw new IllegalStateException("generated story backbone repeats a required situation");
                requiredIds.add(id);
            }
            object.set("requiredSituationIds", requiredIds);
            object.remove("requiredSituationSummaries");
        }
        Set<String> assigned = new java.util.HashSet<>();
        for (JsonNode stage : stages) for (JsonNode id : stage.path("requiredSituationIds"))
            if (!assigned.add(id.asText())) throw new IllegalStateException("backbone assigned a required situation more than once");
        if (assigned.size() != requiredSituations.size())
            throw new IllegalStateException("generated backbone must assign every required situation exactly once");
        devLog("dev_initial_situation_ids outcome=assigned adventureId={} expectedAgentIdentifierFields=none actualAgentIdentifierFields=none stageCount={} assignmentReferenceField=requiredSituationSummaries assignedRequiredSituationCount={}",
                request.adventureId().value(), stages.size(), assigned.size());
    }

    static void validateRequiredSituations(List<RequiredSituation> required,
            List<StorySourcePreviewPort.Passage> passages) {
        if (required.isEmpty()) throw new IllegalStateException("story RAG returned no required situations");
        Set<ScenarioSourceReference> allowed = passages.stream().map(StorySourcePreviewPort.Passage::sourceRef)
                .collect(java.util.stream.Collectors.toSet());
        if (required.stream().anyMatch(item -> item.sourceRefs().isEmpty()
                || item.sourceRefs().stream().anyMatch(ref -> !allowed.contains(ref)))) {
            throw new IllegalStateException("required situation source reference is outside the RAG results");
        }
        long roots = required.stream().filter(item -> item.prerequisiteSituationIds().isEmpty()).count();
        long endings = required.stream().filter(RequiredSituation::isEnding).count();
        String failure = "none";
        try { com.dndmaster.adventure.domain.scenario.StageBackbone.validateRequiredGraph(required); }
        catch (IllegalArgumentException invalid) { failure = invalid.getMessage(); }
        devLog("dev_initial_situation_graph_validation outcome={} situationCount={} expectedRootCount=1 actualRootCount={} expectedEndingCountAtLeast=1 actualEndingCount={} multipleParentCount={} failureKind={} nextAction={}",
                failure.equals("none") ? "completed" : "failed", required.size(), roots, endings,
                required.stream().filter(item -> item.prerequisiteSituationIds().size() > 1).count(),
                failure, failure.equals("none") ? "continue_preparation" : "reject_preparation");
        if (!failure.equals("none")) throw new IllegalStateException("required situation graph is invalid: " + failure);
    }

    private static Set<String> cyclicSituationIds(List<RequiredSituation> situations) {
        Map<String, List<String>> prerequisites = situations.stream().collect(java.util.stream.Collectors.toMap(
                RequiredSituation::situationId, RequiredSituation::prerequisiteSituationIds));
        Set<String> visiting = new java.util.HashSet<>();
        Set<String> visited = new java.util.HashSet<>();
        Set<String> cyclic = new java.util.HashSet<>();
        List<String> path = new ArrayList<>();
        prerequisites.keySet().forEach(id -> collectCycle(id, prerequisites, visiting, visited, path, cyclic));
        return cyclic;
    }

    private static void collectCycle(String id, Map<String, List<String>> prerequisites, Set<String> visiting,
            Set<String> visited, List<String> path, Set<String> cyclic) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) {
            cyclic.addAll(path.subList(path.indexOf(id), path.size()));
            return;
        }
        path.add(id);
        prerequisites.get(id).stream().filter(prerequisites::containsKey)
                .forEach(prerequisite -> collectCycle(prerequisite, prerequisites, visiting, visited, path, cyclic));
        path.remove(path.size() - 1);
        visiting.remove(id);
        visited.add(id);
    }

    private List<ScenarioSourceReference> references(JsonNode node) {
        return mapper.convertValue(node, mapper.getTypeFactory().constructCollectionType(List.class, ScenarioSourceReference.class));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("initial situation prompt serialization failed", failure); }
    }

    private ScenarioPackage loadPackage(UUID scenarioPackageId) {
        return packages.findById(scenarioPackageId).orElseThrow(() -> new IllegalStateException("scenario package not found"));
    }

    private static String openingProblem(ScenarioPackage scenarioPackage) {
        return text(scenarioPackage.scenarioModel().objectives(), scenarioPackage.scenarioModel().startingSituation());
    }

    private static String promptStoryContext(ScenarioPackage scenarioPackage) {
        String context = openingProblem(scenarioPackage);
        int codePoints = context.codePointCount(0, context.length());
        return codePoints <= 2000 ? context : context.substring(0, context.offsetByCodePoints(0, 2000));
    }

    private static String openingThreat(ScenarioPackage scenarioPackage) {
        var objectives = scenarioPackage.scenarioModel().objectives();
        var objectiveRefs = objectives.isEmpty() ? List.<ScenarioSourceReference>of() : objectives.get(0).sourceRefs();
        return scenarioPackage.scenarioModel().combatScenarios().stream()
                .sorted(java.util.Comparator.comparing((com.dndmaster.adventure.domain.scenario.CombatScenarioDefinition encounter) ->
                        encounter.sourceRefs().stream().noneMatch(objectiveRefs::contains)))
                .findFirst()
                .map(encounter -> "시작 장면과 연결된 위협: " + encounter.displayName() + " (" + encounter.location() + ")")
                .orElse("시작 장면의 문제와 관련된 위협은 아직 확인되지 않았다");
    }

    private static String openingGoal(ScenarioPackage scenarioPackage) {
        boolean hasQuestGiver = scenarioPackage.scenarioModel().actors().stream().anyMatch(actor ->
                actor.type().equalsIgnoreCase("quest-giver")
                        || String.valueOf(actor.attributes().getOrDefault("role", "")).equalsIgnoreCase("quest-giver"));
        return hasQuestGiver
                ? "플레이어에게 당면한 의뢰가 제시되고, 수락 여부와 다음 행동은 플레이어가 정한다"
                : "플레이어는 현재 장면의 문제와 위협을 파악하고, 다음 행동을 직접 정한다";
    }

    private static String text(List<ScenarioModelElement> elements, String fallback) {
        return elements.stream().findFirst().map(element -> String.valueOf(element.attributes().getOrDefault("value",
                element.attributes().getOrDefault("description", element.elementId())))).filter(value -> !value.isBlank())
                .orElse(fallback == null || fallback.isBlank() ? "모험의 시작 장면에서 해결할 문제가 아직 드러나지 않았다" : fallback);
    }

    private static Stream<ScenarioModelElement> elements(ScenarioPackage scenarioPackage) {
        var model = scenarioPackage.scenarioModel();
        if (model == null) return Stream.empty();
        return Stream.of(model.actors(), model.locations(), model.objectives(), model.revelations(), model.encounters(),
                model.relationships(), model.resolutionCriteria()).flatMap(List::stream);
    }
}

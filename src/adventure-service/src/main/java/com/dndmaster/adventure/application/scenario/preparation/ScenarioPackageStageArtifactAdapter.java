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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages) {
        this(packages, null, null, null, null, null);
    }

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages, InitialSituationGmPort gm,
            GmProviderBindingRepository bindings, AdventureSessionRepository sessions, ObjectMapper mapper) {
        this(packages, gm, bindings, sessions, mapper, null);
    }

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages, InitialSituationGmPort gm,
            GmProviderBindingRepository bindings, AdventureSessionRepository sessions, ObjectMapper mapper,
            StorySourcePreviewPort storySource) {
        this.packages = java.util.Objects.requireNonNull(packages, "scenario package repository must not be null");
        this.gm = gm;
        this.bindings = bindings;
        this.sessions = sessions;
        this.mapper = mapper;
        this.storySource = storySource;
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
            JsonNode generated = generate(request.sessionId(), request.adventureId().value(),
                    request.scenarioPackageId(), "backbone", backbonePrompt(scenarioPackage, request.evidence(), required, rules),
                    request.evidence());
            JsonNode resolved = generated.deepCopy();
            resolveSourceRefIndexes(resolved, request.evidence());
            List<StageBackboneEntry> stages = mapper.convertValue(resolved.path("stages"),
                    mapper.getTypeFactory().constructCollectionType(List.class, StageBackboneEntry.class));
            return new StageBackbone(request.scenarioPackageId(), 1, stages, request.evidence(), required);
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
            JsonNode generated = generate(request.sessionId(), request.adventureId().value(),
                    request.scenarioPackageId(), "stage:" + request.stageId(),
                    detailedPrompt(scenarioPackage, request.stageId(), request.evidence(), request.requiredSituations(),
                            request.alreadyAssignedRequiredSituationIds(), rules)
                            + (request.openingSituationRequired() ? " The openingSituationIndex must select one situation because this is the first stage." : ""),
                    request.evidence(), request.openingSituationRequired(), scenarioPackage.mapDefinitions().stream()
                            .map(com.dndmaster.adventure.domain.scenario.MapDefinition::id)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
            var content = generated.deepCopy();
            resolveSituationTargetIndexes(content);
            resolveSourceRefIndexes(content, request.evidence());
            resolveOpeningSituationIndex(content, request.openingSituationRequired());
            resolveSupplementedSituationIds(content, request.stageId(), request.requiredSituations());
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("scenarioPackageId", request.scenarioPackageId().toString());
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("backboneRevision", request.backboneRevision());
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("stageId", request.stageId());
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("revision", 1);
            return mapper.convertValue(content, DetailedStage.class);
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

    private SituationDefinition fallbackCandidate(String id, String revelationId, List<ScenarioSourceReference> evidence) {
        return new SituationDefinition(id, List.of(StageIntent.REVELATION), List.of(revelationId),
                List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                evidence.isEmpty() ? SituationProvenance.GM_SUPPLEMENTED : SituationProvenance.SOURCE_EXTRACTED,
                null, evidence);
    }

    private JsonNode generate(SessionId sessionId, UUID adventureId, UUID packageId, String task, String taskPrompt,
            List<ScenarioSourceReference> allowedRefs) {
        return generate(sessionId, adventureId, packageId, task, taskPrompt, allowedRefs, false);
    }

    private JsonNode generate(SessionId sessionId, UUID adventureId, UUID packageId, String task, String taskPrompt,
            List<ScenarioSourceReference> allowedRefs, boolean openingSituationRequired) {
        return generate(sessionId, adventureId, packageId, task, taskPrompt, allowedRefs,
                openingSituationRequired, Set.of());
    }

    private JsonNode generate(SessionId sessionId, UUID adventureId, UUID packageId, String task, String taskPrompt,
            List<ScenarioSourceReference> allowedRefs, boolean openingSituationRequired, Set<UUID> allowedMapIds) {
        var session = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("adventure session not found"));
        var binding = bindings.current(sessionId.value()).orElseThrow(() -> new IllegalStateException("GM provider selection not found"));
        String prompt = "ROLE=INITIAL_SITUATION_PREPARATION\nTASK=" + task + "\n" + taskPrompt
                + "\nOUTPUT_CONTRACT=Return exactly one JSON object matching the requested fields. No markdown.";
        return gm.generate(new InitialSituationGmPort.Request(session.ownerPlayerId().value(), sessionId.value(),
                binding.selection().endpointId(), binding.selection().provider(), binding.selection().model(),
                binding.selection().reasoning(), "initial-situation:" + adventureId + ":" + packageId + ":" + task,
                prompt, outputSchema(task, allowedRefs, openingSituationRequired, allowedMapIds)));
    }

    private JsonNode outputSchema(String task, List<ScenarioSourceReference> allowedRefs) {
        return outputSchema(task, allowedRefs, false);
    }

    private JsonNode outputSchema(String task, List<ScenarioSourceReference> allowedRefs,
            boolean openingSituationRequired) {
        return outputSchema(task, allowedRefs, openingSituationRequired, Set.of());
    }

    private JsonNode outputSchema(String task, List<ScenarioSourceReference> allowedRefs,
            boolean openingSituationRequired, Set<UUID> allowedMapIds) {
        Map<String, Object> properties;
        if (task.equals("required-extract")) {
            properties = Map.of("requiredSituations", arraySchema(requiredSituationSchema(allowedRefs)));
        } else if (task.equals("backbone")) {
            properties = Map.of("stages", arraySchema(objectSchema(Map.of(
                            "stageId", stringSchema(), "order", Map.of("type", "integer", "minimum", 1),
                            "role", stringSchema(), "coreProblem", stringSchema(), "funnelSummary", stringSchema(),
                            "sourceRefIndexes", sourceRefIndexesSchema(allowedRefs))), 1),
                    "sourceRefIndexes", sourceRefIndexesSchema(allowedRefs));
        } else if (task.startsWith("stage:")) {
            Map<String, Object> situationProperties = Map.ofEntries(
                    Map.entry("situationId", stringSchema()),
                    Map.entry("intents", arraySchema(enumSchema("REVELATION", "PRESSURE", "FUNNEL_OPPORTUNITY", "IMPORTANT_CONSEQUENCE"), 1)),
                    Map.entry("revelationIndexes", arraySchema(indexSchema())),
                    Map.entry("pressureIndexes", arraySchema(singleTargetIndexSchema())),
                    Map.entry("funnelIndexes", arraySchema(singleTargetIndexSchema())),
                    Map.entry("funnelPredicateIndexes", arraySchema(Map.of("type", "integer", "minimum", 0))),
                    Map.entry("importantConsequenceIndexes", arraySchema(indexSchema())),
                    Map.entry("required", booleanSchema()), Map.entry("prerequisiteSituationIds", arraySchema(stringSchema())),
                    Map.entry("provenance", enumSchema("SOURCE_EXTRACTED", "GM_SUPPLEMENTED")),
                    Map.entry("mapDefinitionId", nullableMapDefinitionSchema(allowedMapIds)),
                    Map.entry("sourceRefIndexes", sourceRefIndexesSchema(allowedRefs)));
            Map<String, Object> situation = situationSchema(situationProperties);
            properties = Map.of("coreProblem", stringSchema(),
                    "revelations", arraySchema(objectSchema(Map.of("revelationId", stringSchema(),
                            "required", booleanSchema(), "sourceRefIndexes", sourceRefIndexesSchema(allowedRefs)))),
                    "threat", objectSchema(Map.of("core", stringSchema(), "sourceRefIndexes", sourceRefIndexesSchema(allowedRefs))),
                    "pressure", objectSchema(Map.of("pressureId", stringSchema(), "progressionMaterial", stringSchema(),
                            "sourceRefIndexes", sourceRefIndexesSchema(allowedRefs))),
                    "funnel", objectSchema(Map.of("funnelId", stringSchema(), "meaning", stringSchema(),
                            "requiredRevelationIndexes", arraySchema(indexSchema()), "requiredPredicateIds", arraySchema(stringSchema()),
                            "sourceRefIndexes", sourceRefIndexesSchema(allowedRefs))),
                    "situations", arraySchema(situation),
                    "openingSituationIndex", openingSituationRequired ? indexSchema()
                            : Map.of("type", List.of("integer", "null"), "minimum", 0),
                    "importantConsequenceIds", arraySchema(stringSchema()),
                    "sourceRefIndexes", sourceRefIndexesSchema(allowedRefs));
        } else {
            throw new IllegalArgumentException("unsupported initial situation task schema");
        }
        return mapper.valueToTree(objectSchema(properties));
    }

    private static Map<String, Object> requiredSituationSchema(List<ScenarioSourceReference> allowedRefs) {
        return objectSchema(Map.of("situationId", stringSchema(), "summary", boundedStringSchema(REQUIRED_SITUATION_SUMMARY_MAX_CODE_POINTS),
                "prerequisiteSituationIds", arraySchema(stringSchema()),
                "sourceRefIndexes", requiredSourceRefIndexesSchema(allowedRefs)));
    }

    private static Map<String, Object> sourceRefIndexesSchema(List<ScenarioSourceReference> allowedRefs) {
        if (allowedRefs.isEmpty()) {
            return Map.of("type", "array", "items", indexSchema(), "maxItems", 0);
        }
        return Map.of("type", "array", "items", sourceRefIndexSchema(allowedRefs));
    }

    private static Map<String, Object> requiredSourceRefIndexesSchema(List<ScenarioSourceReference> allowedRefs) {
        if (allowedRefs.isEmpty()) return sourceRefIndexesSchema(allowedRefs);
        return Map.of("type", "array", "items", sourceRefIndexSchema(allowedRefs), "minItems", 1);
    }

    private static Map<String, Object> sourceRefIndexSchema(List<ScenarioSourceReference> allowedRefs) {
        return Map.of("type", "integer", "minimum", 0, "maximum", allowedRefs.size() - 1);
    }

    private static void resolveSourceRefIndexes(JsonNode node, List<ScenarioSourceReference> allowedRefs) {
        if (node.isObject()) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            if (object.has("sourceRefs")) {
                throw new IllegalStateException("source references must use canonical source indexes");
            }
            if (object.has("sourceRefIndexes")) {
                JsonNode indexes = object.remove("sourceRefIndexes");
                if (!indexes.isArray()) {
                    throw new IllegalStateException("source reference indexes are invalid");
                }
                var sourceRefs = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
                for (JsonNode index : indexes) {
                    if (!index.isIntegralNumber() || !index.canConvertToInt() || index.intValue() < 0
                            || index.intValue() >= allowedRefs.size()) {
                        throw new IllegalStateException("source reference index is outside the allowed list");
                    }
                    ScenarioSourceReference reference = allowedRefs.get(index.intValue());
                    var resolved = sourceRefs.addObject();
                    resolved.putObject("knowledgeDocumentId")
                            .put("value", reference.knowledgeDocumentId().value().toString());
                    resolved.put("extractionVersion", reference.extractionVersion());
                    resolved.put("locator", reference.locator());
                }
                object.set("sourceRefs", sourceRefs);
            }
            object.elements().forEachRemaining(child -> resolveSourceRefIndexes(child, allowedRefs));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> resolveSourceRefIndexes(child, allowedRefs));
        }
    }

    private String numberedSourceRefs(List<ScenarioSourceReference> allowedRefs) {
        return json(java.util.stream.IntStream.range(0, allowedRefs.size())
                .mapToObj(index -> Map.of("index", index, "sourceRef", allowedRefs.get(index))).toList());
    }

    private static Map<String, Object> situationSchema(Map<String, Object> properties) {
        List<Map<String, Object>> alternatives = new ArrayList<>();
        for (String target : List.of("revelationIndexes", "pressureIndexes", "funnelIndexes",
                "funnelPredicateIndexes", "importantConsequenceIndexes")) {
            for (String provenance : List.of("SOURCE_EXTRACTED", "GM_SUPPLEMENTED")) {
                    Map<String, Object> branchProperties = new LinkedHashMap<>(properties);
                    Map<String, Object> targetSchema = new LinkedHashMap<>();
                    ((Map<?, ?>) properties.get(target)).forEach((key, value) -> targetSchema.put((String) key, value));
                    targetSchema.put("minItems", 1);
                    branchProperties.put(target, targetSchema);
                branchProperties.put("provenance", Map.of("type", "string", "enum", List.of(provenance)));
                if ("GM_SUPPLEMENTED".equals(provenance)) {
                    branchProperties.put("sourceRefIndexes", Map.of(
                            "type", "array", "items", indexSchema(), "maxItems", 0));
                }
                alternatives.add(objectSchema(branchProperties));
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>(objectSchema(properties));
        schema.put("anyOf", alternatives);
        return schema;
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

    private static Map<String, Object> indexSchema() { return Map.of("type", "integer", "minimum", 0); }
    private static Map<String, Object> singleTargetIndexSchema() {
        return Map.of("type", "integer", "minimum", 0, "maximum", 0);
    }

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
    private static Map<String, Object> booleanSchema() { return Map.of("type", "boolean"); }
    private static Map<String, Object> enumSchema(String... values) { return Map.of("type", "string", "enum", List.of(values)); }

    private String backbonePrompt(ScenarioPackage scenarioPackage, List<ScenarioSourceReference> evidence,
            List<RequiredSituation> required, List<List<String>> rules) {
        return "Create the adventure story backbone from the reviewed story situations and retrieved rule evidence. "
                + "Include every story-essential required situation and only prerequisites explicit in the source. "
                + "Add as many intermediate candidate stages as the story needs; do not use a fixed count or impose unstated order. "
                + "Each stage needs stageId, contiguous order, role, coreProblem, funnelSummary, and sourceRefIndexes. "
                + "Return stages and sourceRefIndexes. Every sourceRefIndexes array contains only integer indexes from "
                + "NUMBERED_ALLOWED_SOURCE_REFS and must not contain source reference objects.\nSCENARIO_CONTEXT="
                + promptStoryContext(scenarioPackage) + "\nREQUIRED_STORY_SITUATIONS=" + json(required)
                + "\nCORE_RULEBOOK_EVIDENCE_BY_SITUATION=" + json(rulesBySituation(required, rules))
                + "\nNUMBERED_ALLOWED_SOURCE_REFS=" + numberedSourceRefs(evidence);
    }

    private String detailedPrompt(ScenarioPackage scenarioPackage, String stageId, List<ScenarioSourceReference> evidence,
            List<RequiredSituation> required, List<String> alreadyAssigned, List<List<String>> rules) {
        return "Prepare the situation definitions and supporting details for stage " + stageId + " from the extracted required story situations and retrieved rule evidence. "
                + "Include required situations needed to preserve the source story and multiple intermediate candidates when supported by the story. "
                + "Do not force a fixed candidate count, add unstated prerequisite relations, or make optional ingredients mandatory. "
                + "For every situation include required, prerequisiteSituationIds, provenance (SOURCE_EXTRACTED or GM_SUPPLEMENTED), "
                + "and use zero-based integer index arrays for dynamic references: requiredRevelationIndexes and revelationIndexes index this response's "
                + "revelations[].revelationId values; pressureIndexes indexes the single pressure.pressureId; funnelIndexes indexes the single "
                + "funnel.funnelId; funnelPredicateIndexes indexes this response's funnel.requiredPredicateIds; and importantConsequenceIndexes "
                + "indexes importantConsequenceIds. Indexes point to positions in those same-response lists; pressureIndexes and funnelIndexes "
                + "can only use index 0 when nonempty. Use only in-range indexes and never invent separate target IDs. "
                + "The funnel's requiredRevelationIndexes also indexes this response's revelations[].revelationId values. "
                + "Set openingSituationIndex to null when this stage has no opening situation; otherwise set it to the zero-based index "
                + "of the single opening situation in situations. "
                + "For a non-required GM_SUPPLEMENTED situation, keep prerequisiteSituationIds limited to extracted required situation IDs "
                + "or situation IDs from this same response; do not refer to optional situations from another stage. "
                + "sourceRefIndexes (integer indexes into NUMBERED_ALLOWED_SOURCE_REFS for extracted claims; empty for GM supplements), "
                + "and mapDefinitionId "
                + "(one supplied map UUID or null). Return a complete DetailedStage JSON object except package/revision/stage identifiers. "
                + "Use only these map IDs: " + json(scenarioPackage.mapDefinitions().stream().map(m -> m.id()).toList())
                + "\nUse these exact situationId values for every extracted required situation and preserve each prerequisiteSituationIds list: "
                + json(required) + "\nSCENARIO_CONTEXT=" + promptStoryContext(scenarioPackage)
                + "\nCORE_RULEBOOK_EVIDENCE_BY_SITUATION=" + json(rulesBySituation(required, rules))
                + "\nALREADY_ASSIGNED_REQUIRED_SITUATION_IDS=" + json(alreadyAssigned)
                + "\nDo not emit any already assigned required situation ID in this stage; emit each remaining required situation at most once."
                + "\nNUMBERED_ALLOWED_SOURCE_REFS=" + numberedSourceRefs(evidence);
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
                "situationId", situations.get(index).situationId(), "ruleExcerpts", evidence.get(index)));
        return List.copyOf(result);
    }

    private static void resolveSituationTargetIndexes(JsonNode detailed) {
        List<String> revelationIds = textArray(detailed.path("revelations"), "revelationId");
        JsonNode pressure = detailed.path("pressure");
        JsonNode funnel = detailed.path("funnel");
        List<String> pressureIds = List.of(requiredText(pressure.path("pressureId")));
        List<String> funnelIds = List.of(requiredText(funnel.path("funnelId")));
        List<String> predicateIds = textArray(funnel.path("requiredPredicateIds"));
        List<String> consequenceIds = textArray(detailed.path("importantConsequenceIds"));
        JsonNode situations = detailed.path("situations");
        if (!situations.isArray()) throw new IllegalStateException("detailed situation target indexes are invalid");
        var funnelObject = (com.fasterxml.jackson.databind.node.ObjectNode) funnel;
        funnelObject.set("requiredRevelationIds", indexValues(funnel.path("requiredRevelationIndexes"), revelationIds,
                "funnel.requiredRevelationIndexes"));
        funnelObject.remove("requiredRevelationIndexes");
        for (JsonNode situation : situations) {
            if (!situation.isObject()) throw new IllegalStateException("detailed situation target indexes are invalid");
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) situation;
            object.set("revelationIds", indexValues(situation.path("revelationIndexes"), revelationIds,
                    "situation.revelationIndexes"));
            object.set("pressureIds", indexValues(situation.path("pressureIndexes"), pressureIds,
                    "situation.pressureIndexes"));
            object.set("funnelIds", indexValues(situation.path("funnelIndexes"), funnelIds,
                    "situation.funnelIndexes"));
            object.set("funnelPredicateIds", indexValues(situation.path("funnelPredicateIndexes"), predicateIds,
                    "situation.funnelPredicateIndexes"));
            object.set("importantConsequenceIds", indexValues(situation.path("importantConsequenceIndexes"), consequenceIds,
                    "situation.importantConsequenceIndexes"));
            object.remove(List.of("revelationIndexes", "pressureIndexes", "funnelIndexes", "funnelPredicateIndexes",
                    "importantConsequenceIndexes"));
        }
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode indexValues(JsonNode indexes, List<String> canonicalIds,
            String field) {
        if (!indexes.isArray()) throw targetIndexFailure(field, canonicalIds.size());
        var resolved = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (JsonNode index : indexes) {
            if (!index.isIntegralNumber() || !index.canConvertToInt() || index.intValue() < 0
                    || index.intValue() >= canonicalIds.size()) {
                throw targetIndexFailure(field, canonicalIds.size());
            }
            resolved.add(canonicalIds.get(index.intValue()));
        }
        return resolved;
    }

    private static IllegalStateException targetIndexFailure(String field, int allowedTargetCount) {
        return new IllegalStateException("detailed situation target index is invalid: field=" + field
                + ", allowedTargetCount=" + allowedTargetCount);
    }

    private static void resolveOpeningSituationIndex(JsonNode detailed, boolean openingSituationRequired) {
        JsonNode index = detailed.path("openingSituationIndex");
        JsonNode situations = detailed.path("situations");
        if (!detailed.isObject() || !situations.isArray()) {
            throw new IllegalStateException("detailed opening situation index is invalid");
        }
        if (index.isNull()) {
            if (openingSituationRequired) throw new IllegalStateException("opening situation is required for the first stage");
        } else if (!index.isIntegralNumber() || !index.canConvertToInt() || index.intValue() < 0
                || index.intValue() >= situations.size()) {
            throw new IllegalStateException("detailed opening situation index is invalid");
        }
        for (int i = 0; i < situations.size(); i++) {
            JsonNode situation = situations.get(i);
            if (!situation.isObject()) throw new IllegalStateException("detailed opening situation index is invalid");
            ((com.fasterxml.jackson.databind.node.ObjectNode) situation).put("opening", !index.isNull() && i == index.intValue());
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) detailed).remove("openingSituationIndex");
    }

    private static void resolveSupplementedSituationIds(JsonNode detailed, String stageId,
            List<RequiredSituation> requiredSituations) {
        JsonNode situations = detailed.path("situations");
        if (!situations.isArray()) throw new IllegalStateException("detailed situation identities are invalid");
        java.util.Set<String> requiredIds = requiredSituations.stream().map(RequiredSituation::situationId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        java.util.Set<String> protectedIds = new java.util.HashSet<>(requiredIds);
        java.util.Map<String, String> supplementedIds = new java.util.LinkedHashMap<>();
        for (JsonNode situation : situations) {
            if (!situation.isObject() || !situation.path("provenance").isTextual()
                    || !situation.path("required").isBoolean()) {
                throw new IllegalStateException("detailed situation identities are invalid");
            }
            String situationId = requiredText(situation.path("situationId"));
            boolean optionalSupplement = "GM_SUPPLEMENTED".equals(situation.path("provenance").textValue())
                    && !situation.path("required").booleanValue();
            if (!optionalSupplement) protectedIds.add(situationId);
        }
        for (JsonNode situation : situations) {
            if ("GM_SUPPLEMENTED".equals(situation.path("provenance").textValue())
                    && !situation.path("required").booleanValue()) {
                String situationId = requiredText(situation.path("situationId"));
                String canonicalId = stageScopedSupplementedId(stageId, situationId);
                if (protectedIds.contains(canonicalId)) {
                    throw new IllegalStateException("stage-scoped supplemented situation id conflicts with a protected situation id");
                }
                supplementedIds.put(situationId, canonicalId);
            }
        }
        for (JsonNode situation : situations) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) situation;
            String originalId = requiredText(situation.path("situationId"));
            boolean optionalSupplement = "GM_SUPPLEMENTED".equals(situation.path("provenance").textValue())
                    && !situation.path("required").booleanValue();
            if (optionalSupplement) object.put("situationId", supplementedIds.get(originalId));
            if (requiredIds.contains(originalId) && !optionalSupplement) continue;
            JsonNode prerequisites = situation.path("prerequisiteSituationIds");
            if (!prerequisites.isArray()) throw new IllegalStateException("detailed situation prerequisites are invalid");
            var resolved = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            for (JsonNode prerequisite : prerequisites) {
                String prerequisiteId = requiredText(prerequisite);
                if (supplementedIds.containsKey(prerequisiteId) && protectedIds.contains(prerequisiteId)) {
                    throw new IllegalStateException("situation prerequisite ambiguously references a protected situation id");
                }
                resolved.add(supplementedIds.getOrDefault(prerequisiteId, prerequisiteId));
            }
            object.set("prerequisiteSituationIds", resolved);
        }
    }

    private static String stageScopedSupplementedId(String stageId, String situationId) {
        return "gm-supplemented:" + stageId.length() + ":" + stageId + ":" + situationId;
    }

    private static List<String> textArray(JsonNode values) {
        if (!values.isArray()) throw new IllegalStateException("detailed situation canonical targets are invalid");
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) result.add(requiredText(value));
        return List.copyOf(result);
    }

    private static List<String> textArray(JsonNode values, String field) {
        if (!values.isArray()) throw new IllegalStateException("detailed situation canonical targets are invalid");
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) result.add(requiredText(value.path(field)));
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
        JsonNode extracted = generate(request.sessionId(), request.adventureId().value(), request.scenarioPackageId(),
                "required-extract", "From only these RAG-retrieved story passages, extract story-essential situations and explicit prerequisites. "
                        + "Treat these as retrieved candidates, not the complete story; do not claim that no other required situation exists. "
                        + "Return each situation once with a unique stable ID, a concise summary, prerequisiteSituationIds, "
                        + "and sourceRefIndexes citing the supplied passages. Return {requiredSituations:[...]} and no source objects.\n"
                        + "RETRIEVED_STORY_PASSAGES=" + json(passages)
                        + "\nNUMBERED_ALLOWED_SOURCE_REFS=" + numberedSourceRefs(allowedRefs), allowedRefs);
        List<RequiredSituation> required = requiredSituations(extracted, allowedRefs);
        validateRequiredSituations(required, passages);
        return required;
    }

    private List<RequiredSituation> requiredSituations(JsonNode response, List<ScenarioSourceReference> allowedRefs) {
        if (!response.isObject() || !response.path("requiredSituations").isArray()) {
            throw new IllegalStateException("GM omitted required situation list");
        }
        JsonNode resolved = response.path("requiredSituations").deepCopy();
        resolveSourceRefIndexes(resolved, allowedRefs);
        return mapper.convertValue(resolved,
                mapper.getTypeFactory().constructCollectionType(List.class, RequiredSituation.class));
    }

    private static void validateRequiredSituations(List<RequiredSituation> required,
            List<StorySourcePreviewPort.Passage> passages) {
        if (required.isEmpty()) throw new IllegalStateException("story RAG returned no required situations");
        Set<ScenarioSourceReference> allowed = passages.stream().map(StorySourcePreviewPort.Passage::sourceRef)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> ids = required.stream().map(RequiredSituation::situationId)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.size() != required.size()) throw new IllegalStateException("required situation IDs must be unique");
        if (required.stream().anyMatch(item -> item.sourceRefs().isEmpty()
                || item.sourceRefs().stream().anyMatch(ref -> !allowed.contains(ref)))) {
            throw new IllegalStateException("required situation source reference is outside the RAG results");
        }
        if (required.stream().flatMap(item -> item.prerequisiteSituationIds().stream()).anyMatch(id -> !ids.contains(id))) {
            throw new IllegalStateException("required situation has an unknown prerequisite ID");
        }
        if (!cyclicSituationIds(required).isEmpty()) {
            throw new IllegalStateException("required situation prerequisites contain a cycle");
        }
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

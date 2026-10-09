package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
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
        ScenarioPackage scenarioPackage = loadPackage(scenarioPackageId);
        return Stream.concat(elements(scenarioPackage).flatMap(element -> element.sourceRefs().stream()),
                sourcePassages(scenarioPackage).stream().map(StorySourcePreviewPort.Passage::sourceRef)).distinct().toList();
    }

    @Override
    public boolean isScenarioBacked(UUID scenarioPackageId) {
        ScenarioPackage scenarioPackage = loadPackage(scenarioPackageId);
        return scenarioPackage.documents().stream().anyMatch(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
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
            List<RequiredSituation> required = reviewRequiredSituations(request, scenarioPackage);
            JsonNode generated = generate(request.sessionId(), request.adventureId().value(),
                    request.scenarioPackageId(), "backbone", backbonePrompt(scenarioPackage, request.evidence(), required),
                    request.evidence());
            List<StageBackboneEntry> stages = mapper.convertValue(generated.path("stages"),
                    mapper.getTypeFactory().constructCollectionType(List.class, StageBackboneEntry.class));
            List<ScenarioSourceReference> refs = references(generated.path("sourceRefs"));
            return new StageBackbone(request.scenarioPackageId(), 1, stages, refs, required);
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
            JsonNode generated = generate(request.sessionId(), request.adventureId().value(),
                    request.scenarioPackageId(), "stage:" + request.stageId(),
                    detailedPrompt(scenarioPackage, request.stageId(), request.evidence(), request.requiredSituations()),
                    request.evidence());
            var content = generated.deepCopy();
            resolveFunnelPredicateIndexes(content);
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
        var session = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("adventure session not found"));
        var binding = bindings.current(sessionId.value()).orElseThrow(() -> new IllegalStateException("GM provider selection not found"));
        String prompt = "ROLE=INITIAL_SITUATION_PREPARATION\nTASK=" + task + "\n" + taskPrompt
                + "\nOUTPUT_CONTRACT=Return exactly one JSON object matching the requested fields. No markdown.";
        return gm.generate(new InitialSituationGmPort.Request(session.ownerPlayerId().value(), sessionId.value(),
                binding.selection().endpointId(), binding.selection().provider(), binding.selection().model(),
                binding.selection().reasoning(), "initial-situation:" + adventureId + ":" + packageId + ":" + task,
                prompt, outputSchema(task, allowedRefs)));
    }

    private JsonNode outputSchema(String task, List<ScenarioSourceReference> allowedRefs) {
        Map<String, Object> properties;
        if (task.startsWith("required-extract:")) {
            properties = Map.of("requiredSituations", arraySchema(requiredSituationSchema(allowedRefs)));
        } else if (task.equals("required-review:full")) {
            properties = Map.of("reviewed", Map.of("type", "boolean", "enum", List.of(true)),
                    "requiredSituations", arraySchema(requiredSituationSchema(allowedRefs), 1));
        } else if (task.equals("required-review:repair")) {
            properties = Map.of("reviewed", Map.of("type", "boolean", "enum", List.of(true)),
                    "correctedSituations", arraySchema(objectSchema(Map.of(
                            "sourceRefIndexes", arraySchema(Map.of("type", "integer", "minimum", 0), 1),
                            "prerequisiteSituationIds", arraySchema(stringSchema()),
                            "situationId", nullableStringSchema())), 1));
        } else if (task.equals("backbone")) {
            properties = Map.of("stages", arraySchema(objectSchema(Map.of(
                            "stageId", stringSchema(), "order", Map.of("type", "integer", "minimum", 1),
                            "role", stringSchema(), "coreProblem", stringSchema(), "funnelSummary", stringSchema(),
                            "sourceRefs", sourceReferencesSchema(allowedRefs))), 1),
                    "sourceRefs", sourceReferencesSchema(allowedRefs));
        } else if (task.startsWith("stage:")) {
            Map<String, Object> situationProperties = Map.ofEntries(
                    Map.entry("situationId", stringSchema()),
                    Map.entry("intents", arraySchema(enumSchema("REVELATION", "PRESSURE", "FUNNEL_OPPORTUNITY", "IMPORTANT_CONSEQUENCE"), 1)),
                    Map.entry("revelationIds", arraySchema(stringSchema())), Map.entry("pressureIds", arraySchema(stringSchema())),
                    Map.entry("funnelIds", arraySchema(stringSchema())),
                    Map.entry("funnelPredicateIndexes", arraySchema(Map.of("type", "integer", "minimum", 0))),
                    Map.entry("importantConsequenceIds", arraySchema(stringSchema())), Map.entry("opening", booleanSchema()),
                    Map.entry("required", booleanSchema()), Map.entry("prerequisiteSituationIds", arraySchema(stringSchema())),
                    Map.entry("provenance", enumSchema("SOURCE_EXTRACTED", "GM_SUPPLEMENTED")),
                    Map.entry("mapDefinitionId", nullableStringSchema()), Map.entry("sourceRefs", sourceReferencesSchema(allowedRefs)));
            Map<String, Object> situation = situationSchema(situationProperties);
            properties = Map.of("coreProblem", stringSchema(),
                    "revelations", arraySchema(objectSchema(Map.of("revelationId", stringSchema(),
                            "required", booleanSchema(), "sourceRefs", sourceReferencesSchema(allowedRefs)))),
                    "threat", objectSchema(Map.of("core", stringSchema(), "sourceRefs", sourceReferencesSchema(allowedRefs))),
                    "pressure", objectSchema(Map.of("pressureId", stringSchema(), "progressionMaterial", stringSchema(),
                            "sourceRefs", sourceReferencesSchema(allowedRefs))),
                    "funnel", objectSchema(Map.of("funnelId", stringSchema(), "meaning", stringSchema(),
                            "requiredRevelationIds", arraySchema(stringSchema()), "requiredPredicateIds", arraySchema(stringSchema()),
                            "sourceRefs", sourceReferencesSchema(allowedRefs))),
                    "situations", arraySchema(situation), "importantConsequenceIds", arraySchema(stringSchema()),
                    "sourceRefs", sourceReferencesSchema(allowedRefs));
        } else {
            throw new IllegalArgumentException("unsupported initial situation task schema");
        }
        return mapper.valueToTree(objectSchema(properties));
    }

    private static Map<String, Object> requiredSituationSchema(List<ScenarioSourceReference> allowedRefs) {
        return objectSchema(Map.of("situationId", stringSchema(), "summary", stringSchema(),
                "prerequisiteSituationIds", arraySchema(stringSchema()),
                "sourceRefs", allowedRefs.isEmpty() ? sourceReferencesSchema(allowedRefs)
                        : arraySchema(sourceReferenceSchema(allowedRefs), 1)));
    }

    private static Map<String, Object> sourceReferencesSchema(List<ScenarioSourceReference> allowedRefs) {
        if (allowedRefs.isEmpty()) {
            return Map.of("type", "array", "items", sourceReferenceSchema(List.of()), "maxItems", 0);
        }
        return arraySchema(sourceReferenceSchema(allowedRefs));
    }

    private static Map<String, Object> sourceReferenceSchema(List<ScenarioSourceReference> allowedRefs) {
        List<String> documentIds = allowedRefs.stream().map(ref -> ref.knowledgeDocumentId().value().toString())
                .distinct().toList();
        List<Long> extractionVersions = allowedRefs.stream().map(ScenarioSourceReference::extractionVersion)
                .distinct().toList();
        Map<String, Object> documentId = objectSchema(Map.of("value", documentIds.isEmpty()
                ? stringSchema() : Map.of("type", "string", "enum", documentIds)));
        Map<String, Object> extractionVersion = extractionVersions.isEmpty()
                ? Map.of("type", "integer", "minimum", 1)
                : Map.of("type", "integer", "enum", extractionVersions);
        return objectSchema(Map.of("knowledgeDocumentId", documentId,
                "extractionVersion", extractionVersion, "locator", stringSchema()));
    }

    private static Map<String, Object> situationSchema(Map<String, Object> properties) {
        List<Map<String, Object>> targetAlternatives = List.of("revelationIds", "pressureIds", "funnelIds",
                "funnelPredicateIndexes", "importantConsequenceIds").stream().map(target -> {
                    Map<String, Object> branchProperties = new LinkedHashMap<>(properties);
                    Map<String, Object> targetSchema = new LinkedHashMap<>();
                    ((Map<?, ?>) properties.get(target)).forEach((key, value) -> targetSchema.put((String) key, value));
                    targetSchema.put("minItems", 1);
                    branchProperties.put(target, targetSchema);
                    return objectSchema(branchProperties);
                }).toList();
        Map<String, Object> schema = new LinkedHashMap<>(objectSchema(properties));
        schema.put("anyOf", targetAlternatives);
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

    private static Map<String, Object> stringSchema() { return Map.of("type", "string", "minLength", 1); }
    private static Map<String, Object> nullableStringSchema() { return Map.of("type", List.of("string", "null"), "minLength", 1); }
    private static Map<String, Object> booleanSchema() { return Map.of("type", "boolean"); }
    private static Map<String, Object> enumSchema(String... values) { return Map.of("type", "string", "enum", List.of(values)); }

    private String backbonePrompt(ScenarioPackage scenarioPackage, List<ScenarioSourceReference> evidence,
            List<RequiredSituation> required) {
        return "Create the adventure story backbone from this compiled story model and source evidence. "
                + "Include every story-essential required situation and only prerequisites explicit in the source. "
                + "Add as many intermediate candidate stages as the story needs; do not use a fixed count or impose unstated order. "
                + "Each stage needs stageId, contiguous order, role, coreProblem, funnelSummary, and sourceRefs. "
                + "Return stages and sourceRefs.\nSCENARIO_MODEL=" + json(scenarioPackage.scenarioModel())
                + "\nREVIEWED_REQUIRED_SITUATIONS=" + json(required)
                + "\nSOURCE_QUOTES=" + sourceQuotes(scenarioPackage) + "\nALLOWED_SOURCE_REFS=" + json(evidence);
    }

    private String detailedPrompt(ScenarioPackage scenarioPackage, String stageId, List<ScenarioSourceReference> evidence,
            List<RequiredSituation> required) {
        return "Prepare the situation definitions and supporting details for stage " + stageId + " from this compiled story model. "
                + "Include required situations needed to preserve the source story and multiple intermediate candidates when supported by the story. "
                + "Do not force a fixed candidate count, add unstated prerequisite relations, or make optional ingredients mandatory. "
                + "For every situation include required, prerequisiteSituationIds, provenance (SOURCE_EXTRACTED or GM_SUPPLEMENTED), "
                + "and funnelPredicateIndexes as integer positions in this same response's funnel.requiredPredicateIds array; "
                + "never invent separate predicate IDs for situations. "
                + "sourceRefs (exact allowed references for extracted claims; empty for GM supplements), and mapDefinitionId "
                + "(one supplied map UUID or null). Return a complete DetailedStage JSON object except package/revision/stage identifiers. "
                + "Use only these map IDs: " + json(scenarioPackage.mapDefinitions().stream().map(m -> m.id()).toList())
                + "\nUse these exact situationId values for every reviewed required situation and preserve each prerequisiteSituationIds list: "
                + json(required) + "\nSCENARIO_MODEL=" + json(scenarioPackage.scenarioModel()) + "\nSOURCE_QUOTES=" + sourceQuotes(scenarioPackage)
                + "\nALLOWED_SOURCE_REFS=" + json(evidence);
    }

    private static void resolveFunnelPredicateIndexes(JsonNode detailed) {
        JsonNode predicateIds = detailed.path("funnel").path("requiredPredicateIds");
        JsonNode situations = detailed.path("situations");
        if (!predicateIds.isArray() || !situations.isArray()) {
            throw new IllegalStateException("detailed situation predicate indexes are invalid");
        }
        for (JsonNode situation : situations) {
            JsonNode indexes = situation.path("funnelPredicateIndexes");
            if (!situation.isObject() || !indexes.isArray()) {
                throw new IllegalStateException("detailed situation predicate indexes are invalid");
            }
            var resolved = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            for (JsonNode index : indexes) {
                if (!index.isIntegralNumber() || !index.canConvertToInt() || index.intValue() < 0
                        || index.intValue() >= predicateIds.size()) {
                    throw new IllegalStateException("detailed situation references an unknown funnel predicate index");
                }
                JsonNode predicateId = predicateIds.get(index.intValue());
                if (!predicateId.isTextual() || predicateId.textValue().isBlank()) {
                    throw new IllegalStateException("detailed situation predicate target is invalid");
                }
                resolved.add(predicateId.textValue());
            }
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) situation;
            object.set("funnelPredicateIds", resolved);
            object.remove("funnelPredicateIndexes");
        }
    }

    private List<StorySourcePreviewPort.Passage> sourcePassages(ScenarioPackage scenarioPackage) {
        if (storySource == null) return List.of();
        return scenarioPackage.documents().stream()
                .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                .flatMap(document -> storySource.load(document).stream()).toList();
    }

    private List<RequiredSituation> reviewRequiredSituations(StageBackboneGenerationPort.Request request,
            ScenarioPackage scenarioPackage) {
        List<StorySourcePreviewPort.Passage> passages = sourcePassages(scenarioPackage);
        if (passages.isEmpty()) {
            if (scenarioPackage.documents().stream().anyMatch(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))) {
                throw new IllegalStateException("selected original story passages are unavailable");
            }
            return List.of();
        }
        List<List<StorySourcePreviewPort.Passage>> chunks = new ArrayList<>();
        List<StorySourcePreviewPort.Passage> chunk = new ArrayList<>();
        int characters = 0;
        for (StorySourcePreviewPort.Passage passage : passages) {
            if (!chunk.isEmpty() && characters + passage.text().length() > 12000) {
                chunks.add(List.copyOf(chunk));
                chunk.clear();
                characters = 0;
            }
            chunk.add(passage);
            characters += passage.text().length();
        }
        if (!chunk.isEmpty()) chunks.add(List.copyOf(chunk));

        Map<String, RequiredSituation> proposed = new LinkedHashMap<>();
        for (int index = 0; index < chunks.size(); index++) {
            JsonNode extracted = generate(request.sessionId(), request.adventureId().value(), request.scenarioPackageId(),
                    "required-extract:" + index, "Extract every story-essential required situation and every source-explicit "
                            + "prerequisite from these ORIGINAL STORY PASSAGES. Use stable situationId values, a short summary, "
                            + "prerequisiteSituationIds, and sourceRefs citing passage locators. Do not infer an unstated order. "
                            + "Return {requiredSituations:[...]}.\nORIGINAL_STORY_PASSAGES=" + json(chunks.get(index)),
                    chunks.get(index).stream().map(StorySourcePreviewPort.Passage::sourceRef).distinct().toList());
            for (RequiredSituation item : requiredSituations(extracted)) {
                RequiredSituation earlier = proposed.putIfAbsent(item.situationId(), item);
                if (earlier != null && !earlier.equals(item)) {
                    throw new IllegalStateException("required situation extraction returned conflicting ids");
                }
            }
        }
        JsonNode reviewed = generate(request.sessionId(), request.adventureId().value(), request.scenarioPackageId(),
                "required-review:full", "Independently compare the COMPLETE PROPOSED REQUIRED LIST against ALL "
                        + "ORIGINAL STORY PASSAGES, including relationships across distant passages. Add any omitted "
                        + "story-essential situation or explicit prerequisite, remove unsupported claims and relations, "
                        + "and keep stable ids for retained situations. Return {reviewed:true,requiredSituations:[the "
                        + "complete corrected list]}. Every sourceRefs value must cite a real original passage locator."
                        + "\nORIGINAL_STORY_PASSAGES=" + json(passages)
                        + "\nPROPOSED_REQUIRED_SITUATIONS=" + json(proposed.values()),
                passages.stream().map(StorySourcePreviewPort.Passage::sourceRef).distinct().toList());
        if (!reviewCompleted(reviewed)) throw new IllegalStateException("original story review did not complete");
        List<RequiredSituation> required = requiredSituations(reviewed);
        List<Map<String, Object>> invalid = invalidRequiredSituations(required, passages);
        if (!invalid.isEmpty()) {
            List<ScenarioSourceReference> allowedRefs = passages.stream().map(StorySourcePreviewPort.Passage::sourceRef)
                    .distinct().toList();
            JsonNode corrected = generate(request.sessionId(), request.adventureId().value(), request.scenarioPackageId(),
                    "required-review:repair", "Correct only the invalid entries in the reviewed required-situation list. "
                            + "Keep all valid entries and their meaning unchanged; do not remove a required situation or "
                            + "invent source evidence or prerequisites. Return {reviewed:true,correctedSituations:[...]} "
                            + "with one ordered item per INVALID_ENTRIES item. Each item contains sourceRefIndexes, "
                            + "prerequisiteSituationIds, and optionally a matching situationId. "
                            + "Use integer indexes into NUMBERED_ALLOWED_SOURCE_REFS. "
                            + "Do not return summaries or change situation IDs.\nINVALID_ENTRIES=" + json(invalid)
                            + "\nNUMBERED_ALLOWED_SOURCE_REFS=" + json(java.util.stream.IntStream.range(0, allowedRefs.size())
                                    .mapToObj(index -> Map.of("index", index, "sourceRef", allowedRefs.get(index))).toList())
                            + "\nKNOWN_SITUATION_IDS=" + json(required.stream().map(RequiredSituation::situationId).distinct().toList()),
                    allowedRefs);
            if (!reviewCompleted(corrected)) {
                throw new IllegalStateException("required situation correction did not complete");
            }
            List<RequiredSituation> correctedRequired = mergeCorrectedSituations(required,
                    corrected.path("correctedSituations"), invalid, allowedRefs);
            required = correctedRequired;
            validateRequiredSituations(required, passages);
        }
        return required;
    }

    private List<RequiredSituation> requiredSituations(JsonNode response) {
        if (!response.isObject() || !response.path("requiredSituations").isArray()) {
            throw new IllegalStateException("GM omitted required situation list");
        }
        return mapper.convertValue(response.path("requiredSituations"),
                mapper.getTypeFactory().constructCollectionType(List.class, RequiredSituation.class));
    }

    private static boolean reviewCompleted(JsonNode response) {
        return response.isObject() && response.path("reviewed").isBoolean()
                && response.path("reviewed").booleanValue();
    }

    private static void validateRequiredSituations(List<RequiredSituation> required,
            List<StorySourcePreviewPort.Passage> passages) {
        if (!invalidRequiredSituations(required, passages).isEmpty()) {
            throw new IllegalStateException("required situation has an invalid original source or prerequisite");
        }
    }

    private List<RequiredSituation> mergeCorrectedSituations(List<RequiredSituation> reviewed,
            JsonNode corrected, List<Map<String, Object>> invalid, List<ScenarioSourceReference> allowedRefs) {
        if (!corrected.isArray() || corrected.size() != invalid.size()) {
            throw new IllegalStateException("required situation correction changed the reviewed list");
        }
        Map<String, RequiredSituation> corrections = new java.util.HashMap<>();
        Set<String> allowedFields = Set.of("sourceRefIndexes", "prerequisiteSituationIds", "situationId");
        for (int index = 0; index < invalid.size(); index++) {
            JsonNode item = corrected.get(index);
            if (!item.isObject()) throw new IllegalStateException("required situation correction changed the reviewed list");
            RequiredSituation original = (RequiredSituation) invalid.get(index).get("requiredSituation");
            JsonNode situationId = item.path("situationId");
            JsonNode prerequisiteIds = item.path("prerequisiteSituationIds");
            var fields = item.fieldNames();
            while (fields.hasNext()) {
                if (!allowedFields.contains(fields.next())) {
                    throw new IllegalStateException("required situation correction changed the reviewed list");
                }
            }
            if ((item.has("situationId") && !situationId.isNull() && (!situationId.isTextual()
                    || !original.situationId().equals(situationId.textValue())))
                    || !item.path("sourceRefIndexes").isArray()
                    || !prerequisiteIds.isArray()
                    || java.util.stream.StreamSupport.stream(prerequisiteIds.spliterator(), false)
                            .anyMatch(id -> !id.isTextual() || id.textValue().isBlank())) {
                throw new IllegalStateException("required situation correction changed the reviewed list");
            }
            List<ScenarioSourceReference> sourceRefs = new ArrayList<>();
            for (JsonNode sourceRefIndex : item.path("sourceRefIndexes")) {
                if (!sourceRefIndex.isIntegralNumber() || !sourceRefIndex.canConvertToInt()
                        || sourceRefIndex.intValue() < 0 || sourceRefIndex.intValue() >= allowedRefs.size()) {
                    throw new IllegalStateException("required situation correction referenced an unknown source index");
                }
                sourceRefs.add(allowedRefs.get(sourceRefIndex.intValue()));
            }
            List<String> prerequisites = new ArrayList<>();
            prerequisiteIds.forEach(id -> prerequisites.add(id.textValue()));
            RequiredSituation correction = new RequiredSituation(original.situationId(), original.summary(),
                    prerequisites, sourceRefs);
            corrections.put(original.situationId(), correction);
        }
        return reviewed.stream().map(item -> corrections.getOrDefault(item.situationId(), item)).toList();
    }

    private static List<Map<String, Object>> invalidRequiredSituations(List<RequiredSituation> required,
            List<StorySourcePreviewPort.Passage> passages) {
        if (required.isEmpty()) throw new IllegalStateException("original story review found no required situations");
        Set<ScenarioSourceReference> allowed = passages.stream().map(StorySourcePreviewPort.Passage::sourceRef)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> ids = required.stream().map(RequiredSituation::situationId)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.size() != required.size()) throw new IllegalStateException("required situation ids must be unique");
        Set<String> cyclicIds = cyclicSituationIds(required);
        List<Map<String, Object>> invalid = new ArrayList<>();
        for (RequiredSituation item : required) {
            List<ScenarioSourceReference> invalidRefs = item.sourceRefs().stream().filter(ref -> !allowed.contains(ref)).toList();
            List<String> invalidPrerequisites = item.prerequisiteSituationIds().stream().filter(id -> !ids.contains(id)).toList();
            boolean cyclicPrerequisiteSet = cyclicIds.contains(item.situationId());
            if (!invalidRefs.isEmpty() || !invalidPrerequisites.isEmpty() || cyclicPrerequisiteSet) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("situationId", item.situationId());
                detail.put("requiredSituation", item);
                detail.put("invalidOriginalSourceRefs", invalidRefs);
                detail.put("unknownPrerequisiteSituationIds", invalidPrerequisites);
                detail.put("cyclicPrerequisiteSet", cyclicPrerequisiteSet);
                invalid.add(detail);
            }
        }
        return invalid;
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

    private static List<String> sourceQuotes(ScenarioPackage scenarioPackage) {
        return scenarioPackage.units().stream().filter(unit -> !unit.sourceQuote().isBlank())
                .map(unit -> unit.sourceQuote() + " [" + unit.sourceRefs() + "]").distinct().toList();
    }

    private ScenarioPackage loadPackage(UUID scenarioPackageId) {
        return packages.findById(scenarioPackageId).orElseThrow(() -> new IllegalStateException("scenario package not found"));
    }

    private static String openingProblem(ScenarioPackage scenarioPackage) {
        return text(scenarioPackage.scenarioModel().objectives(), scenarioPackage.scenarioModel().startingSituation());
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

package com.dndmaster.adventure.application.ability;

import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.runtime.GmProviderBindingRepository;
import com.dndmaster.adventure.application.runtime.RuntimeCharacterSheetReadPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchRequest;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.preparation.InitialSituationGmPort;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AdventureAbilityDefinitionPreparationService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AdventureAbilityDefinitionPreparationService.class);
    private final AdventureAbilityDefinitionRepository definitions;
    private final ScenarioPackageRepository packages;
    private final SessionKnowledgeSetRepository knowledgeSets;
    private final AdventureSessionRepository sessions;
    private final RuntimeCharacterSheetReadPort characterSheets;
    private final RuntimeEvidenceSearchPort evidenceSearch;
    private final GmProviderBindingRepository providerBindings;
    private final InitialSituationGmPort gm;
    private final ObjectMapper mapper;

    public AdventureAbilityDefinitionPreparationService(AdventureAbilityDefinitionRepository definitions,
            ScenarioPackageRepository packages, SessionKnowledgeSetRepository knowledgeSets,
            AdventureSessionRepository sessions, RuntimeCharacterSheetReadPort characterSheets,
            RuntimeEvidenceSearchPort evidenceSearch, GmProviderBindingRepository providerBindings,
            InitialSituationGmPort gm, ObjectMapper mapper) {
        this.definitions = Objects.requireNonNull(definitions);
        this.packages = Objects.requireNonNull(packages);
        this.knowledgeSets = Objects.requireNonNull(knowledgeSets);
        this.sessions = Objects.requireNonNull(sessions);
        this.characterSheets = Objects.requireNonNull(characterSheets);
        this.evidenceSearch = Objects.requireNonNull(evidenceSearch);
        this.providerBindings = Objects.requireNonNull(providerBindings);
        this.gm = Objects.requireNonNull(gm);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public List<String> prepareAtAdventureStart(Adventure adventure) {
        List<String> ids = new ArrayList<>();
        for (var member : adventure.party()) {
            JsonNode sheet = readSheet(member.characterSheetId().value());
            JsonNode learned = parseState(sheet).path("learnedAbilityIds");
            if (!learned.isArray()) throw new IllegalStateException("character sheet learnedAbilityIds must be an array");
            learned.forEach(id -> {
                if (!id.isTextual() || id.asText().isBlank()) throw new IllegalStateException("character sheet has an invalid learned ability ID");
                ids.add(id.asText());
            });
        }
        List<String> actualIds = ids.stream().distinct().toList();
        prepare(adventure.id(), adventure.sessionId(), adventure.ownerPlayerId(), actualIds);
        activate(adventure.id().value(), actualIds);
        return actualIds;
    }

    public void prepare(AdventureId adventureId, SessionId sessionId, OwnerPlayerId owner, List<String> abilityIds) {
        prepare(adventureId, sessionId, owner, abilityIds, null);
    }

    public void prepare(AdventureId adventureId, SessionId sessionId, OwnerPlayerId owner, List<String> abilityIds,
            JsonNode sourceDefinition) {
        for (String abilityId : abilityIds.stream().distinct().toList()) {
            if (definitions.find(adventureId.value(), abilityId).isPresent()) continue;
            long started = System.nanoTime();
            List<RuntimeEvidence> evidence = List.of();
            try {
                evidence = search(adventureId, sessionId, owner, abilityId);
                JsonNode proposed;
                if (sourceDefinition != null && !sourceDefinition.isNull()) {
                    proposed = sourceDefinition;
                    validate(abilityId, proposed, evidence);
                } else {
                    proposed = generateValidated(adventureId, sessionId, owner, abilityId, evidence);
                }
                definitions.prepare(adventureId.value(), abilityId, proposed);
                diagnostic("complete", adventureId, sessionId, abilityId, evidence.size(), "valid_definition_saved",
                        (System.nanoTime() - started) / 1_000_000, "none");
            } catch (RuntimeException failure) {
                diagnostic("source_input_required", adventureId, sessionId, abilityId, evidence.size(),
                        "validated_definition", (System.nanoTime() - started) / 1_000_000, failure.getClass().getSimpleName());
                throw new AbilityDefinitionSourceInputRequiredException(abilityId, failure);
            }
        }
    }

    JsonNode generateValidated(AdventureId adventureId, SessionId sessionId, OwnerPlayerId owner, String abilityId,
            List<RuntimeEvidence> evidence) {
        List<String> corrections = new ArrayList<>();
        while (true) {
            JsonNode proposed = generate(adventureId, sessionId, owner, abilityId, evidence, corrections);
            try {
                validate(abilityId, proposed, evidence);
                return proposed;
            } catch (IllegalArgumentException invalid) {
                String correction = invalid.getMessage() == null ? "ABILITY_DEFINITION_INVALID" : invalid.getMessage();
                if (corrections.contains(correction)) throw invalid;
                corrections.add(correction);
                diagnostic("structured_output_repair", adventureId, sessionId, abilityId, evidence.size(),
                        "new_validation_feedback", 0, "validation_failure:" + corrections.size());
            }
        }
    }

    private static void diagnostic(String outcome, AdventureId adventure, SessionId session, String abilityId,
            int evidenceCount, String expected, long elapsedMs, String failureKind) {
        if (Boolean.parseBoolean(System.getenv().getOrDefault("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "false")))
            LOGGER.info("dev_ability_preparation outcome={} adventureId={} sessionId={} abilityId={} evidenceCount={} expected={} failureKind={} elapsedMs={} recovery={}",
                    outcome, adventure.value(), session.value(), abilityId, evidenceCount, expected, failureKind, elapsedMs,
                    "source_definition_resubmission");
    }

    public void activate(UUID adventureId, List<String> abilityIds) {
        abilityIds.forEach(id -> definitions.activate(adventureId, id));
    }

    private List<RuntimeEvidence> search(AdventureId adventureId, SessionId sessionId, OwnerPlayerId owner, String abilityId) {
        var adventure = new com.dndmaster.adventure.domain.adventure.AdventureId(adventureId.value());
        var session = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("adventure session not found"));
        UUID packageId = packages.findById(session.scenarioPackageId()).orElseThrow().packageId();
        var knowledgeSet = knowledgeSets.findBySessionId(sessionId).orElseThrow(() -> new IllegalStateException("session knowledge set not found"));
        var selectedIds = knowledgeSet.knowledgeDocumentIds().stream().map(id -> id.value()).toList();
        var scenarioPackage = packages.findById(packageId).orElseThrow();
        List<ScenarioBundleDocumentSelection> selected = scenarioPackage.documents().stream()
                .filter(doc -> selectedIds.contains(doc.knowledgeDocumentId().value())).toList();
        List<RuntimeEvidence> result = new ArrayList<>();
        for (RuntimeEvidenceType type : List.of(RuntimeEvidenceType.RULEBOOK, RuntimeEvidenceType.STORYBOOK)) {
            List<ScenarioBundleDocumentSelection> documents = selected.stream().filter(doc -> type.name().equalsIgnoreCase(doc.documentType())).toList();
            if (documents.isEmpty()) continue;
            Map<UUID, Long> versions = documents.stream().collect(java.util.stream.Collectors.toMap(
                    doc -> doc.knowledgeDocumentId().value(), ScenarioBundleDocumentSelection::extractionVersion));
            var found = evidenceSearch.search(new RuntimeEvidenceSearchRequest(adventure, owner, sessionId, packageId,
                    documents.stream().map(doc -> doc.knowledgeDocumentId().value()).toList(), null,
                    "Prepare the ability " + abilityId + " from this source type", type, 8, versions,
                    "ability:" + abilityId, "RULE_LOOKUP"));
            result.addAll(found);
        }
        return List.copyOf(result);
    }

    private JsonNode generate(AdventureId adventureId, SessionId sessionId, OwnerPlayerId owner,
            String abilityId, List<RuntimeEvidence> evidence, List<String> corrections) {
        if (evidence.isEmpty()) throw new IllegalStateException("ability definition evidence was not found");
        var binding = providerBindings.current(sessionId.value()).orElseThrow(() -> new IllegalStateException("GM provider selection not found"));
        String prompt = "ROLE=ABILITY_DEFINITION_PREPARATION\nTASK=Structure only the supplied rule evidence for the supplied ability ID. Return every required field and use null where a field does not apply. Never infer unsupported rules. Each populated rule entry must cite one supplied source location.\nABILITY_ID="
                + abilityId + "\nVALIDATION_CORRECTIONS=" + mapper.valueToTree(corrections) + "\nEVIDENCE=" + mapper.valueToTree(evidence);
        String operationKey = "ability-definition:" + adventureId.value() + ":" + abilityId
                + (corrections.isEmpty() ? "" : ":repair:" + corrections.size());
        return gm.generate(new InitialSituationGmPort.Request(owner.value(), sessionId.value(),
                binding.selection().endpointId(), binding.selection().provider(), binding.selection().model(),
                binding.selection().reasoning(), operationKey,
                prompt, schema()));
    }

    private void validate(String abilityId, JsonNode definition, List<RuntimeEvidence> evidence) {
        if (!definition.isObject() || definition.size() != 16 || !abilityId.equals(definition.path("abilityId").asText()))
            throw new IllegalArgumentException("ability definition schema or identifier is invalid");
        for (String field : List.of("name", "actionTiming", "resourceCost", "visibility", "range", "targeting", "origin",
                "effect", "pathBlocking", "effectSpread", "check", "duration", "trigger", "exceptions", "sourceLocations")) {
            if (!definition.has(field)) throw new IllegalArgumentException("ability definition field is missing: " + field);
        }
        JsonNode sources = definition.path("sourceLocations");
        if (!sources.isArray() || sources.isEmpty()) throw new IllegalArgumentException("ability definition source locations are required");
        for (JsonNode source : sources) {
            boolean matched = evidence.stream().anyMatch(item -> item.knowledgeDocumentId().value().toString().equals(source.path("documentId").asText())
                    && item.extractionVersion() == source.path("extractionVersion").asLong(-1)
                    && item.locator().equals(source.path("locator").asText()));
            if (!matched) throw new IllegalArgumentException("ability definition source location is outside retrieved evidence");
        }
    }

    private JsonNode schema() {
        ObjectNode props = mapper.createObjectNode();
        props.putObject("abilityId").put("type", "string");
        props.putObject("name").put("type", "string");
        for (String field : List.of("actionTiming", "resourceCost", "visibility", "range", "targeting", "origin",
                "effect", "pathBlocking", "effectSpread", "check", "duration", "trigger", "exceptions"))
            props.putObject(field).set("type", mapper.valueToTree(List.of("string", "null")));
        ObjectNode sourceSchema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        ObjectNode sourceProps = sourceSchema.putObject("properties");
        sourceProps.putObject("documentId").put("type", "string");
        sourceProps.putObject("extractionVersion").put("type", "integer");
        sourceProps.putObject("locator").put("type", "string");
        sourceSchema.set("required", mapper.valueToTree(List.of("documentId", "extractionVersion", "locator")));
        ObjectNode sources = props.putObject("sourceLocations").put("type", "array").put("minItems", 1);
        sources.set("items", sourceSchema);
        ObjectNode schema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.set("properties", props);
        schema.set("required", mapper.valueToTree(List.of("abilityId", "name", "actionTiming", "resourceCost", "visibility", "range",
                "targeting", "origin", "effect", "pathBlocking", "effectSpread", "check", "duration", "trigger", "exceptions", "sourceLocations")));
        return schema;
    }

    private JsonNode readSheet(UUID id) { try { return mapper.readTree(characterSheets.read(id)); } catch (Exception failure) { throw new IllegalStateException("character sheet read failed", failure); } }
    private JsonNode parseState(JsonNode sheet) { try { return mapper.readTree(sheet.path("characterState").asText("{}")); } catch (Exception failure) { throw new IllegalStateException("character sheet state is invalid", failure); } }
}

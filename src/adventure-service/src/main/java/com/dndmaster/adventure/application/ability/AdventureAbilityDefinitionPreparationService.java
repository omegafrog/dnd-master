package com.dndmaster.adventure.application.ability;

import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.runtime.GmProviderBindingRepository;
import com.dndmaster.adventure.application.runtime.RuntimeCharacterSheetReadPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchRequest;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceLocationLookupPort;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.preparation.InitialSituationGmPort;
import com.dndmaster.adventure.application.scenario.preparation.TransientInitialSituationGmFailureException;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics;
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
    private final RuntimeEvidenceLocationLookupPort locationLookup;
    private final GmProviderBindingRepository providerBindings;
    private final InitialSituationGmPort gm;
    private final ObjectMapper mapper;

    public AdventureAbilityDefinitionPreparationService(AdventureAbilityDefinitionRepository definitions,
            ScenarioPackageRepository packages, SessionKnowledgeSetRepository knowledgeSets,
            AdventureSessionRepository sessions, RuntimeCharacterSheetReadPort characterSheets,
            RuntimeEvidenceSearchPort evidenceSearch, RuntimeEvidenceLocationLookupPort locationLookup,
            GmProviderBindingRepository providerBindings,
            InitialSituationGmPort gm, ObjectMapper mapper) {
        this.definitions = Objects.requireNonNull(definitions);
        this.packages = Objects.requireNonNull(packages);
        this.knowledgeSets = Objects.requireNonNull(knowledgeSets);
        this.sessions = Objects.requireNonNull(sessions);
        this.characterSheets = Objects.requireNonNull(characterSheets);
        this.evidenceSearch = Objects.requireNonNull(evidenceSearch);
        this.locationLookup = Objects.requireNonNull(locationLookup);
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
        List<String> requestedAbilityIds = abilityIds.stream().distinct().toList();
        if (sourceDefinition != null && !sourceDefinition.isNull()) {
            try { validateSourceInputSet(requestedAbilityIds, sourceDefinition); }
            catch (IllegalArgumentException invalid) {
                diagnostic("source_input_required", adventureId, sessionId, requestedAbilityIds.isEmpty() ? "none" : requestedAbilityIds.getFirst(), 0,
                        "one_source_definition_per_requested_ability", 0, "invalid_source_input_set",
                        "requestedAbilityCount=" + requestedAbilityIds.size() + ";actualInputType=" + nodeType(sourceDefinition));
                throw new AbilityDefinitionSourceInputRequiredException(requestedAbilityIds.isEmpty() ? "none" : requestedAbilityIds.getFirst(), invalid);
            }
        }
        for (String abilityId : requestedAbilityIds) {
            long started = System.nanoTime();
            diagnostic("started", adventureId, sessionId, abilityId, 0, "valid_definition_saved", 0, "none",
                    "requestedAbilityCount=" + requestedAbilityIds.size() + ";sourceInputProvided="
                            + (sourceDefinition != null && !sourceDefinition.isNull()));
            try {
                if (definitions.find(adventureId.value(), abilityId).isPresent()) {
                    diagnostic("complete", adventureId, sessionId, abilityId, 0, "existing_definition_reused",
                            (System.nanoTime() - started) / 1_000_000, "none", "definitionAlreadyPrepared=true");
                    continue;
                }
            } catch (RuntimeException failure) {
                diagnostic("failed", adventureId, sessionId, abilityId, 0, "definition_repository_lookup",
                        (System.nanoTime() - started) / 1_000_000, failure.getClass().getSimpleName(),
                        "definitionLookupCompleted=false");
                throw failure;
            }
            List<RuntimeEvidence> evidence = List.of();
            Map<UUID, Long> authorizedSources = Map.of();
            EvidenceSearchScope sourceScope = null;
            JsonNode proposed;
            try {
                if (sourceDefinition != null && !sourceDefinition.isNull()) {
                    SelectedSources selected = selectedSources(adventureId, sessionId, owner);
                    authorizedSources = selected.authorizedSourceVersions();
                    sourceScope = selected.sourceScope();
                    proposed = selectSourceDefinition(abilityId, sourceDefinition);
                    validate(abilityId, proposed, evidence, authorizedSources, true);
                    validateSourceLocators(proposed, evidence, sourceScope);
                } else {
                    SearchResult searchResult = searchWithRecovery(adventureId, sessionId, owner, abilityId);
                    evidence = searchResult.evidence();
                    authorizedSources = searchResult.authorizedSourceVersions();
                    sourceScope = searchResult.sourceScope();
                    proposed = generateValidated(adventureId, sessionId, owner, abilityId, evidence, authorizedSources);
                }
            } catch (RuntimeException failure) {
                String failureKind = failure instanceof AbilityDefinitionValidationException invalid
                        ? "validation_code=" + invalid.code() : failure.getClass().getSimpleName();
                if (requiresSourceInput(failure)) {
                    diagnostic("source_input_required", adventureId, sessionId, abilityId, evidence.size(),
                            "validated_definition", (System.nanoTime() - started) / 1_000_000, failureKind,
                            "sourceInputRequired=true;actualEvidenceCount=" + evidence.size());
                    throw new AbilityDefinitionSourceInputRequiredException(abilityId, failure);
                }
                diagnostic("failed", adventureId, sessionId, abilityId, evidence.size(),
                        "validated_definition", (System.nanoTime() - started) / 1_000_000, failureKind,
                        "sourceInputRequired=false;actualEvidenceCount=" + evidence.size());
                throw failure;
            }
            try {
                definitions.prepare(adventureId.value(), abilityId, proposed);
            } catch (RuntimeException failure) {
                diagnostic("failed", adventureId, sessionId, abilityId, evidence.size(), "definition_saved",
                        (System.nanoTime() - started) / 1_000_000, failure.getClass().getSimpleName(),
                        "definitionValidationCompleted=true;definitionSaveCompleted=false");
                throw failure;
            }
            diagnostic("complete", adventureId, sessionId, abilityId, evidence.size(), "valid_definition_saved",
                    (System.nanoTime() - started) / 1_000_000, "none",
                    "sourceDefinitionFields=" + fieldInventory(proposed));
        }
    }

    JsonNode generateValidated(AdventureId adventureId, SessionId sessionId, OwnerPlayerId owner, String abilityId,
            List<RuntimeEvidence> evidence, Map<UUID, Long> authorizedSources) {
        List<String> corrections = new ArrayList<>();
        boolean providerFailureRetried = false;
        while (true) {
            long attemptStarted = System.nanoTime();
            JsonNode proposed;
            try {
                proposed = generate(adventureId, sessionId, owner, abilityId, evidence, corrections);
            } catch (TransientInitialSituationGmFailureException failure) {
                if (providerFailureRetried) {
                    diagnostic("generation_failed", adventureId, sessionId, abilityId, evidence.size(),
                            "successful_structured_response", (System.nanoTime() - attemptStarted) / 1_000_000,
                            failure.getClass().getSimpleName(), "providerTransientRetryAlreadyUsed=true");
                    throw failure;
                }
                providerFailureRetried = true;
                corrections.add("GENERATION_FAILURE_RETRY");
                diagnostic("generation_retry", adventureId, sessionId, abilityId, evidence.size(),
                        "single_provider_failure_retry", (System.nanoTime() - attemptStarted) / 1_000_000,
                        failure.getClass().getSimpleName());
                continue;
            }
            try {
                validate(abilityId, proposed, evidence, authorizedSources, false);
                return proposed;
            } catch (AbilityDefinitionValidationException invalid) {
                String correction = invalid.code();
                if (corrections.contains(correction)) {
                    diagnostic("validation_recovery_exhausted", adventureId, sessionId, abilityId, evidence.size(),
                            "new_validation_code", (System.nanoTime() - attemptStarted) / 1_000_000,
                            "validation_code=" + correction, "actualFields=" + fieldInventory(proposed));
                    throw invalid;
                }
                corrections.add(correction);
                diagnostic("structured_output_repair", adventureId, sessionId, abilityId, evidence.size(),
                        "new_validation_feedback", (System.nanoTime() - attemptStarted) / 1_000_000,
                        "validation_code=" + correction,
                        "expectedFields=" + expectedFieldInventory() + ";actualFields=" + fieldInventory(proposed));
            }
        }
    }

    private void validateSourceInputSet(List<String> requestedIds, JsonNode input) {
        List<String> suppliedIds;
        if (input.isObject()) suppliedIds = List.of(input.path("abilityId").asText(""));
        else if (input.isArray()) {
            suppliedIds = new ArrayList<>();
            input.forEach(item -> suppliedIds.add(item.path("abilityId").asText("")));
        } else throw new IllegalArgumentException("source definition must be an object or an array of objects");
        if (suppliedIds.stream().anyMatch(String::isBlank) || suppliedIds.size() != suppliedIds.stream().distinct().count()
                || !new java.util.HashSet<>(suppliedIds).equals(new java.util.HashSet<>(requestedIds)))
            throw new IllegalArgumentException("source definitions must provide exactly one entry for every requested ability ID");
    }

    private JsonNode selectSourceDefinition(String abilityId, JsonNode input) {
        if (input.isObject()) return input;
        for (JsonNode item : input) if (abilityId.equals(item.path("abilityId").asText())) return item;
        throw new SourceDefinitionInputException("source definition for the requested ability ID is missing");
    }

    private static void diagnostic(String outcome, AdventureId adventure, SessionId session, String abilityId,
            int evidenceCount, String expected, long elapsedMs, String failureKind) {
        diagnostic(outcome, adventure, session, abilityId, evidenceCount, expected, elapsedMs, failureKind, "not_applicable");
    }

    private static void diagnostic(String outcome, AdventureId adventure, SessionId session, String abilityId,
            int evidenceCount, String expected, long elapsedMs, String failureKind, String actual) {
        if (DevelopmentDiagnostics.enabled())
            LOGGER.info("dev_ability_preparation outcome={} adventureId={} sessionId={} abilityId={} evidenceCount={} expected={} actual={} failureKind={} elapsedMs={} recovery={}",
                    outcome, adventure.value(), session.value(), abilityId, evidenceCount, expected, actual, failureKind,
                    elapsedMs, recoveryFor(outcome));
    }

    private static String recoveryFor(String outcome) {
        return switch (outcome) {
            case "started" -> "prepare_definition";
            case "complete" -> "none";
            case "search_retry", "search_query_empty" -> "try_next_distinct_query";
            case "source_type_search_complete" -> "continue_search_and_retain_evidence";
            case "source_type_search_failure" -> "search_other_selected_source_type";
            case "evidence_query_complete" -> "try_next_distinct_query";
            case "generation_retry" -> "retry_transient_provider_once";
            case "structured_output_repair" -> "regenerate_with_validation_feedback";
            case "validation_recovery_exhausted", "source_input_required" -> "resubmit_source_definition";
            case "failed", "generation_failed" -> "resolve_failure_then_retry_operation";
            default -> "continue_preparation_flow";
        };
    }

    private static boolean requiresSourceInput(RuntimeException failure) {
        return failure instanceof AbilityDefinitionValidationException
                || failure instanceof AbilityEvidenceRecoveryExhaustedException
                || failure instanceof SourceDefinitionInputException;
    }

    private static String expectedFieldInventory() {
        return "abilityId,name,actionTiming,resourceCost,visibility,range,targeting,origin,effect,pathBlocking,effectSpread,check,duration,trigger,exceptions,actionKind,rangeForm,targetMode,originMode,effectShape,pathBlockingRule,propagationRule,verticalDirection,sourceLocations";
    }

    private static String fieldInventory(JsonNode node) {
        if (node == null || !node.isObject()) return "nodeType=" + nodeType(node);
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return String.join(",", fields.stream().sorted().toList());
    }

    private static String nodeType(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        return "other";
    }

    public void activate(UUID adventureId, List<String> abilityIds) {
        abilityIds.forEach(id -> definitions.activate(adventureId, id));
    }

    private SearchResult searchWithRecovery(AdventureId adventureId, SessionId sessionId, OwnerPlayerId owner, String abilityId) {
        SelectedSources selected = selectedSources(adventureId, sessionId, owner);
        if (selected.authorizedSourceVersions().isEmpty())
            throw new AbilityEvidenceSearchFailureException(null);
        String searchableId = abilityId.replaceAll("[^\\p{Alnum}]+", " ").trim();
        List<String> queries = List.of(
                "Prepare the ability " + abilityId + " from this source type",
                "Find rules and details for " + searchableId,
                "Ability " + searchableId + " rules, effects, range, targets, and cost");
        List<String> distinctQueries = queries.stream().distinct().toList();
        RuntimeException lastFailure = null;
        int completedQueries = 0;
        SearchResult lastSuccessfulResult = null;
        java.util.LinkedHashSet<RuntimeEvidence> collectedEvidence = new java.util.LinkedHashSet<>();
        for (String query : distinctQueries) {
            long started = System.nanoTime();
            try {
                SearchResult result = search(adventureId, sessionId, owner, abilityId, query, selected);
                completedQueries++;
                lastSuccessfulResult = result;
                collectedEvidence.addAll(result.evidence());
                diagnostic(result.evidence().isEmpty() ? "search_query_empty" : "evidence_query_complete",
                        adventureId, sessionId, abilityId, collectedEvidence.size(), "evidence_candidates",
                        (System.nanoTime() - started) / 1_000_000, "none",
                        "queryResultCount=" + result.evidence().size() + ";aggregateEvidenceCount=" + collectedEvidence.size());
            } catch (RuntimeException failure) {
                lastFailure = failure;
                diagnostic("search_retry", adventureId, sessionId, abilityId, collectedEvidence.size(), "evidence_candidates",
                        (System.nanoTime() - started) / 1_000_000, failure.getClass().getSimpleName(),
                        "queryResult=unavailable;aggregateEvidenceCount=" + collectedEvidence.size());
            }
        }
        if (!collectedEvidence.isEmpty() && lastSuccessfulResult != null)
            return new SearchResult(List.copyOf(collectedEvidence), lastSuccessfulResult.authorizedSourceVersions(),
                    lastSuccessfulResult.sourceScope());
        if (completedQueries == distinctQueries.size()) throw new AbilityEvidenceRecoveryExhaustedException(null);
        throw new AbilityEvidenceSearchFailureException(lastFailure);
    }

    private SearchResult search(AdventureId adventureId, SessionId sessionId, OwnerPlayerId owner, String abilityId,
            String query, SelectedSources selectedSources) {
        UUID packageId = selectedSources.packageId();
        var adventure = new com.dndmaster.adventure.domain.adventure.AdventureId(adventureId.value());
        List<RuntimeEvidence> result = new ArrayList<>();
        RuntimeException lastFailure = null;
        for (RuntimeEvidenceType type : List.of(RuntimeEvidenceType.RULEBOOK, RuntimeEvidenceType.STORYBOOK)) {
            List<ScenarioBundleDocumentSelection> documents = selectedSources.documents().stream()
                    .filter(doc -> type.name().equalsIgnoreCase(doc.documentType())).toList();
            if (documents.isEmpty()) continue;
            Map<UUID, Long> versions = documents.stream().collect(java.util.stream.Collectors.toMap(
                    doc -> doc.knowledgeDocumentId().value(), ScenarioBundleDocumentSelection::extractionVersion));
            long searchStarted = System.nanoTime();
            try {
                var found = evidenceSearch.search(new RuntimeEvidenceSearchRequest(adventure, owner, sessionId, packageId,
                        documents.stream().map(doc -> doc.knowledgeDocumentId().value()).toList(), null,
                        query, type, 8, versions,
                        "ability:" + abilityId, "RULE_LOOKUP"));
                result.addAll(found);
                diagnostic("source_type_search_complete", adventureId, sessionId, abilityId, result.size(),
                        "independent_evidence_search", (System.nanoTime() - searchStarted) / 1_000_000,
                        "none", "sourceType=" + type.name() + ";queryResultCount=" + found.size()
                                + ";aggregateEvidenceCount=" + result.size());
            } catch (RuntimeException failure) {
                lastFailure = failure;
                diagnostic("source_type_search_failure", adventureId, sessionId, abilityId, result.size(),
                        "other_selected_source_types", (System.nanoTime() - searchStarted) / 1_000_000,
                        failure.getClass().getSimpleName(), "sourceType=" + type.name()
                                + ";queryResult=unavailable;aggregateEvidenceCount=" + result.size());
            }
        }
        if (result.isEmpty() && lastFailure != null) throw lastFailure;
        return new SearchResult(List.copyOf(result), selectedSources.authorizedSourceVersions(), selectedSources.sourceScope());
    }

    private SelectedSources selectedSources(AdventureId adventureId, SessionId sessionId, OwnerPlayerId owner) {
        var session = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("adventure session not found"));
        UUID packageId = packages.findById(session.scenarioPackageId()).orElseThrow().packageId();
        var knowledgeSet = knowledgeSets.findBySessionId(sessionId).orElseThrow(() -> new IllegalStateException("session knowledge set not found"));
        var selectedIds = knowledgeSet.knowledgeDocumentIds().stream().map(id -> id.value()).toList();
        var scenarioPackage = packages.findById(packageId).orElseThrow();
        List<ScenarioBundleDocumentSelection> selected = scenarioPackage.documents().stream()
                .filter(doc -> selectedIds.contains(doc.knowledgeDocumentId().value())).toList();
        Map<UUID, Long> authorizedSources = selected.stream()
                .filter(doc -> RuntimeEvidenceType.RULEBOOK.name().equalsIgnoreCase(doc.documentType())
                        || RuntimeEvidenceType.STORYBOOK.name().equalsIgnoreCase(doc.documentType()))
                .collect(java.util.stream.Collectors.toMap(doc -> doc.knowledgeDocumentId().value(),
                        ScenarioBundleDocumentSelection::extractionVersion, (first, ignored) -> first));
        var sourceDocuments = selected.stream()
                .filter(doc -> authorizedSources.containsKey(doc.knowledgeDocumentId().value()))
                .map(doc -> new EvidenceSearchScope.Document(doc.knowledgeDocumentId().value(), doc.extractionVersion(),
                        doc.documentType().toUpperCase(java.util.Locale.ROOT))).toList();
        EvidenceSearchScope sourceScope = sourceDocuments.isEmpty() ? null : new EvidenceSearchScope(owner.value(),
                sessionId.value(), packageId, "ability-definition", "RULE_LOOKUP", sourceDocuments, List.of());
        return new SelectedSources(packageId, selected, Map.copyOf(authorizedSources), sourceScope);
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

    void validate(String abilityId, JsonNode definition, List<RuntimeEvidence> evidence) {
        validate(abilityId, definition, evidence, Map.of(), false);
    }

    void validate(String abilityId, JsonNode definition, List<RuntimeEvidence> evidence,
            Map<UUID, Long> authorizedSources, boolean userSourceInput) {
        java.util.Set<String> expectedFields = new java.util.LinkedHashSet<>(List.of("abilityId", "name", "actionTiming",
                "resourceCost", "visibility", "range", "targeting", "origin", "effect", "pathBlocking",
                "effectSpread", "check", "duration", "trigger", "exceptions", "actionKind", "rangeForm", "targetMode",
                "originMode", "effectShape", "pathBlockingRule", "propagationRule", "verticalDirection", "sourceLocations"));
        java.util.Set<String> actualFields = new java.util.LinkedHashSet<>();
        if (definition != null && definition.isObject()) definition.fieldNames().forEachRemaining(actualFields::add);
        if (!definition.isObject() || definition.size() != expectedFields.size() || !actualFields.equals(expectedFields)
                || !definition.path("abilityId").isTextual() || !abilityId.equals(definition.path("abilityId").asText()))
            throw new AbilityDefinitionValidationException("SCHEMA_OR_IDENTIFIER", "ability definition schema or identifier is invalid");
        if (!definition.path("name").isTextual() || definition.path("name").asText().isBlank())
            throw new AbilityDefinitionValidationException("NAME_INVALID", "ability definition name must be a nonempty string");
        for (String field : List.of("name", "actionTiming", "resourceCost", "visibility", "range", "targeting", "origin",
                "effect", "pathBlocking", "effectSpread", "check", "duration", "trigger", "exceptions", "sourceLocations")) {
            if (!definition.has(field)) throw new AbilityDefinitionValidationException("MISSING_FIELD:" + field,
                    "ability definition field is missing: " + field);
        }
        for (String field : List.of("actionTiming", "resourceCost", "visibility", "range", "targeting", "origin",
                "effect", "pathBlocking", "effectSpread", "check", "duration", "trigger", "exceptions")) {
            JsonNode value = definition.get(field);
            if (!value.isNull() && !value.isTextual()) throw new AbilityDefinitionValidationException("FIELD_TYPE:" + field,
                    "ability definition field must be a string or null: " + field);
        }
        Map<String, List<String>> allowedEnums = allowedEnumValues();
        for (Map.Entry<String, List<String>> entry : allowedEnums.entrySet()) {
            JsonNode value = definition.get(entry.getKey());
            if (!value.isNull() && (!value.isTextual() || !entry.getValue().contains(value.asText())))
                throw new AbilityDefinitionValidationException("ENUM_VALUE:" + entry.getKey(),
                        "ability definition enum value is not supported: " + entry.getKey());
        }
        JsonNode sources = definition.path("sourceLocations");
        if (!sources.isArray() || sources.isEmpty() || sources.size() > 10) throw new AbilityDefinitionValidationException("SOURCE_LOCATIONS_REQUIRED",
                "ability definition source locations are required");
        for (JsonNode source : sources) {
            java.util.Set<String> sourceFields = new java.util.LinkedHashSet<>();
            if (source.isObject()) source.fieldNames().forEachRemaining(sourceFields::add);
            if (!source.isObject() || source.size() != 3 || !sourceFields.equals(java.util.Set.of("documentId", "extractionVersion", "locator"))
                    || !source.path("documentId").isTextual() || !source.path("locator").isTextual()
                    || source.path("locator").asText().isBlank() || !source.path("extractionVersion").canConvertToLong()
                    || source.path("extractionVersion").asLong() < 1)
                throw new AbilityDefinitionValidationException("SOURCE_LOCATION_SHAPE", "ability definition source location is invalid");
            try { UUID.fromString(source.path("documentId").asText()); }
            catch (IllegalArgumentException malformed) {
                throw new AbilityDefinitionValidationException("SOURCE_LOCATION_SHAPE", "ability definition source location is invalid");
            }
            UUID documentId = UUID.fromString(source.path("documentId").asText());
            long extractionVersion = source.path("extractionVersion").asLong(-1);
            boolean matched = userSourceInput
                    ? authorizedSources.getOrDefault(documentId, -1L) == extractionVersion
                    : evidence.stream().anyMatch(item -> item.knowledgeDocumentId().value().equals(documentId)
                            && item.extractionVersion() == extractionVersion
                            && item.locator().equals(source.path("locator").asText()));
            if (!matched) throw new AbilityDefinitionValidationException("SOURCE_LOCATION_NOT_RETRIEVED",
                    userSourceInput ? "ability definition source document is not selected for this adventure"
                            : "ability definition source location is outside retrieved evidence");
        }
    }

    void validateSourceLocators(JsonNode definition, List<RuntimeEvidence> evidence, EvidenceSearchScope scope) {
        if (scope == null) throw new AbilityDefinitionValidationException("SOURCE_SCOPE_EMPTY",
                "no selected rule or story source is available for source input");
        List<RuntimeEvidence> unresolved = new ArrayList<>();
        for (JsonNode source : definition.path("sourceLocations")) {
            UUID documentId = UUID.fromString(source.path("documentId").asText());
            long version = source.path("extractionVersion").asLong();
            String locator = source.path("locator").asText();
            boolean retrieved = evidence.stream().anyMatch(item -> item.knowledgeDocumentId().value().equals(documentId)
                    && item.extractionVersion() == version && item.locator().equals(locator));
            if (retrieved) continue;
            RuntimeEvidenceType type = scope.documents().stream().filter(doc -> doc.id().equals(documentId)
                    && doc.extractionVersion() == version).map(doc -> RuntimeEvidenceType.valueOf(doc.type())).findFirst()
                    .orElseThrow(() -> new AbilityDefinitionValidationException("SOURCE_LOCATION_NOT_SELECTED",
                            "ability definition source document is not selected for this adventure"));
            unresolved.add(new RuntimeEvidence(type, new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(documentId),
                    version, locator, "source location verification anchor"));
        }
        if (unresolved.isEmpty()) return;
        List<RuntimeEvidence> indexed = locationLookup.findOnSamePages(scope, unresolved, 1);
        for (RuntimeEvidence anchor : unresolved) {
            if (indexed == null || indexed.stream().noneMatch(item -> item.knowledgeDocumentId().equals(anchor.knowledgeDocumentId())
                    && item.extractionVersion() == anchor.extractionVersion() && item.locator().equals(anchor.locator())))
                throw new AbilityDefinitionValidationException("SOURCE_LOCATOR_NOT_INDEXED",
                        "ability definition source locator is not present in the selected source index");
        }
    }

    private JsonNode schema() {
        ObjectNode props = mapper.createObjectNode();
        props.putObject("abilityId").put("type", "string");
        props.putObject("name").put("type", "string");
        for (String field : List.of("actionTiming", "resourceCost", "visibility", "range", "targeting", "origin",
                "effect", "pathBlocking", "effectSpread", "check", "duration", "trigger", "exceptions"))
            props.putObject(field).set("type", mapper.valueToTree(List.of("string", "null")));
        enumProperty(props, "actionKind", com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.ActionKind.values());
        enumProperty(props, "rangeForm", com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.RangeForm.values());
        enumProperty(props, "targetMode", com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.TargetMode.values());
        enumProperty(props, "originMode", com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.OriginMode.values());
        enumProperty(props, "effectShape", com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.EffectShape.values());
        enumProperty(props, "pathBlockingRule", com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.PathBlockingRule.values());
        enumProperty(props, "propagationRule", com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.PropagationRule.values());
        enumProperty(props, "verticalDirection", com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.VerticalDirection.values());
        ObjectNode sourceSchema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        ObjectNode sourceProps = sourceSchema.putObject("properties");
        sourceProps.putObject("documentId").put("type", "string");
        sourceProps.putObject("extractionVersion").put("type", "integer");
        sourceProps.putObject("locator").put("type", "string");
        sourceSchema.set("required", mapper.valueToTree(List.of("documentId", "extractionVersion", "locator")));
        ObjectNode sources = props.putObject("sourceLocations").put("type", "array").put("minItems", 1).put("maxItems", 10);
        sources.set("items", sourceSchema);
        ObjectNode schema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.set("properties", props);
        schema.set("required", mapper.valueToTree(List.of("abilityId", "name", "actionTiming", "resourceCost", "visibility", "range",
                "targeting", "origin", "effect", "pathBlocking", "effectSpread", "check", "duration", "trigger", "exceptions",
                "actionKind", "rangeForm", "targetMode", "originMode", "effectShape", "pathBlockingRule", "propagationRule",
                "verticalDirection", "sourceLocations")));
        return schema;
    }

    private void enumProperty(ObjectNode properties, String name, Enum<?>[] values) {
        var schema = properties.putObject(name);
        schema.set("type", mapper.valueToTree(List.of("string", "null")));
        var allowedValues = mapper.createArrayNode();
        java.util.Arrays.stream(values).map(Enum::name).forEach(allowedValues::add);
        allowedValues.addNull();
        schema.set("enum", allowedValues);
    }

    private static Map<String, List<String>> allowedEnumValues() {
        return Map.of(
                "actionKind", java.util.Arrays.stream(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.ActionKind.values()).map(Enum::name).toList(),
                "rangeForm", java.util.Arrays.stream(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.RangeForm.values()).map(Enum::name).toList(),
                "targetMode", java.util.Arrays.stream(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.TargetMode.values()).map(Enum::name).toList(),
                "originMode", java.util.Arrays.stream(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.OriginMode.values()).map(Enum::name).toList(),
                "effectShape", java.util.Arrays.stream(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.EffectShape.values()).map(Enum::name).toList(),
                "pathBlockingRule", java.util.Arrays.stream(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.PathBlockingRule.values()).map(Enum::name).toList(),
                "propagationRule", java.util.Arrays.stream(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.PropagationRule.values()).map(Enum::name).toList(),
                "verticalDirection", java.util.Arrays.stream(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.VerticalDirection.values()).map(Enum::name).toList());
    }

    private JsonNode readSheet(UUID id) { try { return mapper.readTree(characterSheets.read(id)); } catch (Exception failure) { throw new IllegalStateException("character sheet read failed", failure); } }
    private JsonNode parseState(JsonNode sheet) { try { return mapper.readTree(sheet.path("characterState").asText("{}")); } catch (Exception failure) { throw new IllegalStateException("character sheet state is invalid", failure); } }

    private static final class AbilityDefinitionValidationException extends IllegalArgumentException {
        private final String code;
        private AbilityDefinitionValidationException(String code, String message) { super(message); this.code = code; }
        private String code() { return code; }
    }

    private static final class AbilityEvidenceRecoveryExhaustedException extends IllegalStateException {
        private AbilityEvidenceRecoveryExhaustedException(Throwable cause) {
            super("ability evidence search recovery was exhausted", cause);
        }
    }

    private static final class AbilityEvidenceSearchFailureException extends IllegalStateException {
        private AbilityEvidenceSearchFailureException(Throwable cause) {
            super("ability evidence search failed before it could complete", cause);
        }
    }

    private static final class SourceDefinitionInputException extends IllegalArgumentException {
        private SourceDefinitionInputException(String message) { super(message); }
    }

    private record SearchResult(List<RuntimeEvidence> evidence, Map<UUID, Long> authorizedSourceVersions,
            EvidenceSearchScope sourceScope) {}
    private record SelectedSources(UUID packageId, List<ScenarioBundleDocumentSelection> documents,
            Map<UUID, Long> authorizedSourceVersions, EvidenceSearchScope sourceScope) {}
}

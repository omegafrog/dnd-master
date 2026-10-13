package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.dndmaster.aigamemaster.infrastructure.ai.RequestedGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.EffectiveGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderSelectionResolver;
import com.dndmaster.aigamemaster.application.ai.AiExecutionUnavailableException;
import com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Role-specific, read-only AI boundaries. The AI process receives no tool,
 * database, filesystem, or generic HTTP capability through these contracts.
 */
@RestController
public final class TypedAgentContractController {
    private static final Logger log = LoggerFactory.getLogger(TypedAgentContractController.class);
    private static final org.springframework.ai.tokenizer.JTokkitTokenCountEstimator RULEBOOK_ACTION_TOKEN_ESTIMATOR =
            new org.springframework.ai.tokenizer.JTokkitTokenCountEstimator(com.knuddels.jtokkit.api.EncodingType.O200K_BASE);
    private static final JsonNode RUNTIME_TURN_OUTPUT_SCHEMA = loadRuntimeTurnOutputSchema();
    private static final String ACTION_LOOKUP_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["status","answer","supportingElementIds"],"properties":{"status":{"enum":["FOUND","NOT_FOUND"]},"answer":{"type":"string"},"supportingElementIds":{"type":"array","uniqueItems":true,"items":{"type":"string","minLength":1}}}}
            """.trim();
    private static final java.util.Set<String> RULEBOOK_ACTION_QUERY_IDS = java.util.Set.of("contents", "weapon-table", "spell-list",
            "ranged-actions", "thrown-actions", "shared-range", "shared-target-area", "shared-blocking", "shared-cost-check-effect", "assigned-actions");
    private static final java.util.Set<String> RULEBOOK_ACTION_FIELD_IDS = java.util.Set.of("level", "castingTime", "cost", "range", "rangeForm",
            "components", "duration", "school", "conditions", "attackSave", "damageEffect", "effect", "targetMode", "originMode",
            "effectShape", "pathBlocking", "propagation", "verticalDirection", "normalRangeFeet", "longRangeFeet", "lengthFeet",
            "widthFeet", "heightFeet", "radiusFeet");
    private final GmCompletionAdapter adapter;
    private final ObjectMapper mapper;
    private final ApiRequestGuard requestGuard;
    private final java.util.function.Function<RequestedGmProviderSelection, GmProviderSelectionResolver.EndpointResolution> selectionResolver;

    @Autowired
    public TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper,
            @Value("${ai-game-master.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            AgentEndpointRegistry endpointRegistry) {
        this(adapter, mapper, new ApiRequestGuard(internalToken),
                new GmProviderSelectionResolver(endpointRegistry)::resolveEndpoint);
    }

    public TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard) {
        this(adapter, mapper, requestGuard, request -> testResolution(request));
    }

    TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard,
            int runtimeContextWindowTokens) {
        this(adapter, mapper, requestGuard, request -> testResolution(request));
    }

    TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard,
            java.util.function.Function<RequestedGmProviderSelection, GmProviderSelectionResolver.EndpointResolution> selectionResolver) {
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.requestGuard = Objects.requireNonNull(requestGuard, "request guard must not be null");
        this.selectionResolver = Objects.requireNonNull(selectionResolver);
    }

    private static GmProviderSelectionResolver.EndpointResolution testResolution(RequestedGmProviderSelection request) {
        java.util.UUID id = request.endpointId() == null ? java.util.UUID.nameUUIDFromBytes((request.provider() + "/" + request.model()).getBytes(java.nio.charset.StandardCharsets.UTF_8)) : request.endpointId();
        java.time.Instant version = java.time.Instant.EPOCH;
        var provider = switch (request.provider()) {
            case "codex-cli" -> com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint.Provider.CODEX_CLI;
            case "openai" -> com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint.Provider.OPENAI_COMPATIBLE;
            default -> com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint.Provider.OLLAMA;
        };
        var endpoint = new com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint(id, "test", provider,
                java.net.URI.create("http://localhost"), request.model(), null, true, version);
        return new GmProviderSelectionResolver.EndpointResolution(endpoint,
                new EffectiveGmProviderSelection(id, version, request.provider(), request.model(), request.reasoning()));
    }

    @PostMapping("/internal/gm/scenario-compilation")
    ResponseEntity<?> scenarioCompilation(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ScenarioCompilationRequest request) {
        requestGuard.internal(token);
        require(request);
        try {
            return ResponseEntity.ok(adapter.complete(request.soloPlayerId(), request.operationKey(),
                "ROLE=SCENARIO_COMPILATION\nSTORYBOOK_CONTEXT=" + request.storybookContext()
                        + "\nTASK=Compile the source-backed hidden ScenarioModel from the supplied Storybook excerpts. "
                        + "Find essential combat encounters: named or identifiable hostile groups that the Storybook makes part of the objective, a required obstacle, or an explicit triggered fight. Do not include every passing mention of a creature, non-hostile NPCs, or invented encounters. "
                        + "For each encounter, preserve enemy identity, count, and location only when the Storybook states or clearly supports them. "
                        + "Each element in actors, locations, objectives, revelations, encounters, relationships, and resolutionCriteria must include elementId, type, attributes, and sourceRefs; type is a non-empty string and every sourceRefs entry must follow the specified shape. "
                        + "Every encounter must be an object in scenarioModel.encounters with type combat-scenario, elementId, attributes.enemyKey, attributes.displayName, attributes.count, attributes.location, and sourceRefs. "
                        + "Each sourceRefs item must exactly cite a supplied DOCUMENT_ID, EXTRACTION_VERSION, and LOCATOR using {knowledgeDocumentId:{value:DOCUMENT_ID},extractionVersion:EXTRACTION_VERSION,locator:LOCATOR}. Never invent or rewrite a source reference. "
                        + "Also provide the ScenarioModel schema fields schemaVersion, actors, locations, objectives, revelations, encounters, relationships, resolutionCriteria, and startingSituation. schemaVersion must be an integer JSON number and startingSituation must be a plain text string. "
                        + "OUTPUT_CONTRACT=Return exactly one JSON object with status (READY or BLOCKED), scenarioModel when READY, and diagnostics. Do not use markdown or add text outside JSON.",
                this::parseCompilation));
        } catch (AiExecutionUnavailableException failure) {
            String code = "AI_EXECUTION_" + failure.failure().reason().name();
            log.warn("scenario compilation agent failed operationKey={} code={}", request.operationKey(), code);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ScenarioCompilationAgentError(code, request.operationKey(),
                            failure.getClass().getSimpleName(),
                            failure.failure().reason() != com.dndmaster.aigamemaster.application.ai.AiExecutionFailure.Reason.LOCAL_EXECUTION_FAILED));
        } catch (ProviderMalformedResponseException failure) {
            String code = "SCENARIO_COMPILATION_RESPONSE_INVALID";
            log.warn("scenario compilation agent returned invalid response operationKey={} failureType={} validationReason={}",
                    request.operationKey(), failure.getClass().getSimpleName(), "provider response malformed");
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ScenarioCompilationAgentError(code, request.operationKey(), failure.getClass().getSimpleName(), false));
        } catch (IllegalArgumentException failure) {
            String code = "SCENARIO_COMPILATION_RESPONSE_INVALID";
            if (runtimeDiagnosticsEnabled() && failure instanceof ScenarioCompilationShapeException shapeFailure) {
                log.warn("dev_scenario_compilation stage=output_validation operationKey={} code={} path={} expected={} actual={} recoveryAction=reject_response outcome=failed",
                        request.operationKey(), shapeFailure.code(), shapeFailure.path(), shapeFailure.expected(), shapeFailure.actual());
            }
            log.warn("scenario compilation agent returned invalid response operationKey={} failureType={} validationReason={}",
                    request.operationKey(), failure.getClass().getSimpleName(), safeScenarioCompilationFailureReason(failure));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ScenarioCompilationAgentError(code, request.operationKey(), failure.getClass().getSimpleName(), false));
        } catch (RuntimeException failure) {
            String code = "SCENARIO_COMPILATION_AGENT_FAILED";
            log.error("scenario compilation agent failed operationKey={} failureType={}",
                    request.operationKey(), failure.getClass().getName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ScenarioCompilationAgentError(code, request.operationKey(), failure.getClass().getSimpleName(), false));
        }
    }

    @PostMapping("/internal/gm/initial-situation")
    JsonNode initialSituation(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody InitialSituationRequest request) {
        requestGuard.internal(token);
        require(request);
        RequestedGmProviderSelection requested = new RequestedGmProviderSelection(
                request.endpointId(), request.provider(), request.model(), request.useSubagents() ? "medium" : request.reasoning());
        var resolution = selectionResolver.apply(requested);
        if (request.useSubagents() && resolution.endpoint().provider()
                != com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint.Provider.CODEX_CLI) {
            throw new IllegalArgumentException("subagent preparation requires the Codex provider");
        }
        var parser = (com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<JsonNode>) json -> {
                    try {
                        JsonNode result = mapper.reader()
                                .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                                .readTree(json);
                        if (result == null || !result.isObject()) throw new IllegalArgumentException("initial situation response must be a JSON object");
                        return result;
                    } catch (java.io.IOException invalid) {
                        throw new IllegalArgumentException("initial situation response is invalid JSON", invalid);
                    }
                };
        if (!request.useSubagents()) return adapter.completeWithResolution(request.soloPlayerId(), request.operationKey(), request.prompt(),
                parser, requested, resolution, null, request.outputSchema()).response();
        List<com.dndmaster.aigamemaster.application.ai.AiExecutionRequest.Subtask> subtasks = request.stageTasks().stream()
                .map(task -> new com.dndmaster.aigamemaster.application.ai.AiExecutionRequest.Subtask(task.stageId(), task.prompt(), task.outputSchema())).toList();
        return adapter.completeWithResolutionAndSubtasks(request.soloPlayerId(), request.operationKey(), request.prompt(),
                parser, requested, resolution, request.outputSchema(), subtasks).response();
    }

    private String rulebookActionPrompt(String stage, RulebookActionExtractionInput input) {
        return ("DISCOVERY".equals(stage)
                ? "TASK=Discover only explicitly named spells, ranged attacks, and thrown actions shown by a contents list, weapon table, or section heading in the supplied retrieved evidence. Return every named candidate from the supplied list or table, including its kind and one sourceAnchorId for the name. Do not return definition fields or infer names from examples. "
                : "ASSIGNED".equals(stage)
                    ? "TASK=For every assigned action ID, return exactly one candidateResults entry. Use COMPLETED only with a grounded definition, INSUFFICIENT_EVIDENCE when the supplied source cannot support one, and FAILED only for an unprocessable assigned item. A non-completed result must have a short reason and no definition. Never omit or duplicate an assigned ID. "
                    : "TASK=Structure only explicitly stated spells, ranged attacks, and thrown actions present in the supplied retrieved evidence. ")
                + ("DISCOVERY".equals(stage) ? "Return an empty candidates array if no names are present. " : "ASSIGNED".equals(stage) ? "Use the supplied sourceAnchorId from that assigned action's evidence IDs. " : "Return an empty definitions array if evidence has no supported definition. ")
                + "Never infer missing values. Use null for unknown values and UNSUPPORTED for values outside the allowed representation. For each assigned result, neededFields must list only fields materially relevant to this action but absent from its own evidence and necessary to complete its rule; an unknown/null value alone does not make a field needed. Do not request unrelated field categories. When assignedActions.neededFields is non-empty, those are the fields requested for this completion: fill only those values from supplied evidence, copy knownFields exactly, and leave all other values null unless already present in knownFields. When assignedActions.neededFields is empty and knownFields has no non-null values, perform the initial extraction from the action's own evidence, fill supported fields, and report materially relevant missing fields in the response neededFields. The response neededFields list means fields that remain missing after this completion. Return sharedRules only for concise rules found in the supplied evidence that can apply beyond this one named action. Include the explicit applicability conditions and one exact sourceAnchorId. For every input sharedRules id, return exactly one sharedRuleApplicability result with APPLIES, AMBIGUOUS, or NOT_APPLICABLE. Re-evaluate each rule for this action; never carry forward an earlier action's decision. Set APPLIES only when its conditions clearly include this action; otherwise use AMBIGUOUS or NOT_APPLICABLE. Only APPLIES shared-rule fields may fill requested fields that are currently null. Never infer that an ambiguous rule applies. "
                + "When every assignedActions.neededFields list is empty, return sharedRules as an empty array. For every sourceAnchorId, copy the id exactly from extractionInput.evidence[].anchors[].id belonging to an evidence id listed in that assigned action's evidenceIds; do not use line numbers, distances, document IDs, or candidate IDs. Each definition must cite one sourceAnchorId from the supplied evidence spans. Never write or paraphrase a citation; the caller will attach the exact source span selected by that id. Do not claim that retrieved evidence proves the whole rulebook has no other entries.\n"
                + "FIELD_MEANINGS=level is spell level; castingTime is stated activation time; cost is only a resource or action consumed to use the action, never a purchase price or market value (if the source gives only a price, cost is null); range and numeric range fields are stated distances; rangeForm is the stated distance form; components are stated requirements; duration is stated duration; school is stated spell school; conditions are stated use conditions; attackSave is the required attack or saving check; damageEffect is stated damage; effect is the stated outcome; targetMode is how a target is selected; originMode is where an area begins; effectShape is its geometry; pathBlocking is what blocks it; propagation is how it spreads; verticalDirection is its stated vertical direction; lengthFeet, widthFeet, heightFeet, radiusFeet and normalRangeFeet/longRangeFeet are only explicit measurements.\n"
                + "VALIDATED_EXTRACTION_INPUT=" + write(input);
    }

    @PostMapping("/internal/gm/rulebook-action-definitions/estimate")
    ResponseEntity<?> estimateRulebookActionInputTokens(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody JsonNode body) {
        requestGuard.internal(token);
        requireExactFields(body, java.util.Set.of("soloPlayerId", "endpointId", "provider", "model", "reasoning",
                "operationKey", "extractionInput", "outputSchema"), "rulebook action token estimate request");
        try {
            RulebookActionExtractionRequest request = mapper.treeToValue(body, RulebookActionExtractionRequest.class);
            RulebookActionExtractionInput input = validateRulebookActionInput(request.extractionInput(), request.outputSchema());
            String stage = request.outputSchema().path("x-stage").asText("DEFINITIONS");
            String rendered = "ROLE=RULEBOOK_ACTION_DEFINITION_EXTRACTION\n" + rulebookActionPrompt(stage, input)
                    + "\nOUTPUT_SCHEMA=" + write(request.outputSchema());
            int estimatedTokens = RULEBOOK_ACTION_TOKEN_ESTIMATOR.estimate(rendered);
            if (runtimeDiagnosticsEnabled()) log.info("dev_rulebook_action_extraction stage=token_estimate tokenizer=o200k_base promptChars={} schemaChars={} estimatedInputTokens={} includesProviderSystemAndToolOverhead=false outcome=completed",
                    rendered.length(), write(request.outputSchema()).length(), estimatedTokens);
            return ResponseEntity.ok(java.util.Map.of("estimatedInputTokens", estimatedTokens, "tokenizer", "o200k_base",
                    "includesProviderSystemAndToolOverhead", false));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalArgumentException("rulebook action token estimate request does not match its schema", invalid);
        }
    }

    @PostMapping("/internal/gm/rulebook-action-definitions")
    ResponseEntity<?> rulebookActionDefinitions(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody JsonNode body) {
        requestGuard.internal(token);
        requireExactFields(body, java.util.Set.of("soloPlayerId", "endpointId", "provider", "model", "reasoning",
                "operationKey", "extractionInput", "outputSchema"), "rulebook action extraction request");
        RulebookActionExtractionRequest request;
        try {
            request = mapper.treeToValue(body, RulebookActionExtractionRequest.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalArgumentException("rulebook action extraction request does not match its schema", invalid);
        }
        RulebookActionExtractionInput input;
        try {
            input = validateRulebookActionInput(request.extractionInput(), request.outputSchema());
        } catch (IllegalArgumentException invalid) {
            if (runtimeDiagnosticsEnabled()) log.warn("dev_rulebook_action_extraction stage=input_schema_validation expectedInputFields={} actualInputFields={} documentId={} extractionVersionLength={} queryCount={} evidenceCount={} expectedSourceIdsFromInput=true failureClass={} recoveryAction=repair_retrieval_dto outcome=failed",
                    List.of("documentId", "extractionVersion", "queryCoverage", "evidence"), nodeFieldNames(request.extractionInput()),
                    request.extractionInput().path("documentId").asText(""), request.extractionInput().path("extractionVersion").asText("").length(),
                    request.extractionInput().path("queryCoverage").isArray() ? request.extractionInput().path("queryCoverage").size() : -1,
                    request.extractionInput().path("evidence").isArray() ? request.extractionInput().path("evidence").size() : -1,
                    invalid.getClass().getSimpleName());
            throw invalid;
        }
        RequestedGmProviderSelection requested = new RequestedGmProviderSelection(
                request.endpointId(), request.provider(), request.model(), request.reasoning());
        var resolution = selectionResolver.apply(requested);
        if (Boolean.parseBoolean(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")))) {
            var effective = resolution.effectiveSelection();
            log.info("dev_rulebook_action_ai stage=model_selected operationKey={} requestedEndpointId={} requestedProvider={} requestedModel={} effectiveEndpointId={} effectiveProvider={} effectiveModel={} effectiveReasoning={} timeoutManagedByCaller=true outcome=selected",
                    request.operationKey(), requested.endpointId(), requested.provider(), requested.model(), effective.endpointId(), effective.provider(), effective.model(), effective.reasoning());
        }
        var parser = (com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<JsonNode>) json -> readObject(json);
        String stage = request.outputSchema().path("x-stage").asText("DEFINITIONS");
        String prompt = rulebookActionPrompt(stage, input);
        long aiStarted = System.nanoTime();
        JsonNode response;
        try {
            response = adapter.completeWithResolution(request.soloPlayerId(), request.operationKey(),
                    "ROLE=RULEBOOK_ACTION_DEFINITION_EXTRACTION\n" + prompt, parser, requested,
                    resolution, null, request.outputSchema()).response();
        } catch (AiExecutionUnavailableException failure) {
            if (failure.failure().reason() == com.dndmaster.aigamemaster.application.ai.AiExecutionFailure.Reason.OUTPUT_LIMIT) {
                if (runtimeDiagnosticsEnabled()) log.warn("dev_rulebook_action_extraction stage=provider_failure operationKey={} failureCode=OUTPUT_LIMIT expectedOutcome=completed actualOutcome=failed recoveryAction=fail_assigned_batch outcome=failed",
                        request.operationKey());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(java.util.Map.of("code", "OUTPUT_LIMIT"));
            }
            throw failure;
        }

        var violations = com.networknt.schema.SchemaRegistry.withDefaultDialect(com.networknt.schema.SpecificationVersion.DRAFT_2020_12)
                .getSchema(request.outputSchema()).validate(response);
        if (!violations.isEmpty()) {
            if (runtimeDiagnosticsEnabled()) log.warn("dev_rulebook_action_extraction stage=output_schema_validation expectedFields={} actualType={} actualFields={} violationCount={} violationPaths={} definitionCount={} sourceEvidenceIds={} elapsedMs={} failureKind=output_schema_mismatch recoveryAction=reject_agent_response outcome=failed",
                    textValues(request.outputSchema().path("required")), response == null ? "null" : response.getNodeType(),
                    nodeFieldNames(response), violations.size(), violations.stream().limit(20).map(item -> item.getInstanceLocation().toString()).toList(),
                    response != null && response.path("definitions").isArray() ? response.path("definitions").size() : -1,
                    response != null && response.path("definitions").isArray() ? sourceAnchorIds(response.path("definitions")) : List.of(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - aiStarted));
            throw new IllegalArgumentException("rulebook action extraction response does not match its output schema");
        }
        if ("ASSIGNED".equals(stage)) {
            try {
                validateAssignedResponse(input, response);
            } catch (IllegalArgumentException invalid) {
                if (runtimeDiagnosticsEnabled()) log.warn("dev_rulebook_action_extraction stage=assigned_result_validation expectedCandidateIds={} actualCandidateIds={} expectedCount={} actualResultCount={} definitionIds={} statusCounts={} failureClass={} outcome=failed",
                        input.assignedActions().stream().map(RulebookActionAssignedActionInput::id).toList(),
                        nodeTextValues(response.path("candidateResults"), "candidateId"), input.assignedActions().size(),
                        response.path("candidateResults").isArray() ? response.path("candidateResults").size() : -1,
                        nodeTextValues(response.path("definitions"), "id"), assignedStatusCounts(response.path("candidateResults")), invalid.getClass().getSimpleName());
                throw invalid;
            }
        }
        if (runtimeDiagnosticsEnabled()) log.info("dev_rulebook_action_extraction stage=output_schema_validation expectedFields={} actualFields={} definitionCount={} elapsedMs={} outcome=completed",
                textValues(request.outputSchema().path("required")), nodeFieldNames(response), response.path("definitions").size(),
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - aiStarted));
        return ResponseEntity.ok(response);
    }

    private RulebookActionExtractionInput validateRulebookActionInput(JsonNode raw, JsonNode outputSchema) {
        String stage = outputSchema.path("x-stage").asText("DEFINITIONS");
        java.util.Set<String> expectedInputFields = new java.util.HashSet<>(java.util.Set.of("documentId", "extractionVersion", "queryCoverage", "evidence"));
        if ("ASSIGNED".equals(stage)) expectedInputFields.add("assignedActions");
        requireExactFields(raw, expectedInputFields, "rulebook action extraction input");
        if (!raw.path("queryCoverage").isArray() || raw.path("queryCoverage").size() > 9
                || !raw.path("evidence").isArray() || raw.path("evidence").size() > 1024)
            throw new IllegalArgumentException("rulebook action extraction input exceeds its bounded schema");
        for (JsonNode query : raw.path("queryCoverage"))
            requireExactFields(query, java.util.Set.of("queryId", "queryFingerprint", "candidateCount"), "query coverage");
        for (JsonNode action : raw.path("assignedActions")) {
            requireExactFields(action, java.util.Set.of("id", "name", "actionKind", "sourceLocator", "evidenceIds", "knownFields", "neededFields", "sharedRules"), "assigned action");
            for (JsonNode rule : action.path("sharedRules"))
                requireExactFields(rule, java.util.Set.of("id", "conditions", "sourceLocator", "sourceEvidence", "fields"), "stored shared rule");
        }
        for (JsonNode evidence : raw.path("evidence")) {
            requireExactFields(evidence, java.util.Set.of("id", "locator", "anchors"), "retrieved evidence");
            if (!evidence.path("anchors").isArray() || evidence.path("anchors").isEmpty())
                throw new IllegalArgumentException("retrieved evidence must contain source spans");
            for (JsonNode anchor : evidence.path("anchors"))
                requireExactFields(anchor, java.util.Set.of("id", "text"), "retrieved evidence span");
        }
        try {
            RulebookActionExtractionInput input = mapper.treeToValue(raw, RulebookActionExtractionInput.class);
            boolean discoverySchema = "DISCOVERY".equals(outputSchema.path("x-stage").asText());
            long maxInputChars = "ASSIGNED".equals(outputSchema.path("x-stage").asText()) ? 1_000_000 : 24_000;
            long actualInputChars = input.evidence().stream().flatMap(item -> item.anchors().stream())
                    .mapToLong(item -> item.text().length()).sum();
            if (actualInputChars > maxInputChars) {
                if (runtimeDiagnosticsEnabled()) log.warn("dev_rulebook_action_extraction stage=input_size_validation stageName={} expectedInputCharsMax={} actualInputChars={} evidenceCount={} recoveryAction=reject_oversized_request outcome=failed",
                        outputSchema.path("x-stage").asText("DEFINITIONS"), maxInputChars, actualInputChars, input.evidence().size());
                throw new IllegalArgumentException("rulebook extraction input exceeds the stage text limit");
            }
            String resultField = discoverySchema ? "candidates" : "definitions";
            if (outputSchema == null || !outputSchema.path("additionalProperties").isBoolean()
                    || outputSchema.path("additionalProperties").booleanValue()
                    || !outputSchema.path("required").isArray() || !containsText(outputSchema.path("required"), resultField)
                    || !outputSchema.path("properties").path(resultField).path("items").path("additionalProperties").isBoolean()
                    || outputSchema.path("properties").path(resultField).path("items").path("additionalProperties").booleanValue())
                throw new IllegalArgumentException("rulebook action output schema must reject additional properties");
            if (!providerCompatibleSchema(outputSchema))
                throw new IllegalArgumentException("rulebook action output schema uses unsupported optional object fields or empty enums");
            com.networknt.schema.SchemaRegistry.withDefaultDialect(com.networknt.schema.SpecificationVersion.DRAFT_2020_12).getSchema(outputSchema);
            if ("DISCOVERY".equals(outputSchema.path("x-stage").asText())) {
                java.util.Set<String> expectedCandidateFields = java.util.Set.of("name", "actionKind", "sourceAnchorId");
                java.util.Set<String> actualCandidateFields = new java.util.HashSet<>();
                outputSchema.path("properties").path("candidates").path("items").path("properties").fieldNames().forEachRemaining(actualCandidateFields::add);
                if (!compactAnchorSchema(outputSchema.path("properties").path("candidates").path("items").path("properties").path("sourceAnchorId"))
                        || !textSet(outputSchema.path("properties").path("candidates").path("items").path("properties").path("sourceAnchorId").path("enum"))
                                .equals(anchorIds(input, null))
                        || !actualCandidateFields.equals(expectedCandidateFields)
                        || !textSet(outputSchema.path("properties").path("candidates").path("items").path("required")).equals(expectedCandidateFields))
                    throw new IllegalArgumentException("discovery output schema does not match the source candidate contract");
                return input;
            }
            if ("ASSIGNED".equals(stage)) {
                validateAssignedContract(input, outputSchema);
                return input;
            }
            java.util.Set<String> expectedItemFields = java.util.Set.of("id", "name", "actionKind", "sourceAnchorId", "unsupportedFields", "fields");
            java.util.Set<String> actualItemFields = new java.util.HashSet<>();
            outputSchema.path("properties").path("definitions").path("items").path("properties").fieldNames().forEachRemaining(actualItemFields::add);
            JsonNode itemSchema = outputSchema.path("properties").path("definitions").path("items");
            java.util.Set<String> fieldNames = new java.util.HashSet<>();
            itemSchema.path("properties").path("fields").path("properties").fieldNames().forEachRemaining(fieldNames::add);
            java.util.Set<String> requiredItemFields = textSet(itemSchema.path("required"));
            java.util.Set<String> requiredFields = textSet(itemSchema.path("properties").path("fields").path("required"));
            java.util.Set<String> expectedFields = java.util.Set.of("level", "castingTime", "cost", "range", "rangeForm", "components", "duration", "school",
                    "conditions", "attackSave", "damageEffect", "effect", "targetMode", "originMode", "effectShape", "pathBlocking", "propagation", "verticalDirection",
                    "normalRangeFeet", "longRangeFeet", "lengthFeet", "widthFeet", "heightFeet", "radiusFeet");
            if (!actualItemFields.equals(expectedItemFields) || !requiredItemFields.equals(expectedItemFields)
                    || !compactAnchorSchema(itemSchema.path("properties").path("sourceAnchorId"))
                    || !textSet(itemSchema.path("properties").path("sourceAnchorId").path("enum")).equals(anchorIds(input, null))
                    || !fieldNames.equals(expectedFields) || !requiredFields.equals(expectedFields)
                    || !itemSchema.path("properties").path("fields").path("additionalProperties").isBoolean()
                    || itemSchema.path("properties").path("fields").path("additionalProperties").booleanValue())
                throw new IllegalArgumentException("output schema fields do not match the extraction contract");
            return input;
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalArgumentException("rulebook action extraction input does not match its schema", invalid);
        }
    }

    private static java.util.Set<String> textSet(JsonNode array) {
        java.util.Set<String> values = new java.util.HashSet<>();
        if (array != null && array.isArray()) array.forEach(item -> { if (item.isTextual()) values.add(item.asText()); });
        return values;
    }

    private static boolean compactAnchorSchema(JsonNode schema) {
        return "string".equals(schema.path("type").asText())
                && "^[0-9]{1,6}$".equals(schema.path("pattern").asText())
                && schema.path("enum").isArray() && !schema.path("enum").isEmpty()
                && textSet(schema.path("enum")).size() == schema.path("enum").size()
                && textSet(schema.path("enum")).stream().allMatch(value -> value.matches("[0-9]{1,6}"));
    }

    private static java.util.Set<String> anchorIds(RulebookActionExtractionInput input, java.util.Set<String> evidenceIds) {
        java.util.Set<String> allowed = new java.util.LinkedHashSet<>();
        for (RulebookEvidenceInput evidence : input.evidence()) {
            if (evidenceIds == null || evidenceIds.contains(evidence.id()))
                evidence.anchors().forEach(anchor -> allowed.add(anchor.id()));
        }
        return allowed;
    }

    private void validateAssignedContract(RulebookActionExtractionInput input, JsonNode outputSchema) {
        java.util.Set<String> expectedIds = input.assignedActions().stream().map(RulebookActionAssignedActionInput::id).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> schemaIds = textSet(outputSchema.path("properties").path("candidateResults").path("items").path("properties").path("candidateId").path("enum"));
        if (expectedIds.isEmpty() || expectedIds.size() != input.assignedActions().size() || !schemaIds.equals(expectedIds)
                || outputSchema.path("properties").path("candidateResults").path("minItems").asInt(-1) != expectedIds.size()
                || outputSchema.path("properties").path("candidateResults").path("maxItems").asInt(-1) != expectedIds.size())
            throw new IllegalArgumentException("assigned result schema must require every assigned action exactly once");
        java.util.Set<String> expectedResultFields = java.util.Set.of("candidateId", "status", "reason", "neededFields", "sharedRules", "sharedRuleApplicability");
        JsonNode resultItem = outputSchema.path("properties").path("candidateResults").path("items");
        if (!fieldNames(resultItem.path("properties")).equals(expectedResultFields)
                || !textSet(resultItem.path("required")).equals(expectedResultFields)
                || !resultItem.path("properties").path("neededFields").path("items").path("enum").isArray())
            throw new IllegalArgumentException("assigned neededFields contract is invalid");
        JsonNode applicability = resultItem.path("properties").path("sharedRuleApplicability");
        JsonNode applicabilityItem = applicability.path("items");
        java.util.Set<String> allowedSharedIds = input.assignedActions().stream().flatMap(action -> action.sharedRules().stream())
                .map(RulebookActionSharedRuleInput::id).collect(java.util.stream.Collectors.toSet());
        if (!"array".equals(applicability.path("type").asText())
                || applicability.path("minItems").asInt(-1) != allowedSharedIds.size()
                || applicability.path("maxItems").asInt(-1) != allowedSharedIds.size()
                || (allowedSharedIds.isEmpty() ? applicabilityItem.path("properties").path("id").path("enum").isArray()
                        : !textSet(applicabilityItem.path("properties").path("id").path("enum")).equals(allowedSharedIds))
                || !textSet(applicabilityItem.path("properties").path("applicability").path("enum")).equals(java.util.Set.of("APPLIES", "AMBIGUOUS", "NOT_APPLICABLE")))
            throw new IllegalArgumentException("shared rule applicability schema is invalid");
        JsonNode sharedRules = resultItem.path("properties").path("sharedRules");
        JsonNode sharedRuleItem = sharedRules.path("items");
        boolean initialExtraction = input.assignedActions().stream().allMatch(action -> action.neededFields().isEmpty());
        java.util.Set<String> allAssignedEvidence = input.assignedActions().stream().flatMap(action -> action.evidenceIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> sharedAnchorIds = anchorIds(input, allAssignedEvidence);
        if (!"array".equals(sharedRules.path("type").asText()) || sharedRules.path("maxItems").asInt(-1) != (initialExtraction ? 0 : 8)
                || !textSet(sharedRuleItem.path("required")).equals(java.util.Set.of("applicability", "conditions", "sourceAnchorId", "fields"))
                || !compactAnchorSchema(sharedRuleItem.path("properties").path("sourceAnchorId"))
                || !textSet(sharedRuleItem.path("properties").path("sourceAnchorId").path("enum")).equals(sharedAnchorIds)
                || !fieldNames(sharedRuleItem.path("properties").path("fields").path("properties")).equals(RULEBOOK_ACTION_FIELD_IDS)
                || !textSet(sharedRuleItem.path("properties").path("fields").path("required")).equals(RULEBOOK_ACTION_FIELD_IDS))
            throw new IllegalArgumentException("shared rule response schema is invalid");
        java.util.Set<String> sourceIds = input.evidence().stream().map(RulebookEvidenceInput::id).collect(java.util.stream.Collectors.toSet());
        for (RulebookActionAssignedActionInput action : input.assignedActions()) {
            if (!sourceIds.containsAll(action.evidenceIds())) throw new IllegalArgumentException("assigned action references evidence outside the supplied input");
            if (!validRulebookActionFieldValues(action.knownFields())
                    || !RULEBOOK_ACTION_FIELD_IDS.containsAll(action.neededFields()))
                throw new IllegalArgumentException("assigned action contains an unknown field identifier");
            for (RulebookActionSharedRuleInput rule : action.sharedRules())
                if (!validRulebookActionFieldValues(rule.fields()) || rule.sourceEvidence().length() > 500)
                    throw new IllegalArgumentException("stored shared rule contains unsupported fields or an oversized source excerpt");
        }
        java.util.Set<String> definitionIds = textSet(outputSchema.path("properties").path("definitions").path("items").path("properties").path("id").path("enum"));
        java.util.Map<String, RulebookActionAssignedActionInput> expected = input.assignedActions().stream()
                .collect(java.util.stream.Collectors.toMap(RulebookActionAssignedActionInput::id, item -> item));
        java.util.Set<String> identityIds = new java.util.HashSet<>();
        for (JsonNode identity : outputSchema.path("properties").path("definitions").path("items").path("anyOf")) {
            String id = singletonEnumValue(identity.path("properties").path("id").path("enum"));
            RulebookActionAssignedActionInput action = expected.get(id);
            if (action == null || !action.name().equals(singletonEnumValue(identity.path("properties").path("name").path("enum")))
                    || !action.actionKind().equals(singletonEnumValue(identity.path("properties").path("actionKind").path("enum")))
                    || !textSet(identity.path("required")).equals(textSet(outputSchema.path("properties").path("definitions").path("items").path("required")))
                    || !fieldNames(identity.path("properties")).equals(fieldNames(outputSchema.path("properties").path("definitions").path("items").path("properties")))
                    || !compactAnchorSchema(identity.path("properties").path("sourceAnchorId"))
                    || !textSet(identity.path("properties").path("sourceAnchorId").path("enum")).equals(anchorIds(input, action == null ? java.util.Set.of() : java.util.Set.copyOf(action.evidenceIds())))
                    || !identity.path("additionalProperties").isBoolean() || identity.path("additionalProperties").booleanValue()
                    || !identityIds.add(id)) throw new IllegalArgumentException("definition identity schema does not match assigned actions");
        }
        if (!definitionIds.equals(expectedIds) || !identityIds.equals(expectedIds))
            throw new IllegalArgumentException("definition schema IDs do not match assigned actions");
    }

    private static boolean providerCompatibleSchema(JsonNode schema) {
        if (schema == null) return false;
        if (schema.path("enum").isArray() && schema.path("enum").isEmpty()) return false;
        if ("object".equals(schema.path("type").asText())) {
            java.util.Set<String> properties = fieldNames(schema.path("properties"));
            if (!schema.path("properties").isObject() || !schema.path("required").isArray()
                    || !properties.equals(textSet(schema.path("required")))
                    || !schema.path("additionalProperties").isBoolean() || schema.path("additionalProperties").booleanValue()) return false;
        }
        var children = schema.elements();
        while (children.hasNext()) if (!providerCompatibleSchema(children.next())) return false;
        return true;
    }

    private static boolean validRulebookActionFieldValues(java.util.Map<String, Object> fields) {
        for (var entry : fields.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (!RULEBOOK_ACTION_FIELD_IDS.contains(key)) return false;
            if (value == null) continue;
            if (java.util.Set.of("normalRangeFeet", "longRangeFeet", "lengthFeet", "widthFeet", "heightFeet", "radiusFeet").contains(key)) {
                if (!(value instanceof Number number) || number.doubleValue() < 0 || number.doubleValue() != Math.rint(number.doubleValue())) return false;
                continue;
            }
            if (!(value instanceof String text)) return false;
            java.util.Map<String, java.util.Set<String>> enums = java.util.Map.of(
                    "rangeForm", java.util.Set.of("SINGLE", "NORMAL_AND_LONG", "UNSUPPORTED"),
                    "targetMode", java.util.Set.of("SINGLE_TARGET", "MULTIPLE_TARGETS", "POINT", "DIRECTION", "SELF", "UNSUPPORTED"),
                    "originMode", java.util.Set.of("ACTOR_TOKEN", "TARGET", "CURSOR_POINT", "CURSOR_DIRECTION", "UNSUPPORTED"),
                    "effectShape", java.util.Set.of("NONE", "CONE", "LINE", "CUBE", "SPHERE", "CYLINDER", "WALL", "UNSUPPORTED"),
                    "pathBlocking", java.util.Set.of("SOLID_BLOCKS", "CLEAR_PATH_REQUIRED", "NONE", "UNSUPPORTED"),
                    "propagation", java.util.Set.of("DIRECT", "AREA_FROM_POINT", "CONTIGUOUS", "NONE", "UNSUPPORTED"),
                    "verticalDirection", java.util.Set.of("UP", "DOWN", "UNSUPPORTED"));
            if (enums.containsKey(key) && !enums.get(key).contains(text)) return false;
        }
        return true;
    }

    private static String singletonEnumValue(JsonNode values) {
        return values != null && values.isArray() && values.size() == 1 && values.get(0).isTextual()
                ? values.get(0).asText() : "";
    }

    private static java.util.Set<String> fieldNames(JsonNode object) {
        java.util.Set<String> names = new java.util.HashSet<>();
        if (object != null && object.isObject()) object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> nodeTextValues(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        if (array != null && array.isArray()) array.forEach(item -> { if (item.path(field).isTextual()) values.add(item.path(field).asText()); });
        return List.copyOf(values);
    }

    private static java.util.Map<String, Long> assignedStatusCounts(JsonNode results) {
        java.util.Map<String, Long> counts = new java.util.TreeMap<>();
        if (results != null && results.isArray()) results.forEach(item -> counts.merge(item.path("status").asText("<missing>"), 1L, Long::sum));
        return counts;
    }

    private void validateAssignedResponse(RulebookActionExtractionInput input, JsonNode response) {
        java.util.Map<String, RulebookActionAssignedActionInput> expected = input.assignedActions().stream()
                .collect(java.util.stream.Collectors.toMap(RulebookActionAssignedActionInput::id, item -> item));
        java.util.Map<String, JsonNode> results = new java.util.HashMap<>();
        JsonNode candidateResults = response.path("candidateResults");
        for (JsonNode result : candidateResults) {
            String id = result.path("candidateId").asText("");
            if (!expected.containsKey(id) || results.putIfAbsent(id, result) != null)
                throw new IllegalArgumentException("assigned response has a duplicate or unknown candidate ID");
        }
        if (!results.keySet().equals(expected.keySet())) throw new IllegalArgumentException("assigned response omitted an assigned candidate ID");
        java.util.Map<String, JsonNode> definitions = new java.util.HashMap<>();
        for (JsonNode definition : response.path("definitions")) {
            String id = definition.path("id").asText("");
            if (!expected.containsKey(id) || definitions.putIfAbsent(id, definition) != null)
                throw new IllegalArgumentException("assigned definitions have a duplicate or unknown candidate ID");
            RulebookActionAssignedActionInput action = expected.get(id);
            if (!action.name().equals(definition.path("name").asText()) || !action.actionKind().equals(definition.path("actionKind").asText()))
                throw new IllegalArgumentException("assigned definition identity does not match its candidate");
            String anchor = definition.path("sourceAnchorId").asText("");
            if (input.evidence().stream().filter(source -> action.evidenceIds().contains(source.id()))
                    .flatMap(source -> source.anchors().stream()).noneMatch(source -> source.id().equals(anchor)))
                throw new IllegalArgumentException("assigned definition cites evidence outside its candidate scope");
        }
        for (String id : expected.keySet()) {
            String status = results.get(id).path("status").asText("");
            boolean hasDefinition = definitions.containsKey(id);
            if ("COMPLETED".equals(status) != hasDefinition
                    || (!"COMPLETED".equals(status) && results.get(id).path("reason").asText("").isBlank()))
                throw new IllegalArgumentException("assigned result status and definition do not agree");
        }
    }

    private static boolean containsText(JsonNode array, String expected) {
        for (JsonNode value : array) if (expected.equals(value.asText())) return true;
        return false;
    }

    private static void requireExactFields(JsonNode node, java.util.Set<String> expected, String context) {
        java.util.Set<String> actual = new java.util.HashSet<>();
        if (node != null && node.isObject()) node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw new IllegalArgumentException(context + " does not match its JSON schema");
    }

    private static List<String> nodeFieldNames(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isObject()) node.fieldNames().forEachRemaining(values::add);
        return List.copyOf(values);
    }

    private static List<String> sourceAnchorIds(JsonNode definitions) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : definitions) ids.add(item.path("sourceAnchorId").asText(""));
        return List.copyOf(ids);
    }

    static String safeScenarioCompilationFailureReason(IllegalArgumentException failure) {
        if (failure instanceof ScenarioCompilationShapeException shapeFailure) return shapeFailure.reason();
        String message = failure.getMessage();
        if ("status is required".equals(message)
                || "invalid scenario compilation status".equals(message)
                || "READY scenario compilation requires a scenario model".equals(message)
                || "typed agent response must be an object".equals(message)
                || "invalid typed agent response".equals(message)) {
            return message;
        }
        return "unclassified invalid response";
    }

    private static final class ScenarioCompilationShapeException extends IllegalArgumentException {
        private final String path;
        private final String expected;
        private final String actual;

        private ScenarioCompilationShapeException(String path, String expected, String actual) {
            super("scenario compilation diagnostics must be an array");
            this.path = path;
            this.expected = expected;
            this.actual = actual;
        }

        private String code() { return "DIAGNOSTICS_SHAPE_INVALID"; }
        private String path() { return path; }
        private String expected() { return expected; }
        private String actual() { return actual; }
        private String reason() { return "scenario compilation diagnostics must be an array"; }
    }

    record ScenarioCompilationAgentError(String code, String correlationId, String rootCauseClass, boolean retryable) {}

    @PostMapping("/internal/gm/scenario-lookup")
    ScenarioLookupResponse scenarioLookup(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody JsonNode body) {
        requestGuard.internal(token);
        java.util.Set<String> fields = new java.util.HashSet<>();
        if (body != null && body.isObject()) body.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(java.util.Set.of("soloPlayerId", "query", "currentSituation", "currentScene",
                "establishedGameState", "runtimeAddedFacts", "evidence", "lockedScenarioModel",
                "preparedSituation", "longTermFacts", "knownEvidenceIds"))) {
            throw new IllegalArgumentException("scenario lookup request does not match its JSON schema");
        }
        ScenarioLookupRequest request;
        try {
            request = mapper.treeToValue(body, ScenarioLookupRequest.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalArgumentException("scenario lookup request does not match its JSON schema", invalid);
        }
        return scenarioLookup(token, request);
    }

    ScenarioLookupResponse scenarioLookup(String token, ScenarioLookupRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete(request.soloPlayerId(), "action-context-lookup:" + request.query(),
                "ROLE=ISOLATED_ACTION_CONTEXT_LOOKUP\n"
                        + "TASK=Return only a concise fact relevant to resolving PLAYER_ACTION, grounded in confirmed state, the locked scenario model, or retrieved evidence. Do not use dialogue history, narrate, or decide an outcome.\n"
                        + "OUTPUT_CONTRACT=Return one JSON object: status is FOUND or NOT_FOUND; answer is concise or empty for NOT_FOUND; supportingElementIds only names locked scenario elements and is empty for NOT_FOUND. No markdown.\n"
                        + "OUTPUT_SCHEMA=" + ACTION_LOOKUP_OUTPUT_SCHEMA + "\n"
                        + "PLAYER_ACTION=" + request.query()
                        + "\nPREPARED_SITUATION=" + write(request.preparedSituation())
                        + "\nCURRENT_SCENE=" + write(request.currentScene())
                        + "\nCURRENT_SITUATION=" + write(request.currentSituation())
                        + "\nCONFIRMED_GAME_STATE=" + write(request.establishedGameState())
                        + "\nCONFIRMED_FACTS_FROM_PLAY=" + write(request.runtimeAddedFacts())
                        + "\nLONG_TERM_ADVENTURE_FACTS=" + write(request.longTermFacts())
                        + "\nALREADY_KNOWN_IMMUTABLE_EVIDENCE_IDS=" + write(request.knownEvidenceIds())
                        + "\nLOCKED_SCENARIO_MODEL=" + write(request.lockedScenarioModel())
                        + "\nRETRIEVED_STORYBOOK_AND_RULEBOOK_EVIDENCE=" + write(request.evidence()),
                this::parseLookup);
    }

    @PostMapping("/internal/gm/runtime-endpoint")
    RuntimeEndpointResponse runtimeEndpoint(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody RuntimeEndpointRequest request) {
        requestGuard.internal(token);
        require(request);
        var resolution = selectionResolver.apply(new RequestedGmProviderSelection(
                request.endpointId(), request.provider(), request.model(), request.reasoning()));
        var effective = resolution.effectiveSelection();
        return new RuntimeEndpointResponse(effective.endpointId(), effective.endpointVersion().toString(),
                effective.provider(), effective.model(), effective.reasoning());
    }

    @PostMapping("/internal/gm/runtime-turn")
    RuntimeTurnResponse runtimeTurn(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody JsonNode body) {
        requestGuard.internal(token);
        java.util.Set<String> fields = new java.util.HashSet<>();
        if (body != null && body.isObject()) body.fieldNames().forEachRemaining(fields::add);
        java.util.Set<String> allowed = java.util.Set.of("soloPlayerId", "operationKey", "action", "endpointId", "provider",
                "model", "reasoning", "effectiveEndpointId", "effectiveEndpointVersion", "effectiveProvider",
                "effectiveModel", "prompt", "ragSearchContext", "allowedEvidenceKeys");
        if (!allowed.containsAll(fields) || !fields.containsAll(allowed.stream()
                .filter(field -> !field.equals("ragSearchContext") && !field.equals("allowedEvidenceKeys")).toList())) {
            throw new IllegalArgumentException("runtime turn request does not match its JSON schema");
        }
        RuntimeTurnRequest request;
        try {
            request = mapper.treeToValue(body, RuntimeTurnRequest.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalArgumentException("runtime turn request does not match its JSON schema", invalid);
        }
        return runtimeTurn(token, request);
    }

    RuntimeTurnResponse runtimeTurn(String token, RuntimeTurnRequest request) {
        requestGuard.internal(token);
        require(request);
        RequestedGmProviderSelection requested = new RequestedGmProviderSelection(request.endpointId(), request.provider(), request.model(), request.reasoning());
        GmProviderSelectionResolver.EndpointResolution resolution = selectionResolver.apply(requested);
        EffectiveGmProviderSelection effective = resolution.effectiveSelection();
        if (!effective.endpointId().equals(request.effectiveEndpointId())
                || !effective.endpointVersion().toString().equals(request.effectiveEndpointVersion())
                || !effective.provider().equals(request.effectiveProvider())
                || !effective.model().equals(request.effectiveModel())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "selected GM endpoint changed before execution");
        }
        long startedAt = System.nanoTime();
        JsonNode runtimeSchema = runtimeTurnOutputSchema(request.allowedEvidenceKeys());
        if (runtimeDiagnosticsEnabled()) log.info("dev_gm_runtime_schema outcome=selected operationKey={} schemaId={} schemaChars={} schemaFingerprint={} model={} reasoning={} ragContextPresent={} allowedEvidenceKeyCount={} allowedEvidenceKeys={} evidenceConstraintApplied={} checkBranch={}",
                request.operationKey(), runtimeSchema.path("$id").asText(), runtimeSchema.toString().length(),
                fingerprint(runtimeSchema.toString()), request.model(), request.reasoning(), request.ragSearchContext() != null,
                request.allowedEvidenceKeys().size(), request.allowedEvidenceKeys(), !request.allowedEvidenceKeys().isEmpty(),
                request.allowedEvidenceKeys().isEmpty() ? "base_schema_no_supplied_evidence" : "supplied_evidence_keys_only");
        try {
            RuntimeTurnResponse response = adapter.completeWithResolution(request.soloPlayerId(), request.operationKey(), request.prompt(),
                    json -> {
                        try {
                            return parseRuntimeTurn(json);
                        } catch (IllegalArgumentException invalid) {
                            RuntimeResponseShape shape = runtimeResponseShape(json);
                            if (runtimeDiagnosticsEnabled()) log.warn("dev_gm_runtime_schema outcome=rejected operationKey={} expectedFields={} actualFields={} evidencePresent={} evidenceCount={} blankEvidenceCount={} parseFailureClass={} parseLine={} parseColumn={} expectedEvidenceKeys={} actualEvidenceKeys={} reason={} recoveryAction=return_contract_error",
                                    request.operationKey(), textValues(RUNTIME_TURN_OUTPUT_SCHEMA.path("required")),
                                    shape.fields(), shape.evidencePresent(), shape.evidenceCount(), shape.blankEvidenceCount(),
                                    shape.parseFailureClass(), shape.parseLine(), shape.parseColumn(),
                                    request.allowedEvidenceKeys(), runtimeEvidenceKeys(json), invalid.getMessage());
                            throw invalid;
                        }
                    },
                    requested, resolution, request.ragSearchContext(), runtimeSchema).response();
            if (runtimeDiagnosticsEnabled()) log.info("dev_gm_runtime_schema outcome=completed operationKey={} elapsedMs={} checkRequired={} evidenceCount={}",
                    request.operationKey(), java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                    response.checkProposal().required(), response.checkProposal().evidenceKeys().size());
            return response;
        } catch (RuntimeException failure) {
            if (runtimeDiagnosticsEnabled()) log.warn("dev_gm_runtime_schema outcome=failed operationKey={} elapsedMs={} failureClass={} recoveryAction=propagate",
                    request.operationKey(), java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), failure.getClass().getSimpleName());
            throw failure;
        }
    }

    private static JsonNode loadRuntimeTurnOutputSchema() {
        try (var input = TypedAgentContractController.class.getResourceAsStream("/ai-game-master/schemas/runtime-turn-response.json")) {
            if (input == null) throw new IllegalStateException("runtime turn response schema is missing");
            return new ObjectMapper().readTree(input);
        } catch (java.io.IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static JsonNode runtimeTurnOutputSchema(List<String> allowedEvidenceKeys) {
        var schema = RUNTIME_TURN_OUTPUT_SCHEMA.deepCopy();
        var proposal = (com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties").path("판정제안");
        var branches = (com.fasterxml.jackson.databind.node.ArrayNode) proposal.path("anyOf");
        if (!allowedEvidenceKeys.isEmpty()) {
            var evidenceItems = (com.fasterxml.jackson.databind.node.ObjectNode) branches.get(1).path("properties")
                    .path("근거키").path("items");
            var allowed = evidenceItems.putArray("enum");
            allowedEvidenceKeys.stream().distinct().forEach(allowed::add);
        }
        return schema;
    }

    private List<String> runtimeEvidenceKeys(String json) {
        try {
            JsonNode evidence = mapper.readTree(json).path("판정제안").path("근거키");
            if (!evidence.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            evidence.forEach(value -> {
                String key = value.isTextual() ? value.asText() : "";
                result.add(key.length() <= 512 && key.matches("[A-Za-z0-9._:=/-]+") ? key : "<non-identifier>");
            });
            return List.copyOf(result);
        } catch (java.io.IOException ignored) {
            return List.of();
        }
    }

    private static boolean runtimeDiagnosticsEnabled() {
        return "true".equalsIgnoreCase(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")));
    }

    private static String fingerprint(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 0, 8); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static List<String> textValues(JsonNode values) {
        if (values == null || !values.isArray()) return List.of();
        List<String> result = new ArrayList<>(); values.forEach(value -> result.add(value.asText())); return List.copyOf(result);
    }

    private static RuntimeResponseShape runtimeResponseShape(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            if (root == null || !root.isObject()) return new RuntimeResponseShape(List.of("<non-object>"), false, 0, 0, "none", 0, 0);
            List<String> fields = new ArrayList<>(); root.fieldNames().forEachRemaining(fields::add);
            JsonNode evidence = root.path("판정제안").path("근거키");
            int count = evidence.isArray() ? evidence.size() : 0;
            int blankCount = 0;
            if (evidence.isArray()) for (JsonNode key : evidence) if (!key.isTextual() || key.asText().isBlank()) blankCount++;
            return new RuntimeResponseShape(List.copyOf(fields), !evidence.isMissingNode(), count, blankCount, "none", 0, 0);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalidJson) {
            var location = invalidJson.getLocation();
            return new RuntimeResponseShape(List.of("<unavailable>"), false, 0, 0,
                    invalidJson.getClass().getSimpleName(), location == null ? 0 : location.getLineNr(),
                    location == null ? 0 : location.getColumnNr());
        } catch (Exception ignored) {
            return new RuntimeResponseShape(List.of("<unavailable>"), false, 0, 0,
                    ignored.getClass().getSimpleName(), 0, 0);
        }
    }

    private record RuntimeResponseShape(List<String> fields, boolean evidencePresent, int evidenceCount,
            int blankEvidenceCount, String parseFailureClass, int parseLine, int parseColumn) {}

    @PostMapping("/internal/gm/combat-turn-decision")
    CombatTurnDecisionResponse combatTurnDecision(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody CombatTurnDecisionRequest request) {
        requestGuard.internal(token);
        require(request);
        RequestedGmProviderSelection requested = new RequestedGmProviderSelection(
                request.endpointId(), request.provider(), request.model(), request.reasoning());
        GmProviderSelectionResolver.EndpointResolution resolution = selectionResolver.apply(requested);
        EffectiveGmProviderSelection effective = resolution.effectiveSelection();
        if (!effective.endpointId().equals(request.effectiveEndpointId())
                || !effective.endpointVersion().toString().equals(request.effectiveEndpointVersion())
                || !effective.provider().equals(request.effectiveProvider())
                || !effective.model().equals(request.effectiveModel())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "selected GM endpoint changed before execution");
        }
        return adapter.completeWithResolution(request.soloPlayerId(), request.operationKey(), request.prompt(),
                json -> {
                    try {
                        return parseCombatTurnDecision(json);
                    } catch (IllegalArgumentException invalid) {
                        // The message comes only from our fixed contract validators; never log model output.
                        log.warn("gm_combat_turn_response_rejected operationKey={} reason={}",
                                request.operationKey(), invalid.getMessage());
                        throw invalid;
                    }
                }, requested, resolution, null).response();
    }

    private CombatTurnDecisionResponse parseCombatTurnDecision(String json) {
        JsonNode root = readObject(json);
        String kind = required(root, "kind").toUpperCase(java.util.Locale.ROOT);
        if (!kind.equals("ACTION") && !kind.equals("END_TURN")) throw new IllegalArgumentException("combat decision kind is invalid");
        java.util.UUID actorId;
        try { actorId = java.util.UUID.fromString(required(root, "actorId")); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("combat actorId must be a UUID", invalid); }
        String action = kind.equals("ACTION") ? required(root, "action") : null;
        java.util.UUID targetId = null;
        if (root.hasNonNull("targetId") && !root.path("targetId").asText().isBlank()) {
            try { targetId = java.util.UUID.fromString(root.path("targetId").asText()); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("combat targetId must be a UUID", invalid); }
        }
        List<String> citations = citationKeys(root.path("citationKeys"), "combat decision");
        String assessment = kind.equals("END_TURN") ? required(root, "endTurnAssessment") : null;
        if (kind.equals("END_TURN") && citations.isEmpty()) {
            throw new IllegalArgumentException("ending a combat turn requires source citations");
        }
        return new CombatTurnDecisionResponse(actorId, kind, action, targetId, citations, assessment);
    }

    @PostMapping("/internal/gm/narration-safety")
    NarrationSafetyResponse narrationSafety(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody NarrationSafetyRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete(request.soloPlayerId(), "narration-safety", "ROLE=NARRATION_SAFETY\nNARRATION=" + request.narration()
                        + "\nDISCLOSED_FACT_IDS=" + write(request.disclosedFactIds()), this::parseSafety);
    }

    @PostMapping("/internal/gm/conversation-compaction")
    ConversationCompactionResponse conversationCompaction(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ConversationCompactionRequest request) {
        requestGuard.internal(token);
        require(request);
        String conversation = write(request.conversation());
        return adapter.complete(request.soloPlayerId(), "conversation-compaction:" + request.sourceStart() + ":" + request.sourceEnd(),
                "ROLE=CONVERSATION_COMPACTION\nSOURCE_START=" + request.sourceStart()
                        + "\nSOURCE_END=" + request.sourceEnd() + "\nEXPECTED_ADVENTURE_VERSION=" + request.expectedAdventureVersion()
                        + "\nCONFIRMED_CONVERSATION=" + conversation
                        + "\nCONFIRMED_RUNTIME_FACTS=" + write(request.runtimeFacts())
                        + "\nTASK=Write one concise Korean summary across the entire confirmed conversation range, preserving its meaning, including character speech, commitments, scene flow, established consequences, unresolved choices, and current goals. You may rewrite and compress the wording. Do not add, infer, or alter facts; do not treat HP, resources, location, or combat state as authoritative."
                        + "\nSOURCE_REFERENCE_RULE=Keep sourceStart, sourceEnd, and expectedAdventureVersion unchanged. The server binds this summary to the exact confirmed source range and version. Do not emit per-entry sequence, speaker, or quotation fields. Keep summary at most 80 percent of source content length."
                        + "\nLONG_TERM_FACT_RULE=longTermFacts is optional. Include only a confirmed Runtime Fact from CONFIRMED_RUNTIME_FACTS that represents an established EVENT, RELATIONSHIP, GOAL, or THREAT with ongoing relevance. Each item must contain factId, establishedTurnId, kind, relevance, and playerVisible. Never create a record for simple dialogue or copy character sheets, HP, resources, location, or combat state."
                        + "\nOUTPUT_CONTRACT=Return exactly one JSON object with sourceStart, sourceEnd, expectedAdventureVersion, summary, and optional longTermFacts [{factId,establishedTurnId,kind,relevance,playerVisible}]. summary must be generated prose grounded only in the supplied confirmed range. Do not use markdown.",
                json -> parseConversationCompaction(json, request));
    }

    private ScenarioCompilationResponse parseCompilation(String json) {
        JsonNode root = readObject(json);
        String status = required(root, "status").toUpperCase(java.util.Locale.ROOT);
        if (!status.equals("READY") && !status.equals("BLOCKED")) {
            throw new IllegalArgumentException("invalid scenario compilation status");
        }
        Map<String, Object> model = root.path("scenarioModel").isObject()
                ? mapper.convertValue(root.path("scenarioModel"), new TypeReference<Map<String, Object>>() { })
                : Map.of();
        if (status.equals("READY") && model.isEmpty()) {
            throw new IllegalArgumentException("READY scenario compilation requires a scenario model");
        }
        JsonNode diagnosticsNode = root.get("diagnostics");
        if (diagnosticsNode != null && !diagnosticsNode.isNull() && !diagnosticsNode.isArray()) {
            throw new ScenarioCompilationShapeException("diagnostics", "array", jsonShape(diagnosticsNode));
        }
        List<String> diagnostics = diagnosticsNode != null && !diagnosticsNode.isNull()
                ? mapper.convertValue(diagnosticsNode, mapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class)) : List.of();
        return new ScenarioCompilationResponse(status,
                model, diagnostics);
    }

    private static String jsonShape(JsonNode node) {
        if (node.isObject()) return "object";
        if (node.isArray()) return "array(size=" + node.size() + ")";
        if (node.isTextual()) return "string";
        if (node.isIntegralNumber()) return "integer";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        return node.isNull() ? "null" : "other";
    }

    private ScenarioLookupResponse parseLookup(String json) {
        JsonNode root = readObject(json);
        java.util.Set<String> fields = new java.util.HashSet<>();
        root.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(java.util.Set.of("status", "answer", "supportingElementIds"))) {
            throw new IllegalArgumentException("lookup response must contain exactly status, answer, and supportingElementIds");
        }
        JsonNode answerNode = root.path("answer");
        JsonNode idsNode = root.path("supportingElementIds");
        if (!answerNode.isTextual() || !idsNode.isArray()
                || java.util.stream.StreamSupport.stream(idsNode.spliterator(), false).anyMatch(id -> !id.isTextual() || id.asText().isBlank())) {
            throw new IllegalArgumentException("lookup answer and supportingElementIds have invalid types");
        }
        List<String> ids = mapper.convertValue(idsNode, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        if (ids.size() != new java.util.HashSet<>(ids).size()) throw new IllegalArgumentException("lookup supportingElementIds must be unique");
        String status = required(root, "status").toUpperCase(java.util.Locale.ROOT);
        if (!status.equals("FOUND") && !status.equals("NOT_FOUND")) throw new IllegalArgumentException("invalid lookup status");
        String answer = answerNode.asText();
        if ((status.equals("NOT_FOUND") && (!answer.isEmpty() || !ids.isEmpty()))
                || (status.equals("FOUND") && answer.isBlank())) {
            throw new IllegalArgumentException("lookup response fields do not match status");
        }
        return new ScenarioLookupResponse(status, answer, ids);
    }

    private RuntimeTurnResponse parseRuntimeTurn(String json) {
        JsonNode root = readObject(json);
        requireOnlyFields(root, java.util.Set.of("scene", "judgment", "narration", "combatStart", "combatEnemies",
                "situation", "mapEntryRequested", "runtimeFacts", "completion", "citedEvidence", "판정제안"),
                java.util.Set.of("scene", "judgment", "narration", "combatStart", "combatEnemies", "situation",
                        "mapEntryRequested", "판정제안"), "runtime turn response");
        String scene = required(root, "scene");
        String judgment = required(root, "judgment");
        String narration = required(root, "narration");
        requireKoreanPlayerText("narration", narration);
        if (!root.has("combatStart") || !root.path("combatStart").isBoolean()) {
            throw new IllegalArgumentException("combatStart is required and must be boolean");
        }
        if (!root.has("mapEntryRequested") || !root.path("mapEntryRequested").isBoolean()) {
            throw new IllegalArgumentException("mapEntryRequested is required and must be boolean");
        }
        JsonNode checkNode = root.path("판정제안");
        requireOnlyFields(checkNode, java.util.Set.of("필요", "이유", "판정능력또는기술", "대상캐릭터ID", "굴림주체",
                "굴림식", "보정치", "난이도", "근거키", "성공시결과", "실패시결과"), java.util.Set.of("필요"), "check proposal");
        if (!checkNode.isObject() || !checkNode.path("필요").isBoolean()) {
            throw new IllegalArgumentException("판정제안 with a boolean 필요 field is required");
        }
        CheckProposalResponse checkProposal;
        if (!checkNode.path("필요").booleanValue()) {
            checkProposal = CheckProposalResponse.none();
        } else {
            String rollMethod = required(checkNode, "굴림주체");
            if (!rollMethod.equals("플레이어") && !rollMethod.equals("시스템")) {
                throw new IllegalArgumentException("판정제안 굴림주체 must be 플레이어 or 시스템");
            }
            java.util.UUID characterSheetId;
            try { characterSheetId = java.util.UUID.fromString(required(checkNode, "대상캐릭터ID")); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("판정제안 대상캐릭터ID must be a UUID", invalid); }
            int difficulty = checkNode.path("난이도").asInt(-1);
            int modifier = checkNode.path("보정치").asInt(Integer.MIN_VALUE);
            if (difficulty < 1 || difficulty > 40) throw new IllegalArgumentException("판정제안 난이도 is outside 1..40");
            if (modifier < -10 || modifier > 20) throw new IllegalArgumentException("판정제안 보정치 is outside -10..20");
            List<String> evidenceKeys;
            try { evidenceKeys = mapper.convertValue(checkNode.path("근거키"), mapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class)); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("판정제안 근거키 must be an array", invalid); }
            if (evidenceKeys == null || evidenceKeys.isEmpty() || evidenceKeys.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("판정제안 requires nonblank 근거키");
            }
            checkProposal = new CheckProposalResponse(true, required(checkNode, "이유"),
                    required(checkNode, "판정능력또는기술"), characterSheetId,
                    rollMethod,
                    required(checkNode, "굴림식"), modifier, difficulty, List.copyOf(evidenceKeys),
                    required(checkNode, "성공시결과"), required(checkNode, "실패시결과"));
        }
        JsonNode enemiesNode = root.path("combatEnemies");
        if (!enemiesNode.isArray()) throw new IllegalArgumentException("combatEnemies is required and must be an array");
        List<CombatEnemyResponse> enemies = new java.util.ArrayList<>();
        for (JsonNode enemy : enemiesNode) {
            if (!enemy.isObject()) throw new IllegalArgumentException("combatEnemies entries must be objects");
            requireOnlyFields(enemy, java.util.Set.of("mode", "scenarioId", "enemyKey", "name", "count", "abilities", "actions"),
                    java.util.Set.of("enemyKey", "name", "count"), "combat enemy");
            int count = enemy.path("count").asInt(0);
            if (count < 1) throw new IllegalArgumentException("combatEnemies count must be positive");
            String mode = enemy.path("mode").asText("SCENARIO").toUpperCase(java.util.Locale.ROOT);
            if (!mode.equals("SCENARIO") && !mode.equals("SITUATION") && !mode.equals("INSTANT")) {
                throw new IllegalArgumentException("invalid combat enemy mode");
            }
            String scenarioId = enemy.path("scenarioId").asText("").trim();
            if (mode.equals("SCENARIO") && scenarioId.isBlank()) {
                throw new IllegalArgumentException("scenarioId is required for a SCENARIO combat enemy");
            }
            String name = required(enemy, "name");
            List<CombatEnemyAbilityResponse> abilities = new java.util.ArrayList<>();
            JsonNode abilityArray = enemy.path("abilities");
            if (abilityArray.isArray()) for (JsonNode ability : abilityArray) {
                requireOnlyFields(ability, java.util.Set.of("ability", "score", "citationKeys"),
                        java.util.Set.of("ability", "score", "citationKeys"), "combat enemy ability");
                int score = ability.path("score").asInt(-1);
                if (score < 1 || score > 30) throw new IllegalArgumentException("combat enemy ability score must be within 1..30");
                abilities.add(new CombatEnemyAbilityResponse(required(ability, "ability"), score,
                        citationKeys(ability.path("citationKeys"), "combat enemy ability")));
            }
            List<CombatEnemyActionResponse> actions = new java.util.ArrayList<>();
            JsonNode actionArray = enemy.path("actions");
            if (actionArray.isArray()) for (JsonNode action : actionArray) {
                requireOnlyFields(action, java.util.Set.of("name", "description", "citationKeys"),
                        java.util.Set.of("name", "description", "citationKeys"), "combat enemy action");
                actions.add(new CombatEnemyActionResponse(required(action, "name"), required(action, "description"),
                        citationKeys(action.path("citationKeys"), "combat enemy action")));
            }
            enemies.add(new CombatEnemyResponse(mode, scenarioId, required(enemy, "enemyKey"), name, count,
                    List.copyOf(abilities), List.copyOf(actions)));
        }
        boolean combatStart = root.path("combatStart").booleanValue();
        if (combatStart && enemies.isEmpty()) {
            throw new IllegalArgumentException("combatStart requires at least one structured combat enemy");
        }
        JsonNode situation = root.path("situation");
        requireOnlyFields(situation, java.util.Set.of("kind", "location", "problem", "threat", "goal", "basis", "reference", "required"),
                java.util.Set.of("kind", "basis", "required"), "situation");
        if (!situation.isObject()) throw new IllegalArgumentException("situation is required and must be an object");
        String basis = required(situation, "basis").toUpperCase(java.util.Locale.ROOT);
        if (!basis.equals("SCENARIO") && !basis.equals("RAG") && !basis.equals("FALLBACK")) {
            throw new IllegalArgumentException("invalid situation basis");
        }
        if (!situation.has("required") || !situation.path("required").isBoolean()) {
            throw new IllegalArgumentException("situation required is mandatory");
        }
        String kind = required(situation, "kind").toUpperCase(java.util.Locale.ROOT);
        if (!kind.equals("CONTINUE") && !kind.equals("TRANSITION")) throw new IllegalArgumentException("invalid situation kind");
        String location = optional(situation, "location");
        String problem = optional(situation, "problem");
        String threat = optional(situation, "threat");
        String goal = optional(situation, "goal");
        SituationResponse response = new SituationResponse(kind, location, problem, threat, goal, basis, situation.path("reference").asText(""),
                situation.path("required").booleanValue());
        List<RuntimeFactResponse> runtimeFacts = new java.util.ArrayList<>();
        JsonNode runtimeFactsNode = root.path("runtimeFacts");
        if (!runtimeFactsNode.isMissingNode() && !runtimeFactsNode.isNull()) {
            if (!runtimeFactsNode.isArray()) throw new IllegalArgumentException("runtimeFacts must be an array when provided");
            for (JsonNode fact : runtimeFactsNode) {
                if (!fact.isObject()) throw new IllegalArgumentException("runtimeFacts entries must be objects");
                requireOnlyFields(fact, java.util.Set.of("subject", "content"), java.util.Set.of("subject", "content"), "runtime fact");
                String subject = required(fact, "subject");
                String content = required(fact, "content");
                runtimeFacts.add(new RuntimeFactResponse(subject, content));
            }
        }
        CompletionResponse completion = CompletionResponse.continueAdventure();
        JsonNode completionNode = root.path("completion");
        if (!completionNode.isMissingNode() && !completionNode.isNull()) {
            requireOnlyFields(completionNode, java.util.Set.of("complete", "resolvedObjectiveIds",
                    "satisfiedResolutionCriteriaIds", "concludingScene"), java.util.Set.of("complete"), "completion");
            if (!completionNode.isObject() || !completionNode.path("complete").isBoolean()) {
                throw new IllegalArgumentException("completion must be an object with a boolean complete field");
            }
            List<String> resolvedObjectives = completionNode.hasNonNull("resolvedObjectiveIds")
                    ? mapper.convertValue(completionNode.path("resolvedObjectiveIds"), mapper.getTypeFactory()
                            .constructCollectionType(List.class, String.class)) : List.of();
            List<String> satisfiedConditions = completionNode.hasNonNull("satisfiedResolutionCriteriaIds")
                    ? mapper.convertValue(completionNode.path("satisfiedResolutionCriteriaIds"), mapper.getTypeFactory()
                            .constructCollectionType(List.class, String.class)) : List.of();
            if (resolvedObjectives.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("resolvedObjectiveIds must not contain blank values");
            }
            if (satisfiedConditions.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("satisfiedResolutionCriteriaIds must not contain blank values");
            }
            boolean complete = completionNode.path("complete").booleanValue();
            String concludingScene = completionNode.path("concludingScene").asText("").trim();
            if (complete && concludingScene.isBlank()) {
                throw new IllegalArgumentException("a completed adventure requires a concludingScene");
            }
            if (!complete && !concludingScene.isBlank()) {
                throw new IllegalArgumentException("an incomplete adventure cannot propose a concludingScene");
            }
            completion = new CompletionResponse(complete, List.copyOf(resolvedObjectives),
                    List.copyOf(satisfiedConditions), concludingScene);
        }
        List<String> citedEvidence = new java.util.ArrayList<>();
        JsonNode citedEvidenceNode = root.path("citedEvidence");
        if (!citedEvidenceNode.isMissingNode() && !citedEvidenceNode.isNull()) {
            if (!citedEvidenceNode.isArray()) throw new IllegalArgumentException("citedEvidence must be an array when provided");
            for (JsonNode citation : citedEvidenceNode) {
                if (!citation.isTextual() || citation.asText().isBlank()) {
                    throw new IllegalArgumentException("citedEvidence entries must be non-blank evidence keys");
                }
                citedEvidence.add(citation.asText().trim());
            }
            if (citedEvidence.size() != citedEvidence.stream().distinct().count()) {
                throw new IllegalArgumentException("citedEvidence entries must be unique");
            }
        }
        return new RuntimeTurnResponse(scene, judgment, narration,
                combatStart, List.copyOf(enemies), response, root.path("mapEntryRequested").booleanValue(),
                List.copyOf(runtimeFacts), completion, List.copyOf(citedEvidence), checkProposal);
    }

    private NarrationSafetyResponse parseSafety(String json) {
        JsonNode root = readObject(json);
        if (!root.has("approved") || !root.path("approved").isBoolean()) throw new IllegalArgumentException("approved is required");
        return new NarrationSafetyResponse(root.path("approved").booleanValue(), root.path("reason").asText(""));
    }

    private ConversationCompactionResponse parseConversationCompaction(String json, ConversationCompactionRequest request) {
        JsonNode root = readObject(json);
        long sourceStart = root.path("sourceStart").asLong(Long.MIN_VALUE);
        long sourceEnd = root.path("sourceEnd").asLong(Long.MIN_VALUE);
        long version = root.path("expectedAdventureVersion").asLong(Long.MIN_VALUE);
        if (sourceStart != request.sourceStart() || sourceEnd != request.sourceEnd() || version != request.expectedAdventureVersion()) {
            throw new IllegalArgumentException("conversation compaction response source does not match request");
        }
        String summary = required(root, "summary");
        long sourceLength = request.conversation().stream().mapToLong(entry -> entry.content().length()).sum();
        if (summary.length() * 5 > sourceLength * 4) throw new IllegalArgumentException("conversation summary must be meaningfully shorter");
        List<LongTermFactCandidate> longTermFacts = new ArrayList<>();
        JsonNode factNodes = root.path("longTermFacts");
        if (factNodes.isArray()) {
            for (JsonNode fact : factNodes) {
                try {
                    if (!fact.isObject()) continue;
                    java.util.UUID factId = java.util.UUID.fromString(required(fact, "factId"));
                    java.util.UUID establishedTurnId = java.util.UUID.fromString(required(fact, "establishedTurnId"));
                    String kind = required(fact, "kind").toUpperCase(java.util.Locale.ROOT);
                    if (!List.of("EVENT", "RELATIONSHIP", "GOAL", "THREAT").contains(kind)) continue;
                    if (!fact.has("playerVisible") || !fact.path("playerVisible").isBoolean()) continue;
                    boolean confirmed = request.runtimeFacts().stream().anyMatch(runtimeFact -> runtimeFact.factId().equals(factId)
                            && runtimeFact.establishedTurnId().equals(establishedTurnId));
                    if (!confirmed) continue;
                    longTermFacts.add(new LongTermFactCandidate(factId, establishedTurnId, kind,
                            required(fact, "relevance"), fact.path("playerVisible").booleanValue()));
                } catch (IllegalArgumentException malformedOptionalFact) {
                    // A proposed long-term record is optional; malformed proposals do not invalidate source excerpts.
                }
            }
        }
        return new ConversationCompactionResponse(sourceStart, sourceEnd, version, summary, List.copyOf(longTermFacts));
    }

    private JsonNode readObject(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("typed agent response must be an object");
            return node;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid typed agent response", e);
        }
    }

    private static void requireOnlyFields(JsonNode node, java.util.Set<String> allowed,
            java.util.Set<String> required, String subject) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException(subject + " must be an object");
        java.util.Set<String> actual = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!allowed.containsAll(actual) || !actual.containsAll(required)) {
            throw new IllegalArgumentException(subject + " does not match its JSON schema");
        }
    }

    private static List<String> citationKeys(JsonNode array, String subject) {
        if (!array.isArray() || array.isEmpty()) throw new IllegalArgumentException(subject + " requires citationKeys");
        List<String> keys = new java.util.ArrayList<>();
        for (JsonNode key : array) {
            if (!key.isTextual() || key.asText().isBlank()) throw new IllegalArgumentException(subject + " has invalid citationKeys");
            keys.add(key.asText().trim());
        }
        return List.copyOf(keys);
    }

    private static String required(JsonNode root, String field) {
        String value = root.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String optional(JsonNode root, String field) {
        if (!root.hasNonNull(field)) return null;
        String value = root.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank when provided");
        return value;
    }

    private void requireKoreanPlayerText(String field, String value) {
        boolean hasKoreanLetter = value.codePoints().anyMatch(TypedAgentContractController::isKoreanLetter);
        if (!hasKoreanLetter) {
            log.warn("gm_player_text_language_rejected field={} value={}", field, write(value));
            throw new IllegalArgumentException(field + " must be written in Korean");
        }
    }

    private static boolean isKoreanLetter(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x11FF)
                || (codePoint >= 0x3130 && codePoint <= 0x318F)
                || (codePoint >= 0xA960 && codePoint <= 0xA97F)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xD7B0 && codePoint <= 0xD7FF);
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("typed request serialization failed", e); }
    }

    private static List<String> stringList(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IllegalArgumentException("runtime GM text entries must be strings");
        }
        return list.stream().map(String.class::cast).toList();
    }

    private static void require(Object request) {
        if (request == null) throw new IllegalArgumentException("typed agent request is required");
    }

    public record ScenarioCompilationRequest(java.util.UUID soloPlayerId, String operationKey, String storybookContext) {
        public ScenarioCompilationRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            operationKey = required(operationKey, "operationKey");
            storybookContext = required(storybookContext, "storybookContext");
        }
    }

    public record InitialSituationRequest(java.util.UUID soloPlayerId, java.util.UUID endpointId,
            String provider, String model, String reasoning, String operationKey, String prompt, JsonNode outputSchema,
            boolean useSubagents, List<InitialSituationStageTask> stageTasks) {
        public InitialSituationRequest(java.util.UUID soloPlayerId, java.util.UUID endpointId,
                String provider, String model, String reasoning, String operationKey, String prompt, JsonNode outputSchema) {
            this(soloPlayerId, endpointId, provider, model, reasoning, operationKey, prompt, outputSchema, false, List.of());
        }
        public InitialSituationRequest(java.util.UUID soloPlayerId, java.util.UUID endpointId,
                String provider, String model, String reasoning, String operationKey, String prompt, JsonNode outputSchema,
                boolean useSubagents) {
            this(soloPlayerId, endpointId, provider, model, reasoning, operationKey, prompt, outputSchema, useSubagents, List.of());
        }
        public InitialSituationRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            provider = required(provider, "provider");
            model = required(model, "model");
            reasoning = required(reasoning, "reasoning");
            operationKey = required(operationKey, "operationKey");
            prompt = required(prompt, "prompt");
            stageTasks = stageTasks == null ? List.of() : List.copyOf(stageTasks);
            if (useSubagents && (stageTasks.isEmpty() || stageTasks.stream().map(InitialSituationStageTask::stageId).distinct().count() != stageTasks.size()))
                throw new IllegalArgumentException("delegated stage tasks must have unique stageIds");
            if (outputSchema == null || !outputSchema.isObject() || !"object".equals(outputSchema.path("type").asText())
                    || !outputSchema.path("properties").isObject() || !outputSchema.path("required").isArray()
                    || !outputSchema.path("additionalProperties").isBoolean()
                    || outputSchema.path("additionalProperties").booleanValue()) {
                throw new IllegalArgumentException("outputSchema must be a strict object schema");
            }
        }
    }

    public record RulebookActionExtractionRequest(java.util.UUID soloPlayerId, java.util.UUID endpointId,
            String provider, String model, String reasoning, String operationKey, JsonNode extractionInput, JsonNode outputSchema) {
        public RulebookActionExtractionRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            provider = required(provider, "provider"); model = required(model, "model"); reasoning = required(reasoning, "reasoning");
            operationKey = required(operationKey, "operationKey");
            if (extractionInput == null || outputSchema == null) throw new IllegalArgumentException("extractionInput and outputSchema are required");
        }
    }

    public record RulebookActionExtractionInput(java.util.UUID documentId, String extractionVersion,
            List<RulebookActionQueryInput> queryCoverage, List<RulebookEvidenceInput> evidence,
            List<RulebookActionAssignedActionInput> assignedActions) {
        public RulebookActionExtractionInput {
            documentId = Objects.requireNonNull(documentId, "documentId is required");
            extractionVersion = required(extractionVersion, "extractionVersion");
            queryCoverage = List.copyOf(Objects.requireNonNull(queryCoverage, "queryCoverage is required"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence is required"));
            assignedActions = assignedActions == null ? List.of() : List.copyOf(assignedActions);
            if (queryCoverage.size() > 9 || evidence.size() > 1024 || assignedActions.size() > 5
                    || queryCoverage.stream().map(RulebookActionQueryInput::queryId).distinct().count() != queryCoverage.size()
                    || evidence.stream().map(RulebookEvidenceInput::id).distinct().count() != evidence.size())
                throw new IllegalArgumentException("rulebook extraction input exceeds bounds or contains duplicate ids");
            if (queryCoverage.stream().anyMatch(query -> !isRulebookActionQueryId(query.queryId()))
                    || evidence.stream().flatMap(item -> item.anchors().stream()).mapToLong(item -> item.text().length()).sum() > 1_000_000)
                throw new IllegalArgumentException("rulebook extraction input contains an unknown query or exceeds the text limit");
        }

        private static boolean isRulebookActionQueryId(String queryId) {
            if (RULEBOOK_ACTION_QUERY_IDS.contains(queryId)) return true;
            if (queryId == null || !queryId.startsWith("action:")) return false;
            try {
                java.util.UUID.fromString(queryId.substring("action:".length()));
                return true;
            } catch (IllegalArgumentException invalidId) {
                return false;
            }
        }
    }

    public record RulebookActionAssignedActionInput(String id, String name, String actionKind,
            String sourceLocator, List<String> evidenceIds, java.util.Map<String, Object> knownFields, List<String> neededFields,
            List<RulebookActionSharedRuleInput> sharedRules) {
        public RulebookActionAssignedActionInput(String id, String name, String actionKind, String sourceLocator,
                List<String> evidenceIds, java.util.Map<String, Object> knownFields, List<String> neededFields) {
            this(id, name, actionKind, sourceLocator, evidenceIds, knownFields, neededFields, List.of());
        }
        public RulebookActionAssignedActionInput {
            id = required(id, "assigned action id"); name = required(name, "assigned action name");
            actionKind = required(actionKind, "assigned action kind"); sourceLocator = required(sourceLocator, "assigned source locator");
            evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "assigned evidence IDs are required"));
            knownFields = knownFields == null ? java.util.Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(knownFields));
            neededFields = neededFields == null ? List.of() : List.copyOf(neededFields);
            sharedRules = sharedRules == null ? List.of() : List.copyOf(sharedRules);
            if (!java.util.Set.of("SPELL", "RANGED_ATTACK", "THROWN").contains(actionKind) || evidenceIds.isEmpty()
                    || evidenceIds.size() > 65
                    || evidenceIds.stream().anyMatch(value -> value == null || value.isBlank())
                    || evidenceIds.stream().distinct().count() != evidenceIds.size()
                    || neededFields.stream().anyMatch(value -> value == null || value.isBlank())
                    || neededFields.stream().distinct().count() != neededFields.size())
                throw new IllegalArgumentException("assigned action input is invalid");
        }
    }

    public record RulebookActionSharedRuleInput(String id, String conditions, String sourceLocator,
            String sourceEvidence, java.util.Map<String, Object> fields) {
        public RulebookActionSharedRuleInput {
            id = required(id, "shared rule id"); conditions = required(conditions, "shared rule conditions");
            sourceLocator = required(sourceLocator, "shared rule source locator"); sourceEvidence = required(sourceEvidence, "shared rule source evidence");
            fields = fields == null ? java.util.Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
            if (id.length() > 120 || conditions.length() > 500 || sourceLocator.length() > 500 || sourceEvidence.length() > 500 || fields.isEmpty())
                throw new IllegalArgumentException("shared rule input exceeds its bounds");
        }
    }

    public record RulebookActionQueryInput(String queryId, String queryFingerprint, int candidateCount) {
        public RulebookActionQueryInput {
            queryId = required(queryId, "queryId"); queryFingerprint = required(queryFingerprint, "queryFingerprint");
            if (candidateCount < 0 || candidateCount > 64) throw new IllegalArgumentException("candidateCount is outside the allowed bound");
        }
    }

    public record RulebookEvidenceInput(String id, String locator, List<RulebookActionAnchorInput> anchors) {
        public RulebookEvidenceInput {
            id = required(id, "evidence id"); locator = required(locator, "evidence locator");
            try { java.util.UUID.fromString(id); } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("evidence id must be a UUID", invalid); }
            anchors = List.copyOf(Objects.requireNonNull(anchors, "evidence anchors are required"));
            if (locator.length() > 500 || anchors.isEmpty() || anchors.size() > 100
                    || anchors.stream().mapToInt(anchor -> anchor.text().length()).sum() > 6000
                    || anchors.stream().map(RulebookActionAnchorInput::id).distinct().count() != anchors.size())
                throw new IllegalArgumentException("retrieved evidence spans exceed their bounds or contain duplicate ids");
        }
    }

    public record RulebookActionAnchorInput(String id, String text) {
        public RulebookActionAnchorInput {
            id = required(id, "source span id"); text = required(text, "source span text");
            if (id.length() > 80 || text.length() > 500) throw new IllegalArgumentException("retrieved evidence span exceeds its size bound");
        }
    }

    public record InitialSituationStageTask(String stageId, String prompt, JsonNode outputSchema) {
        public InitialSituationStageTask {
            stageId = required(stageId, "stageId");
            prompt = required(prompt, "stage prompt");
            if (outputSchema == null || !outputSchema.isObject() || !"object".equals(outputSchema.path("type").asText())
                    || !outputSchema.path("properties").isObject() || !outputSchema.path("required").isArray()
                    || !outputSchema.path("additionalProperties").isBoolean() || outputSchema.path("additionalProperties").booleanValue())
                throw new IllegalArgumentException("stage outputSchema must be a strict object schema");
            outputSchema = outputSchema.deepCopy();
        }
        @Override public JsonNode outputSchema() { return outputSchema.deepCopy(); }
    }

    public record ScenarioLookupRequest(java.util.UUID soloPlayerId, String query, String currentSituation,
            String currentScene, Map<String, Object> establishedGameState,
            List<Map<String, Object>> runtimeAddedFacts, List<Map<String, Object>> evidence,
            Map<String, Object> lockedScenarioModel, String preparedSituation,
            List<Map<String, Object>> longTermFacts, List<String> knownEvidenceIds) {
        public ScenarioLookupRequest(java.util.UUID soloPlayerId, String query, Map<String, Object> lockedScenarioModel) {
            this(soloPlayerId, query, "", "", Map.of(), List.of(), List.of(), lockedScenarioModel, "", List.of(), List.of());
        }
        public ScenarioLookupRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            query = required(query, "query");
            currentSituation = currentSituation == null ? "" : currentSituation;
            currentScene = currentScene == null ? "" : currentScene;
            establishedGameState = Map.copyOf(Objects.requireNonNull(establishedGameState, "establishedGameState is required"));
            runtimeAddedFacts = List.copyOf(Objects.requireNonNull(runtimeAddedFacts, "runtimeAddedFacts is required"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence is required"));
            lockedScenarioModel = Map.copyOf(Objects.requireNonNull(lockedScenarioModel, "lockedScenarioModel is required"));
            preparedSituation = preparedSituation == null ? "" : preparedSituation;
            longTermFacts = List.copyOf(Objects.requireNonNull(longTermFacts, "longTermFacts is required"));
            knownEvidenceIds = List.copyOf(Objects.requireNonNull(knownEvidenceIds, "knownEvidenceIds is required"));
            if (knownEvidenceIds.stream().anyMatch(id -> id == null || !id.startsWith("IMMUTABLE_DOCUMENT:")))
                throw new IllegalArgumentException("knownEvidenceIds must use immutable document references");
        }
    }

    public record ConversationCompactionRequest(java.util.UUID soloPlayerId, long sourceStart, long sourceEnd, long expectedAdventureVersion,
                                                 List<ConversationEntry> conversation, List<RuntimeFactReference> runtimeFacts) {
        public ConversationCompactionRequest(long sourceStart, long sourceEnd, long expectedAdventureVersion,
                List<ConversationEntry> conversation) {
            this(new java.util.UUID(0L, 0L), sourceStart, sourceEnd, expectedAdventureVersion, conversation, List.of());
        }
        public ConversationCompactionRequest(java.util.UUID soloPlayerId, long sourceStart, long sourceEnd, long expectedAdventureVersion, List<ConversationEntry> conversation) { this(soloPlayerId, sourceStart, sourceEnd, expectedAdventureVersion, conversation, List.of()); }
        public ConversationCompactionRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            if (sourceStart < 0 || sourceEnd < sourceStart || expectedAdventureVersion < 0) throw new IllegalArgumentException("invalid conversation range");
            conversation = List.copyOf(Objects.requireNonNull(conversation, "conversation is required"));
            runtimeFacts = List.copyOf(Objects.requireNonNull(runtimeFacts, "runtime facts are required"));
            if (conversation.isEmpty()) throw new IllegalArgumentException("conversation is required");
            long expectedCount = sourceEnd - sourceStart + 1;
            if (expectedCount <= 0 || conversation.size() != expectedCount) throw new IllegalArgumentException("conversation must cover the requested range");
            for (int i = 0; i < conversation.size(); i++) {
                if (conversation.get(i).sequence() != sourceStart + i) throw new IllegalArgumentException("conversation must cover the requested range");
            }
        }
    }
    public record RuntimeFactReference(java.util.UUID factId, java.util.UUID establishedTurnId, String content, String subject) {
        public RuntimeFactReference(java.util.UUID factId, java.util.UUID establishedTurnId, String content) { this(factId, establishedTurnId, content, ""); }
        public RuntimeFactReference { factId = Objects.requireNonNull(factId, "runtime fact id is required"); establishedTurnId = Objects.requireNonNull(establishedTurnId, "runtime fact turn is required"); content = required(content, "runtime fact content"); subject = subject == null ? "" : subject.trim(); }
    }
    public record ConversationEntry(long sequence, String speaker, String content) {
        public ConversationEntry { if (sequence < 0) throw new IllegalArgumentException("sequence is invalid"); speaker = required(speaker, "speaker"); content = required(content, "content"); }
    }
    public record LongTermFactCandidate(java.util.UUID factId, java.util.UUID establishedTurnId, String kind, String relevance, boolean playerVisible) { }
    public record ConversationCompactionResponse(long sourceStart, long sourceEnd, long expectedAdventureVersion,
                                                 String summary, List<LongTermFactCandidate> longTermFacts) {
        public ConversationCompactionResponse(long sourceStart, long sourceEnd, long expectedAdventureVersion, String summary) { this(sourceStart, sourceEnd, expectedAdventureVersion, summary, List.of()); }
        public ConversationCompactionResponse { summary = required(summary, "summary"); longTermFacts = List.copyOf(longTermFacts); }
    }

    public record RuntimeEndpointRequest(java.util.UUID endpointId, String provider, String model, String reasoning) { }
    public record RuntimeEndpointResponse(java.util.UUID endpointId, String endpointVersion,
                                          String provider, String model, String reasoning) { }

    public record CombatTurnDecisionRequest(java.util.UUID soloPlayerId, String operationKey,
            java.util.UUID endpointId, String provider, String model, String reasoning,
            java.util.UUID effectiveEndpointId, String effectiveEndpointVersion,
            String effectiveProvider, String effectiveModel, String prompt) {
        public CombatTurnDecisionRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            operationKey = required(operationKey, "operationKey");
            provider = required(provider, "provider"); model = required(model, "model");
            reasoning = required(reasoning, "reasoning");
            effectiveEndpointId = Objects.requireNonNull(effectiveEndpointId, "effectiveEndpointId is required");
            effectiveEndpointVersion = required(effectiveEndpointVersion, "effectiveEndpointVersion");
            effectiveProvider = required(effectiveProvider, "effectiveProvider");
            effectiveModel = required(effectiveModel, "effectiveModel");
            prompt = required(prompt, "prompt");
        }
    }

    public record CombatTurnDecisionResponse(java.util.UUID actorId, String kind, String action,
            java.util.UUID targetId, List<String> citationKeys, String endTurnAssessment) {
        public CombatTurnDecisionResponse { citationKeys = List.copyOf(citationKeys); }
    }

    public record RuntimeTurnRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                                     java.util.UUID endpointId, String provider, String model, String reasoning,
                                     java.util.UUID effectiveEndpointId, String effectiveEndpointVersion,
                                     String effectiveProvider, String effectiveModel, String prompt,
                                     com.fasterxml.jackson.databind.JsonNode ragSearchContext,
                                     List<String> allowedEvidenceKeys) {
        public RuntimeTurnRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                java.util.UUID endpointId, String provider, String model, String reasoning,
                java.util.UUID effectiveEndpointId, String effectiveEndpointVersion,
                String effectiveProvider, String effectiveModel, String prompt,
                com.fasterxml.jackson.databind.JsonNode ragSearchContext) {
            this(soloPlayerId, operationKey, action, endpointId, provider, model, reasoning,
                    effectiveEndpointId, effectiveEndpointVersion, effectiveProvider, effectiveModel, prompt,
                    ragSearchContext, List.of());
        }
        public RuntimeTurnRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                java.util.UUID endpointId, String provider, String model, String reasoning,
                java.util.UUID effectiveEndpointId, String effectiveEndpointVersion,
                String effectiveProvider, String effectiveModel, String prompt) {
            this(soloPlayerId, operationKey, action, endpointId, provider, model, reasoning,
                    effectiveEndpointId, effectiveEndpointVersion, effectiveProvider, effectiveModel, prompt, null);
        }
        public RuntimeTurnRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                java.util.List<java.util.Map<String, Object>> ignored) {
            this(soloPlayerId, operationKey, action, null, "codex-cli", "gpt-5.6-luna", "medium",
                    java.util.UUID.nameUUIDFromBytes("codex-cli/gpt-5.6-luna".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    java.time.Instant.EPOCH.toString(), "codex-cli", "gpt-5.6-luna", "ROLE=RUNTIME_GM");
        }
        public RuntimeTurnRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                java.util.List<java.util.Map<String, Object>> ignored, java.util.Map<String, Object> ignoredContext) {
            this(soloPlayerId, operationKey, action, ignored);
        }
        public RuntimeTurnRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                java.util.UUID endpointId, String provider, String model, String reasoning,
                java.util.List<java.util.Map<String, Object>> ignored, java.util.Map<String, Object> ignoredContext) {
            this(soloPlayerId, operationKey, action, endpointId, provider, model, reasoning,
                    endpointId == null ? java.util.UUID.nameUUIDFromBytes((provider + "/" + model).getBytes(java.nio.charset.StandardCharsets.UTF_8)) : endpointId,
                    java.time.Instant.EPOCH.toString(), provider, model, "ROLE=RUNTIME_GM");
        }
        public RuntimeTurnRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            operationKey = required(operationKey, "operationKey");
            action = required(action, "action");
            provider = required(provider, "provider");
            model = required(model, "model");
            reasoning = required(reasoning, "reasoning");
            effectiveEndpointId = Objects.requireNonNull(effectiveEndpointId, "effectiveEndpointId is required");
            effectiveEndpointVersion = required(effectiveEndpointVersion, "effectiveEndpointVersion");
            effectiveProvider = required(effectiveProvider, "effectiveProvider");
            effectiveModel = required(effectiveModel, "effectiveModel");
            prompt = required(prompt, "prompt");
            allowedEvidenceKeys = allowedEvidenceKeys == null ? List.of() : List.copyOf(allowedEvidenceKeys);
            if (allowedEvidenceKeys.stream().anyMatch(value -> value == null || value.isBlank())
                    || allowedEvidenceKeys.stream().distinct().count() != allowedEvidenceKeys.size()) {
                throw new IllegalArgumentException("allowedEvidenceKeys must contain unique nonblank identifiers");
            }
        }
    }

    public record NarrationSafetyRequest(java.util.UUID soloPlayerId, String narration, List<String> disclosedFactIds) {
        public NarrationSafetyRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            narration = required(narration, "narration");
            disclosedFactIds = List.copyOf(Objects.requireNonNull(disclosedFactIds, "disclosedFactIds is required"));
        }
    }

    public record ScenarioCompilationResponse(String status, Map<String, Object> scenarioModel, List<String> diagnostics) {
        public ScenarioCompilationResponse {
            scenarioModel = scenarioModel == null ? Map.of() : Map.copyOf(scenarioModel);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }
    public record ScenarioLookupResponse(String status, String answer, List<String> supportingElementIds) { }
    public record RuntimeTurnResponse(String scene, String judgment, String narration, boolean combatStart,
                                      List<CombatEnemyResponse> combatEnemies, SituationResponse situation,
                                      boolean mapEntryRequested, List<RuntimeFactResponse> runtimeFacts,
                                      CompletionResponse completion, List<String> citedEvidence,
                                      @JsonProperty("판정제안") CheckProposalResponse checkProposal) {
        public RuntimeTurnResponse(String scene, String judgment, String narration, boolean combatStart,
                List<CombatEnemyResponse> combatEnemies, SituationResponse situation, boolean mapEntryRequested) {
            this(scene, judgment, narration, combatStart, combatEnemies, situation, mapEntryRequested, List.of(),
                    CompletionResponse.continueAdventure(), List.of(), CheckProposalResponse.none());
        }
        public RuntimeTurnResponse(String scene, String judgment, String narration, boolean combatStart,
                List<CombatEnemyResponse> combatEnemies, SituationResponse situation, boolean mapEntryRequested,
                List<RuntimeFactResponse> runtimeFacts) {
            this(scene, judgment, narration, combatStart, combatEnemies, situation, mapEntryRequested, runtimeFacts,
                    CompletionResponse.continueAdventure(), List.of(), CheckProposalResponse.none());
        }
        public RuntimeTurnResponse {
            runtimeFacts = runtimeFacts == null ? List.of() : List.copyOf(runtimeFacts);
            completion = completion == null ? CompletionResponse.continueAdventure() : completion;
            citedEvidence = citedEvidence == null ? List.of() : List.copyOf(citedEvidence);
            checkProposal = checkProposal == null ? CheckProposalResponse.none() : checkProposal;
        }
    }
    public record CheckProposalResponse(@JsonProperty("필요") boolean required,
                                        @JsonProperty("이유") String reason,
                                        @JsonProperty("판정능력또는기술") String abilityOrSkill,
                                        @JsonProperty("대상캐릭터ID") java.util.UUID characterSheetId,
                                        @JsonProperty("굴림주체") String rollMethod,
                                        @JsonProperty("굴림식") String diceExpression,
                                        @JsonProperty("보정치") int modifier,
                                        @JsonProperty("난이도") Integer difficulty,
                                        @JsonProperty("근거키") List<String> evidenceKeys,
                                        @JsonProperty("성공시결과") String successOutcome,
                                        @JsonProperty("실패시결과") String failureOutcome) {
        public CheckProposalResponse {
            reason = reason == null ? "" : reason;
            abilityOrSkill = abilityOrSkill == null ? "" : abilityOrSkill;
            rollMethod = rollMethod == null ? "" : rollMethod;
            diceExpression = diceExpression == null ? "" : diceExpression;
            evidenceKeys = evidenceKeys == null ? List.of() : List.copyOf(evidenceKeys);
            successOutcome = successOutcome == null ? "" : successOutcome;
            failureOutcome = failureOutcome == null ? "" : failureOutcome;
        }
        public static CheckProposalResponse none() {
            return new CheckProposalResponse(false, "", "", null, "", "", 0, null, List.of(), "", "");
        }
    }
    public record CombatEnemyResponse(String mode, String scenarioId, String enemyKey, String name, int count,
                                     List<CombatEnemyAbilityResponse> abilities, List<CombatEnemyActionResponse> actions) {
        public CombatEnemyResponse(String mode, String scenarioId, String enemyKey, String name, int count) {
            this(mode, scenarioId, enemyKey, name, count, List.of(), List.of());
        }
        public CombatEnemyResponse {
            abilities = abilities == null ? List.of() : List.copyOf(abilities);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }
    public record CombatEnemyAbilityResponse(String ability, int score, List<String> citationKeys) {
        public CombatEnemyAbilityResponse { citationKeys = citationKeys == null ? List.of() : List.copyOf(citationKeys); }
    }
    public record CombatEnemyActionResponse(String name, String description, List<String> citationKeys) {
        public CombatEnemyActionResponse { citationKeys = citationKeys == null ? List.of() : List.copyOf(citationKeys); }
    }
    public record SituationResponse(String kind, String location, String problem, String threat, String goal,
                                    String basis, String reference, boolean required) { }
    public record RuntimeFactResponse(String subject, String content) { }
    public record CompletionResponse(boolean complete, List<String> resolvedObjectiveIds,
            List<String> satisfiedResolutionCriteriaIds, String concludingScene) {
        public CompletionResponse {
            resolvedObjectiveIds = resolvedObjectiveIds == null ? List.of() : List.copyOf(resolvedObjectiveIds);
            satisfiedResolutionCriteriaIds = satisfiedResolutionCriteriaIds == null
                    ? List.of() : List.copyOf(satisfiedResolutionCriteriaIds);
            concludingScene = concludingScene == null ? "" : concludingScene.trim();
        }
        public static CompletionResponse continueAdventure() {
            return new CompletionResponse(false, List.of(), List.of(), "");
        }
    }
    public record NarrationSafetyResponse(boolean approved, String reason) { }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}

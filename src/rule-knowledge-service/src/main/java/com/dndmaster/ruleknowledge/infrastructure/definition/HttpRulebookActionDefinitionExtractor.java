package com.dndmaster.ruleknowledge.infrastructure.definition;

import com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinition;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionExtractionPort;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionEvidence;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionQueryCoverage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sends only retrieved evidence to the existing typed AI boundary. */
public final class HttpRulebookActionDefinitionExtractor implements RulebookActionDefinitionExtractionPort {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HttpRulebookActionDefinitionExtractor.class);
    private final URI baseUri;
    private final String internalToken;
    private final UUID endpointId;
    private final String provider;
    private final String model;
    private final String reasoning;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public HttpRulebookActionDefinitionExtractor(URI baseUri, String internalToken, UUID endpointId,
            String provider, String model, String reasoning, Duration timeout, ObjectMapper mapper) {
        this.baseUri = baseUri; this.internalToken = internalToken; this.endpointId = endpointId;
        this.provider = provider; this.model = model; this.reasoning = reasoning; this.timeout = timeout; this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        if (internalToken == null || internalToken.isBlank() || provider == null || provider.isBlank()
                || model == null || model.isBlank() || reasoning == null || reasoning.isBlank())
            throw new IllegalArgumentException("rulebook definition model and internal token are required");
    }

    @Override
    public List<com.dndmaster.ruleknowledge.application.definition.RulebookActionCandidate> discover(UUID documentId,
            String extractionVersion, List<RulebookActionEvidence> evidence,
            List<RulebookActionQueryCoverage> queryCoverage, UUID executionActorPlayerId) {
        if (evidence.isEmpty()) return List.of();
        if (executionActorPlayerId == null) throw new IllegalArgumentException("extraction execution actor is required");
        String operation = "rulebook-action-discovery:v2:" + documentId + ":" + extractionVersion + ":" + fingerprint(
                queryCoverage.stream().map(RulebookActionQueryCoverage::queryFingerprint).toList().toString());
        long started = System.nanoTime();
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("soloPlayerId", executionActorPlayerId.toString());
            if (endpointId == null) body.putNull("endpointId"); else body.put("endpointId", endpointId.toString());
            body.put("provider", provider); body.put("model", model); body.put("reasoning", reasoning);
            body.put("operationKey", operation);
            body.set("extractionInput", input(documentId, extractionVersion, evidence, queryCoverage));
            body.set("outputSchema", discoverySchema(evidence));
            log("dev_rulebook_action_discovery stage=request documentId={} extractionVersion={} operationKey={} inputEvidenceCount={} inputChars={} queryCount={} outcome=started",
                    documentId, extractionVersion, operation, evidence.size(), evidence.stream().mapToInt(item -> item.text().length()).sum(), queryCoverage.size());
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/gm/rulebook-action-definitions"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("rulebook action discovery endpoint returned HTTP " + response.statusCode());
            JsonNode root = mapper.readTree(response.body());
            if (!root.isObject() || !root.path("candidates").isArray())
                throw new IllegalArgumentException("action discovery response does not match its schema");
            Map<String, EvidenceAnchor> anchors = anchors(evidence);
            Map<String, com.dndmaster.ruleknowledge.application.definition.RulebookActionCandidate> unique = new LinkedHashMap<>();
            for (JsonNode item : root.path("candidates")) {
                String name = required(item, "name");
                String kind = required(item, "actionKind");
                String anchorId = required(item, "sourceAnchorId");
                EvidenceAnchor anchor = anchors.get(anchorId);
                if (anchor == null || !Set.of("SPELL", "RANGED_ATTACK", "THROWN").contains(kind))
                    throw new IllegalArgumentException("discovered action does not cite an allowed source anchor");
                String id = UUID.nameUUIDFromBytes((documentId + ":" + extractionVersion + ":" + kind + ":"
                        + name.strip().toLowerCase(java.util.Locale.ROOT) + ":" + anchor.evidence().locator())
                        .getBytes(StandardCharsets.UTF_8)).toString();
                var candidate = new com.dndmaster.ruleknowledge.application.definition.RulebookActionCandidate(id, name, kind,
                        anchor.evidence().locator(), anchor.evidence().id(), anchor.text());
                unique.putIfAbsent(name.strip().toLowerCase(java.util.Locale.ROOT) + ":" + kind + ":" + candidate.sourceLocator(), candidate);
            }
            log("dev_rulebook_action_discovery stage=response documentId={} extractionVersion={} operationKey={} candidateCount={} candidateIds={} elapsedMs={} outcome=completed",
                    documentId, extractionVersion, operation, unique.size(), unique.values().stream().map(item -> item.id()).toList(), elapsed(started));
            return List.copyOf(unique.values());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("rulebook action discovery was interrupted", interrupted);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("rulebook action discovery request failed", failure);
        }
    }

    private ObjectNode discoverySchema(List<RulebookActionEvidence> evidence) {
        ObjectNode schema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false).put("x-stage", "DISCOVERY");
        schema.putArray("required").add("candidates");
        ObjectNode candidates = schema.putObject("properties").putObject("candidates").put("type", "array");
        ObjectNode item = candidates.putObject("items").put("type", "object").put("additionalProperties", false);
        item.putArray("required").add("name").add("actionKind").add("sourceAnchorId");
        ObjectNode properties = item.putObject("properties");
        properties.putObject("name").put("type", "string").put("minLength", 1).put("maxLength", 200);
        properties.putObject("actionKind").put("type", "string").putArray("enum").add("SPELL").add("RANGED_ATTACK").add("THROWN");
        putAnchorEnum(properties.putObject("sourceAnchorId").put("type", "string").put("pattern", "^[0-9]{1,6}$"), anchorIds(evidence, null));
        return schema;
    }

    @Override
    public long estimateAssignedBatchInputTokens(UUID documentId, String extractionVersion,
            List<com.dndmaster.ruleknowledge.application.definition.RulebookActionAssignment> assigned,
            List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> queryCoverage,
            UUID executionActorPlayerId) {
        String operation = assignedOperation(documentId, extractionVersion, assigned, queryCoverage);
        ObjectNode body = assignedRequestBody(documentId, extractionVersion, assigned, evidence, queryCoverage,
                executionActorPlayerId, operation);
        long started = System.nanoTime();
        log("dev_rulebook_action_ai stage=token_estimate_request operationKey={} assignedCount={} evidenceCount={} schemaBytes={} outcome=started",
                operation, assigned.size(), evidence.size(), body.path("outputSchema").toString().getBytes(StandardCharsets.UTF_8).length);
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/gm/rulebook-action-definitions/estimate"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log("dev_rulebook_action_ai stage=token_estimate_response operationKey={} httpStatus={} responseChars={} failureKind=http_status recoveryAction=abort_batch_pack outcome=failed",
                        operation, response.statusCode(), response.body().length());
                throw new IllegalStateException("assigned action token estimate endpoint returned HTTP " + response.statusCode());
            }
            JsonNode result = mapper.readTree(response.body());
            JsonNode tokenCount = result.path("estimatedInputTokens");
            if (!tokenCount.isIntegralNumber() || !tokenCount.canConvertToLong() || tokenCount.longValue() < 0
                    || !"o200k_base".equals(result.path("tokenizer").asText())
                    || !result.path("includesProviderSystemAndToolOverhead").isBoolean()
                    || result.path("includesProviderSystemAndToolOverhead").booleanValue()) {
                log("dev_rulebook_action_ai stage=token_estimate_response operationKey={} expectedFields=[estimatedInputTokens:nonnegative_integer,tokenizer:o200k_base,includesProviderSystemAndToolOverhead:false] actualFields={} actualTokenCountType={} actualTokenCount={} actualTokenizer={} actualIncludesExternalOverhead={} failureKind=schema_mismatch recoveryAction=abort_batch_pack outcome=failed",
                        operation, safeTopLevelFields(response.body()), tokenCount.getNodeType(), tokenCount.asText("MISSING"),
                        result.path("tokenizer").asText("MISSING"), result.path("includesProviderSystemAndToolOverhead").asText("MISSING"));
                throw new IllegalArgumentException("assigned action token estimate response does not match its schema");
            }
            long estimate = result.path("estimatedInputTokens").longValue();
            log("dev_rulebook_action_ai stage=token_estimate_response operationKey={} assignedCount={} evidenceCount={} estimatedInputTokens={} tokenizer={} elapsedMs={} includesProviderSystemAndToolOverhead=false outcome=completed",
                    operation, assigned.size(), evidence.size(), estimate, result.path("tokenizer").asText("UNKNOWN"), elapsed(started));
            return estimate;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log("dev_rulebook_action_ai stage=token_estimate_response operationKey={} failureKind=interrupted elapsedMs={} recoveryAction=abort_batch_pack outcome=failed",
                    operation, elapsed(started));
            throw new IllegalStateException("assigned action token estimate was interrupted", interrupted);
        } catch (java.io.IOException failure) {
            log("dev_rulebook_action_ai stage=token_estimate_response operationKey={} failureKind=io elapsedMs={} recoveryAction=abort_batch_pack outcome=failed",
                    operation, elapsed(started));
            throw new IllegalStateException("assigned action token estimate request failed", failure);
        }
    }

    private String assignedOperation(UUID documentId, String extractionVersion,
            List<com.dndmaster.ruleknowledge.application.definition.RulebookActionAssignment> assigned,
            List<RulebookActionQueryCoverage> queryCoverage) {
        return "rulebook-actions:assigned-v4:" + documentId + ":" + extractionVersion + ":"
                + fingerprint(assigned.stream().map(item -> item.candidate().id()).toList().toString() + queryCoverage);
    }

    private ObjectNode assignedRequestBody(UUID documentId, String extractionVersion,
            List<com.dndmaster.ruleknowledge.application.definition.RulebookActionAssignment> assigned,
            List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> queryCoverage,
            UUID executionActorPlayerId, String operation) {
        ObjectNode body = mapper.createObjectNode().put("soloPlayerId", executionActorPlayerId.toString());
        if (endpointId == null) body.putNull("endpointId"); else body.put("endpointId", endpointId.toString());
        body.put("provider", provider); body.put("model", model); body.put("reasoning", reasoning); body.put("operationKey", operation);
        ObjectNode input = input(documentId, extractionVersion, evidence, queryCoverage);
        ArrayNode assignedInput = input.putArray("assignedActions");
        assigned.forEach(assignment -> {
            var candidate = assignment.candidate();
            ObjectNode item = assignedInput.addObject().put("id", candidate.id()).put("name", candidate.name())
                    .put("actionKind", candidate.actionKind()).put("sourceLocator", candidate.sourceLocator());
            ArrayNode ids = item.putArray("evidenceIds"); assignment.evidenceIds().forEach(ids::add);
            item.set("knownFields", mapper.valueToTree(assignment.knownFields()));
            ArrayNode needed = item.putArray("neededFields"); assignment.neededFields().forEach(needed::add);
            ArrayNode sharedRules = item.putArray("sharedRules");
            assignment.availableSharedRules().forEach(rule -> {
                ObjectNode shared = sharedRules.addObject().put("id", rule.id()).put("conditions", rule.conditions())
                        .put("sourceLocator", rule.sourceLocator()).put("sourceEvidence", rule.sourceEvidence());
                shared.set("fields", mapper.valueToTree(rule.fields()));
            });
        });
        body.set("extractionInput", input);
        body.set("outputSchema", assignedSchema(assigned, evidence));
        return body;
    }

    @Override
    public List<com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionResult> extractAssignedBatch(UUID documentId,
            String extractionVersion, List<com.dndmaster.ruleknowledge.application.definition.RulebookActionAssignment> assigned,
            List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> queryCoverage, UUID executionActorPlayerId) {
        if (assigned.isEmpty()) return List.of();
        String operation = assignedOperation(documentId, extractionVersion, assigned, queryCoverage);
        long started = System.nanoTime();
        JsonNode responseShape = null;
        try {
            ObjectNode body = assignedRequestBody(documentId, extractionVersion, assigned, evidence, queryCoverage,
                    executionActorPlayerId, operation);
            log("dev_rulebook_action_ai stage=assigned_request documentId={} extractionVersion={} operationKey={} assignedCount={} evidenceCount={} inputChars={} outcome=started",
                    documentId, extractionVersion, operation, assigned.size(), evidence.size(), evidence.stream().mapToInt(item -> item.text().length()).sum());
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/gm/rulebook-action-definitions"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log("dev_rulebook_action_ai stage=assigned_response documentId={} extractionVersion={} operationKey={} httpStatus={} responseChars={} responseFields={} responseCode={} outcome=failed",
                        documentId, extractionVersion, operation, response.statusCode(), response.body().length(), safeTopLevelFields(response.body()), safeErrorCode(response.body()));
                if ("OUTPUT_LIMIT".equals(safeErrorCode(response.body())))
                    throw new com.dndmaster.ruleknowledge.application.definition.RulebookActionOutputLimitException();
                throw new IllegalStateException("assigned action extraction endpoint returned HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            responseShape = root;
            if (!root.isObject() || !root.path("candidateResults").isArray() || !root.path("definitions").isArray())
                throw new IllegalArgumentException("assigned action response does not match its schema");
            Map<String, com.dndmaster.ruleknowledge.application.definition.RulebookActionAssignment> expected = assigned.stream()
                    .collect(java.util.stream.Collectors.toMap(item -> item.candidate().id(), item -> item));
            Map<String, JsonNode> outcomes = new LinkedHashMap<>();
            root.path("candidateResults").forEach(item -> {
                String id = required(item, "candidateId");
                if (!expected.containsKey(id) || outcomes.putIfAbsent(id, item) != null)
                    throw new IllegalArgumentException("assigned response contains a duplicate or unknown candidate ID");
            });
            if (!outcomes.keySet().equals(expected.keySet())) throw new IllegalArgumentException("assigned response omitted a candidate ID");
            Map<String, JsonNode> definitions = new LinkedHashMap<>();
            root.path("definitions").forEach(item -> {
                String id = required(item, "id");
                if (!expected.containsKey(id) || definitions.putIfAbsent(id, item) != null)
                    throw new IllegalArgumentException("assigned response contains a duplicate or unknown definition ID");
            });
            Map<String, EvidenceAnchor> anchors = anchors(evidence);
            List<com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionResult> result = new ArrayList<>();
            for (var assignment : assigned) {
                var candidate = assignment.candidate();
                JsonNode outcome = outcomes.get(candidate.id());
                String status = required(outcome, "status");
                List<String> neededFields = new ArrayList<>();
                outcome.path("neededFields").forEach(value -> { if (value.isTextual()) neededFields.add(value.asText()); });
                    List<String> requestedFields = assignment.neededFields();
                JsonNode item = definitions.get(candidate.id());
                if ("COMPLETED".equals(status)) {
                    if (item == null || !candidate.name().equals(required(item, "name"))
                            || !candidate.actionKind().equals(required(item, "actionKind")))
                        throw new IllegalArgumentException("assigned definition does not match its candidate identity");
                    String anchorId = required(item, "sourceAnchorId");
                    EvidenceAnchor anchor = anchors.get(anchorId);
                if (anchor == null || !assignment.evidenceIds().contains(anchor.evidence().id())) {
                    List<String> allowedAnchorIds = anchorIds(evidence, assignment.evidenceIds());
                    log("dev_rulebook_action_ai stage=assigned_source_anchor_rejected documentId={} actionId={} expectedAllowedEvidenceIds={} expectedAllowedAnchorCount={} expectedAllowedAnchorIds={} actualSourceAnchorId={} actualSourceEvidenceId={} recoveryAction=reject_out_of_scope_citation outcome=failed",
                            documentId, candidate.id(), assignment.evidenceIds(), allowedAnchorIds.size(), allowedAnchorIds,
                            anchorId, anchor == null ? "UNKNOWN_ANCHOR" : anchor.evidence().id());
                        throw new IllegalArgumentException("assigned definition cites evidence outside its candidate scope");
                }
                    Map<String, Object> fields = mapper.convertValue(item.path("fields"), mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                    List<String> unsupported = new ArrayList<>();
                    item.path("unsupportedFields").forEach(value -> { if (value.isTextual()) unsupported.add(value.asText()); });
                    List<com.dndmaster.ruleknowledge.application.definition.RulebookActionSharedRule> extractedSharedRules = new ArrayList<>();
                    List<String> sharedRuleRefs = new ArrayList<>();
                    Map<String, com.dndmaster.ruleknowledge.application.definition.RulebookActionSharedRule> priorRules = assignment.availableSharedRules().stream()
                            .collect(java.util.stream.Collectors.toMap(com.dndmaster.ruleknowledge.application.definition.RulebookActionSharedRule::id,
                                    value -> value));
                    Map<String, String> priorApplicability = new LinkedHashMap<>();
                    outcome.path("sharedRuleApplicability").forEach(value -> {
                        String sharedId = required(value, "id");
                        String decision = required(value, "applicability");
                        if (!priorRules.containsKey(sharedId) || priorApplicability.putIfAbsent(sharedId, decision) != null
                                || !Set.of("APPLIES", "AMBIGUOUS", "NOT_APPLICABLE").contains(decision))
                            throw new IllegalArgumentException("shared rule applicability contains an unknown or duplicate reference");
                    });
                    if (!priorApplicability.keySet().equals(priorRules.keySet()))
                        throw new IllegalArgumentException("shared rule applicability omitted an available reference");
                    for (var entry : priorApplicability.entrySet()) {
                        if (!"APPLIES".equals(entry.getValue())) continue;
                        var prior = priorRules.get(entry.getKey());
                        sharedRuleRefs.add(entry.getKey());
                        prior.fields().forEach((key, value) -> {
                            if (requestedFields.contains(key) && fields.get(key) == null) fields.put(key, value);
                        });
                    }
                    if (requestedFields.isEmpty() && !outcome.path("sharedRules").isEmpty())
                        throw new IllegalArgumentException("shared rules cannot be extracted without requested fields");
                    for (JsonNode sharedRule : outcome.path("sharedRules")) {
                        String applicability = required(sharedRule, "applicability");
                        String conditions = required(sharedRule, "conditions");
                        String sharedAnchorId = required(sharedRule, "sourceAnchorId");
                        EvidenceAnchor sharedAnchor = anchors.get(sharedAnchorId);
                        if (sharedAnchor == null || !assignment.evidenceIds().contains(sharedAnchor.evidence().id())) {
                            List<String> allowedAnchorIds = anchorIds(evidence, assignment.evidenceIds());
                            log("dev_rulebook_action_ai stage=assigned_shared_anchor_rejected documentId={} actionId={} expectedAllowedEvidenceIds={} expectedAllowedAnchorCount={} expectedAllowedAnchorIds={} actualSourceAnchorId={} actualSourceEvidenceId={} recoveryAction=reject_out_of_scope_citation outcome=failed",
                                    documentId, candidate.id(), assignment.evidenceIds(), allowedAnchorIds.size(), allowedAnchorIds,
                                    sharedAnchorId, sharedAnchor == null ? "UNKNOWN_ANCHOR" : sharedAnchor.evidence().id());
                            throw new IllegalArgumentException("shared rule cites evidence outside its assigned action scope");
                        }
                        Map<String, Object> sparseFields = mapper.convertValue(sharedRule.path("fields"),
                                mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                        sparseFields.entrySet().removeIf(entry -> !requestedFields.contains(entry.getKey()) || entry.getValue() == null);
                        if (sparseFields.isEmpty()) throw new IllegalArgumentException("shared rule does not provide a requested field");
                        String sharedId = "shared:" + fingerprint(sharedAnchor.evidence().id() + ":" + conditions + ":" +
                                mapper.writeValueAsString(new java.util.TreeMap<>(sparseFields)));
                        var shared = new com.dndmaster.ruleknowledge.application.definition.RulebookActionSharedRule(sharedId,
                                applicability, conditions, sharedAnchor.evidence().id(), sharedAnchor.evidence().locator(),
                                sharedAnchor.text(), sparseFields);
                        extractedSharedRules.add(shared);
                        if ("APPLIES".equals(applicability)) {
                            sharedRuleRefs.add(sharedId);
                            sparseFields.forEach((key, value) -> { if (requestedFields.contains(key) && fields.get(key) == null) fields.put(key, value); });
                        }
                    }
                    var definition = new RulebookActionDefinition(candidate.id(), candidate.name(), candidate.actionKind(),
                            anchor.evidence().locator(), anchor.evidence().id(), anchor.text(), fields, unsupported, sharedRuleRefs);
                    result.add(new com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionResult(candidate.id(), status, definition, null,
                            neededFields, extractedSharedRules, sharedRuleRefs));
                } else {
                    if (item != null) throw new IllegalArgumentException("non-completed assigned action must not contain a definition");
                    result.add(new com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionResult(candidate.id(),
                            status, null, required(outcome, "reason"), neededFields));
                }
            }
            log("dev_rulebook_action_ai stage=assigned_response documentId={} extractionVersion={} operationKey={} expectedResultCount={} actualResultCount={} completedCount={} insufficientCount={} failedCount={} elapsedMs={} outcome=completed",
                    documentId, extractionVersion, operation, assigned.size(), result.size(), result.stream().filter(item -> "COMPLETED".equals(item.status())).count(),
                    result.stream().filter(item -> "INSUFFICIENT_EVIDENCE".equals(item.status())).count(), result.stream().filter(item -> "FAILED".equals(item.status())).count(), elapsed(started));
            return List.copyOf(result);
        } catch (IllegalArgumentException invalid) {
            log("dev_rulebook_action_ai stage=assigned_response_validation documentId={} extractionVersion={} operationKey={} expectedAssignedIds={} actualResultCount={} actualResultIds={} actualStatuses={} definitionCount={} definitionIds={} failureClass={} elapsedMs={} outcome=failed",
                    documentId, extractionVersion, operation, assigned.stream().map(item -> item.candidate().id()).toList(),
                    responseShape != null && responseShape.path("candidateResults").isArray() ? responseShape.path("candidateResults").size() : -1,
                    responseTextValues(responseShape == null ? null : responseShape.path("candidateResults"), "candidateId"),
                    responseTextValues(responseShape == null ? null : responseShape.path("candidateResults"), "status"),
                    responseShape != null && responseShape.path("definitions").isArray() ? responseShape.path("definitions").size() : -1,
                    responseTextValues(responseShape == null ? null : responseShape.path("definitions"), "id"), invalid.getClass().getSimpleName(), elapsed(started));
            throw invalid;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("assigned action extraction was interrupted", interrupted);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("assigned action extraction request failed", failure);
        }
    }

    private ObjectNode assignedSchema(List<com.dndmaster.ruleknowledge.application.definition.RulebookActionAssignment> assigned,
            List<RulebookActionEvidence> evidence) {
        ObjectNode schema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false).put("x-stage", "ASSIGNED");
        schema.putArray("required").add("candidateResults").add("definitions");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode results = properties.putObject("candidateResults").put("type", "array").put("minItems", assigned.size()).put("maxItems", assigned.size());
        ObjectNode resultItem = results.putObject("items").put("type", "object").put("additionalProperties", false);
        resultItem.putArray("required").add("candidateId").add("status").add("reason").add("neededFields").add("sharedRules").add("sharedRuleApplicability");
        ObjectNode resultProps = resultItem.putObject("properties");
        ArrayNode ids = resultProps.putObject("candidateId").put("type", "string").putArray("enum");
        assigned.forEach(item -> ids.add(item.candidate().id()));
        resultProps.putObject("status").put("type", "string").putArray("enum").add("COMPLETED").add("INSUFFICIENT_EVIDENCE").add("FAILED");
        resultProps.putObject("reason").putArray("type").add("string").add("null");
        ObjectNode neededFields = resultProps.putObject("neededFields").put("type", "array");
        neededFields.putObject("items").put("type", "string").putArray("enum").add("level").add("castingTime").add("cost").add("range")
                .add("rangeForm").add("components").add("duration").add("school").add("conditions").add("attackSave")
                .add("damageEffect").add("effect").add("targetMode").add("originMode").add("effectShape").add("pathBlocking")
                .add("propagation").add("verticalDirection").add("normalRangeFeet").add("longRangeFeet").add("lengthFeet")
                .add("widthFeet").add("heightFeet").add("radiusFeet");
        boolean initialExtraction = assigned.stream().allMatch(item -> item.neededFields().isEmpty());
        ObjectNode sharedRules = resultProps.putObject("sharedRules").put("type", "array").put("maxItems", initialExtraction ? 0 : 8);
        ObjectNode sharedRule = sharedRules.putObject("items").put("type", "object").put("additionalProperties", false);
        sharedRule.putArray("required").add("applicability").add("conditions").add("sourceAnchorId").add("fields");
        ObjectNode sharedRuleProperties = sharedRule.putObject("properties");
        sharedRuleProperties.putObject("applicability").put("type", "string").putArray("enum").add("APPLIES").add("AMBIGUOUS");
        sharedRuleProperties.putObject("conditions").put("type", "string").put("minLength", 1).put("maxLength", 500);
        putAnchorEnum(sharedRuleProperties.putObject("sourceAnchorId").put("type", "string").put("pattern", "^[0-9]{1,6}$"),
                anchorIds(evidence, assigned.stream().flatMap(item -> item.evidenceIds().stream()).distinct().toList()));
        ObjectNode sharedFieldsSchema = (ObjectNode) outputSchema(evidence, null, null, null).path("properties").path("definitions")
                .path("items").path("properties").path("fields").deepCopy();
        sharedRuleProperties.set("fields", sharedFieldsSchema);
        ObjectNode applicability = resultProps.putObject("sharedRuleApplicability").put("type", "array")
                .put("minItems", assigned.stream().mapToInt(item -> item.availableSharedRules().size()).sum())
                .put("maxItems", assigned.stream().mapToInt(item -> item.availableSharedRules().size()).sum());
        ObjectNode applicabilityItem = applicability.putObject("items").put("type", "object").put("additionalProperties", false);
        applicabilityItem.putArray("required").add("id").add("applicability");
        ObjectNode applicabilityProperties = applicabilityItem.putObject("properties");
        ObjectNode storedRuleId = applicabilityProperties.putObject("id").put("type", "string");
        List<String> allowedStoredRuleIds = assigned.stream().flatMap(item -> item.availableSharedRules().stream())
                .map(com.dndmaster.ruleknowledge.application.definition.RulebookActionSharedRule::id).distinct().toList();
        if (!allowedStoredRuleIds.isEmpty()) {
            ArrayNode storedRuleIds = storedRuleId.putArray("enum");
            allowedStoredRuleIds.forEach(storedRuleIds::add);
        }
        applicabilityProperties.putObject("applicability").put("type", "string").putArray("enum")
                .add("APPLIES").add("AMBIGUOUS").add("NOT_APPLICABLE");
        ObjectNode definitionSchema = outputSchema(evidence, null, null, null).path("properties").path("definitions").deepCopy();
        definitionSchema.put("maxItems", assigned.size());
        ObjectNode definitionId = (ObjectNode) definitionSchema.path("items").path("properties").path("id");
        ArrayNode assignedIds = definitionId.putArray("enum"); assigned.forEach(item -> assignedIds.add(item.candidate().id()));
        ArrayNode identities = ((ObjectNode) definitionSchema.path("items")).putArray("anyOf");
        ObjectNode baseItem = (ObjectNode) definitionSchema.path("items");
        assigned.forEach(item -> {
            ObjectNode variant = identities.addObject();
            variant.set("properties", baseItem.path("properties").deepCopy());
            ObjectNode identity = (ObjectNode) variant.path("properties");
            identity.putObject("id").put("type", "string").putArray("enum").add(item.candidate().id());
            identity.putObject("name").put("type", "string").putArray("enum").add(item.candidate().name());
            identity.putObject("actionKind").put("type", "string").putArray("enum").add(item.candidate().actionKind());
            putAnchorEnum(identity.putObject("sourceAnchorId").put("type", "string").put("pattern", "^[0-9]{1,6}$"),
                    anchorIds(evidence, item.evidenceIds()));
            variant.set("required", baseItem.path("required").deepCopy());
            variant.put("additionalProperties", false);
        });
        properties.set("definitions", definitionSchema);
        return schema;
    }

    @Override
    public List<RulebookActionDefinition> extract(UUID documentId, String extractionVersion,
            List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> queryCoverage,
            UUID executionActorPlayerId) {
        return extract(documentId, extractionVersion, null, null, null, evidence, queryCoverage, executionActorPlayerId);
    }

    @Override
    public List<RulebookActionDefinition> extractAssigned(UUID documentId, String extractionVersion,
            String expectedActionId, String expectedName, String expectedKind, List<RulebookActionEvidence> evidence,
            List<RulebookActionQueryCoverage> queryCoverage, UUID executionActorPlayerId) {
        if (expectedActionId == null || expectedActionId.isBlank() || expectedName == null || expectedName.isBlank()
                || expectedKind == null || expectedKind.isBlank()) throw new IllegalArgumentException("assigned action identity is required");
        return extract(documentId, extractionVersion, expectedActionId, expectedName, expectedKind, evidence, queryCoverage, executionActorPlayerId);
    }

    private List<RulebookActionDefinition> extract(UUID documentId, String extractionVersion, String expectedActionId,
            String expectedName, String expectedKind,
            List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> queryCoverage,
            UUID executionActorPlayerId) {
        if (evidence.isEmpty()) return List.of();
        if (executionActorPlayerId == null) throw new IllegalArgumentException("extraction execution actor is required");
        String operation = "rulebook-actions:source-anchor-v4:" + documentId + ":" + extractionVersion + ":" + fingerprint(
                queryCoverage.stream().map(RulebookActionQueryCoverage::queryFingerprint).toList().toString());
        long started = System.nanoTime();
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("soloPlayerId", executionActorPlayerId.toString());
            if (endpointId == null) body.putNull("endpointId"); else body.put("endpointId", endpointId.toString());
            body.put("provider", provider); body.put("model", model); body.put("reasoning", reasoning);
            body.put("operationKey", operation);
            body.set("extractionInput", input(documentId, extractionVersion, evidence, queryCoverage));
            body.set("outputSchema", outputSchema(evidence, expectedActionId, expectedName, expectedKind));
            log("dev_rulebook_action_ai stage=request documentId={} extractionVersion={} operationKey={} provider={} model={} endpointId={} inputEvidenceCount={} inputChars={} queryCount={} schemaId=rulebook-action-definitions-source-anchor-v4 outcome=started",
                    documentId, extractionVersion, operation, provider, model, endpointId, evidence.size(),
                    evidence.stream().mapToInt(item -> item.text().length()).sum(), queryCoverage.size());
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/gm/rulebook-action-definitions"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log("dev_rulebook_action_ai stage=response documentId={} extractionVersion={} operationKey={} expectedStatus=2xx actualStatus={} elapsedMs={} outcome=failed",
                        documentId, extractionVersion, operation, response.statusCode(), elapsed(started));
                throw new IllegalStateException("rulebook action extraction endpoint returned HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            if (!root.isObject() || !root.path("definitions").isArray()) {
                log("dev_rulebook_action_ai stage=schema_validation documentId={} extractionVersion={} operationKey={} expectedShape=object_with_definitions_array actualShape={} outcome=failed",
                        documentId, extractionVersion, operation, shape(root));
                throw new IllegalArgumentException("definition extraction response does not match schema");
            }
            Set<String> allowed = evidence.stream().map(RulebookActionEvidence::id).collect(java.util.stream.Collectors.toSet());
            Set<String> ids = new HashSet<>();
            Map<String, EvidenceAnchor> anchors = anchors(evidence);
            List<RulebookActionDefinition> result = new ArrayList<>();
            int index = 0;
            for (JsonNode item : root.path("definitions")) {
                String anchorId = required(item, "sourceAnchorId");
                String definitionId = required(item, "id");
                EvidenceAnchor anchor = anchors.get(anchorId);
                if (anchor == null || !allowed.contains(anchor.evidence().id()))
                    throw validationFailure(documentId, extractionVersion, anchorId, "anchor_id_outside_input", index++);
                String evidenceId = anchor.evidence().id();
                String quote = anchor.text();
                if (!anchor.evidence().text().contains(quote))
                    throw validationFailure(documentId, extractionVersion, anchorId, "server_anchor_not_in_source", index++);
                if (!ids.add(evidenceId + ":" + definitionId))
                    throw validationFailure(documentId, extractionVersion, anchorId, "duplicate_definition_id", index++);
                JsonNode fields = item.path("fields");
                List<String> unsupported = new ArrayList<>();
                if (item.path("unsupportedFields").isArray()) item.path("unsupportedFields").forEach(value -> {
                    if (value.isTextual() && !value.asText().isBlank()) unsupported.add(value.asText());
                });
                result.add(new RulebookActionDefinition(expectedActionId == null ? evidenceId + ":" + definitionId : expectedActionId, required(item, "name"),
                        required(item, "actionKind"), anchor.evidence().locator(), evidenceId, quote,
                        mapper.convertValue(fields, mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)), unsupported));
                index++;
            }
            log("dev_rulebook_action_ai stage=response documentId={} extractionVersion={} operationKey={} expectedShape=definitions_array actualShape=definitions_array definitionCount={} evidenceIds={} elapsedMs={} outcome=completed",
                    documentId, extractionVersion, operation, result.size(), result.stream().map(RulebookActionDefinition::sourceChunkId).toList(), elapsed(started));
            return List.copyOf(result);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("rulebook action extraction was interrupted", interrupted);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("rulebook action extraction request failed", failure);
        }
    }

    private ObjectNode input(UUID documentId, String extractionVersion, List<RulebookActionEvidence> evidence,
            List<RulebookActionQueryCoverage> coverage) {
        ObjectNode input = mapper.createObjectNode();
        input.put("documentId", documentId.toString()); input.put("extractionVersion", extractionVersion);
        ArrayNode queries = input.putArray("queryCoverage");
        coverage.forEach(item -> queries.addObject().put("queryId", item.queryId())
                .put("queryFingerprint", item.queryFingerprint()).put("candidateCount", item.candidateCount()));
        ArrayNode sources = input.putArray("evidence");
        evidence.forEach(item -> {
            ObjectNode source = sources.addObject().put("id", item.id()).put("locator", item.locator());
            ArrayNode spans = source.putArray("anchors");
            int offset = anchorOffset(evidence, item);
            List<SentenceSpan> itemSpans = sentenceSpans(item.text());
            for (int index = 0; index < itemSpans.size(); index++) spans.addObject()
                    .put("id", Integer.toString(offset + index)).put("text", itemSpans.get(index).text());
        });
        return input;
    }

    private ObjectNode outputSchema(List<RulebookActionEvidence> evidence, String expectedActionId, String expectedName, String expectedKind) {
        ObjectNode schema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putArray("required").add("definitions");
        ObjectNode definitions = schema.putObject("properties").putObject("definitions").put("type", "array");
        if (expectedActionId != null) definitions.put("minItems", 1).put("maxItems", 1);
        ObjectNode item = definitions.putObject("items").put("type", "object").put("additionalProperties", false);
        item.putArray("required").add("id").add("name").add("actionKind").add("sourceAnchorId").add("unsupportedFields").add("fields");
        ObjectNode properties = item.putObject("properties");
        ObjectNode id = properties.putObject("id").put("type", "string").put("minLength", 1).put("maxLength", 120);
        ObjectNode nameSchema = properties.putObject("name").put("type", "string").put("minLength", 1).put("maxLength", 200);
        if (expectedActionId != null) id.putArray("enum").add(expectedActionId);
        if (expectedName != null) nameSchema.putArray("enum").add(expectedName);
        ObjectNode actionKind = properties.putObject("actionKind").put("type", "string");
        if (expectedKind == null) actionKind.putArray("enum").add("SPELL").add("RANGED_ATTACK").add("THROWN");
        else actionKind.putArray("enum").add(expectedKind);
        putAnchorEnum(properties.putObject("sourceAnchorId").put("type", "string").put("pattern", "^[0-9]{1,6}$"), anchorIds(evidence, null));
        properties.putObject("unsupportedFields").put("type", "array").putObject("items").put("type", "string");
        ObjectNode fields = properties.putObject("fields").put("type", "object").put("additionalProperties", false);
        ArrayNode required = fields.putArray("required");
        ObjectNode values = fields.putObject("properties");
        List<String> strings = List.of("level", "castingTime", "cost", "range", "rangeForm", "components", "duration", "school",
                "conditions", "attackSave", "damageEffect", "effect", "targetMode", "originMode", "effectShape", "pathBlocking", "propagation", "verticalDirection");
        for (String name : strings) { required.add(name); values.putObject(name).putArray("type").add("string").add("null"); }
        for (String name : List.of("normalRangeFeet", "longRangeFeet", "lengthFeet", "widthFeet", "heightFeet", "radiusFeet")) {
            required.add(name); values.putObject(name).putArray("type").add("integer").add("null");
        }
        ((ObjectNode) values.path("rangeForm")).putArray("enum").add("SINGLE").add("NORMAL_AND_LONG").add("UNSUPPORTED").addNull();
        Map<String, List<String>> enums = Map.of(
                "targetMode", List.of("SINGLE_TARGET", "MULTIPLE_TARGETS", "POINT", "DIRECTION", "SELF", "UNSUPPORTED"),
                "originMode", List.of("ACTOR_TOKEN", "TARGET", "CURSOR_POINT", "CURSOR_DIRECTION", "UNSUPPORTED"),
                "effectShape", List.of("NONE", "CONE", "LINE", "CUBE", "SPHERE", "CYLINDER", "WALL", "UNSUPPORTED"),
                "pathBlocking", List.of("SOLID_BLOCKS", "CLEAR_PATH_REQUIRED", "NONE", "UNSUPPORTED"),
                "propagation", List.of("DIRECT", "AREA_FROM_POINT", "CONTIGUOUS", "NONE", "UNSUPPORTED"),
                "verticalDirection", List.of("UP", "DOWN", "UNSUPPORTED"));
        enums.forEach((name, allowed) -> { ArrayNode valuesEnum = ((ObjectNode) values.path(name)).putArray("enum"); allowed.forEach(valuesEnum::add); valuesEnum.addNull(); });
        return schema;
    }

    private static void putAnchorEnum(ObjectNode schema, List<String> ids) {
        ArrayNode allowed = schema.putArray("enum");
        ids.forEach(allowed::add);
    }

    private static List<String> anchorIds(List<RulebookActionEvidence> evidence, List<String> evidenceIds) {
        Set<String> allowedEvidence = evidenceIds == null ? null : Set.copyOf(evidenceIds);
        return anchors(evidence).entrySet().stream()
                .filter(entry -> allowedEvidence == null || allowedEvidence.contains(entry.getValue().evidence().id()))
                .map(Map.Entry::getKey).toList();
    }

    private static Map<String, EvidenceAnchor> anchors(List<RulebookActionEvidence> evidence) {
        Map<String, EvidenceAnchor> result = new LinkedHashMap<>();
        int index = 0;
        for (RulebookActionEvidence item : evidence)
            for (SentenceSpan span : sentenceSpans(item.text())) result.put(Integer.toString(index++), new EvidenceAnchor(item, span.text()));
        return result;
    }

    private static int anchorOffset(List<RulebookActionEvidence> evidence, RulebookActionEvidence target) {
        int offset = 0;
        for (RulebookActionEvidence item : evidence) {
            if (item.id().equals(target.id())) return offset;
            offset += sentenceSpans(item.text()).size();
        }
        throw new IllegalArgumentException("evidence anchor target is outside its input");
    }

    private static List<SentenceSpan> sentenceSpans(String text) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(java.util.Locale.ROOT);
        iterator.setText(text);
        List<SentenceSpan> result = new ArrayList<>();
        int start = iterator.first();
        int index = 0;
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end);
            for (String part : splitBounded(sentence, 500)) {
                String trimmed = part.strip();
                if (!trimmed.isEmpty()) result.add(new SentenceSpan(index++, trimmed));
            }
        }
        if (result.isEmpty() && !text.isBlank()) result.add(new SentenceSpan(0, text.strip()));
        return List.copyOf(result);
    }

    private static List<String> splitBounded(String text, int maxLength) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxLength);
            if (end < text.length()) {
                int boundary = text.lastIndexOf(' ', end);
                int newline = text.lastIndexOf('\n', end);
                boundary = Math.max(boundary, newline);
                if (boundary > start) end = boundary;
            }
            String part = text.substring(start, end);
            if (!part.isBlank()) result.add(part);
            start = end;
        }
        return result;
    }

    private record SentenceSpan(int index, String text) { }
    private record EvidenceAnchor(RulebookActionEvidence evidence, String text) { }

    private IllegalArgumentException validationFailure(UUID documentId, String version, String evidenceId, String kind, int index) {
        log("dev_rulebook_action_ai stage=output_validation_failed documentId={} extractionVersion={} expectedEvidenceFromInput=true actualEvidenceId={} definitionIndex={} failureKind={} outcome=failed",
                documentId, version, evidenceId, index, kind);
        return new IllegalArgumentException("definition extraction output failed evidence validation: " + kind);
    }

    private static String required(JsonNode node, String name) {
        String value = node.path(name).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("definition extraction response has a blank " + name);
        return value;
    }
    private static List<String> responseTextValues(JsonNode array, String field) {
        List<String> result = new ArrayList<>();
        if (array != null && array.isArray()) array.forEach(item -> { if (item.path(field).isTextual()) result.add(item.path(field).asText()); });
        return List.copyOf(result);
    }
    private static String shape(JsonNode node) { return node == null ? "null" : node.isObject() ? "object:" + String.join(",", iterable(node.fieldNames())) : node.getNodeType().name(); }
    private static List<String> iterable(java.util.Iterator<String> values) { List<String> result = new ArrayList<>(); values.forEachRemaining(result::add); return result; }
    private static String fingerprint(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static long elapsed(long started) { return Duration.ofNanos(System.nanoTime() - started).toMillis(); }
    private List<String> safeTopLevelFields(String body) {
        try {
            JsonNode node = mapper.readTree(body);
            List<String> fields = new ArrayList<>();
            if (node != null && node.isObject()) node.fieldNames().forEachRemaining(fields::add);
            return List.copyOf(fields);
        } catch (Exception ignored) { return List.of(); }
    }
    private String safeErrorCode(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode code = root.path("code").isTextual() ? root.path("code") : root.path("error").path("code");
            return code.isTextual() && code.asText().matches("[A-Za-z0-9_.-]{1,80}") ? code.asText() : "";
        } catch (Exception ignored) { return ""; }
    }
    private static void log(String pattern, Object... args) {
        if (Boolean.parseBoolean(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED"))))
            LOGGER.info(pattern, args);
    }
}

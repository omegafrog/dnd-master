package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderRequest;
import com.dndmaster.aigamemaster.infrastructure.ai.EffectiveGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionResult;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateLifecycleResult;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderSelectionUnresolvedException;
import com.dndmaster.aigamemaster.infrastructure.ai.RequestedGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException;
import com.dndmaster.aigamemaster.application.rule.GmCitationBinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provider-neutral read-only GM loop. No tool calls or state mutations are exposed. */
@RestController
public final class GmAgentController {
    private static final Logger LOGGER = LoggerFactory.getLogger(GmAgentController.class);
    private final GmCompletionAdapter adapter;
    private final ObjectMapper mapper;
    private final ApiRequestGuard requestGuard;

    public GmAgentController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard) {
        this.adapter = adapter;
        this.mapper = mapper;
        this.requestGuard = requestGuard;
    }

    @PostMapping("/internal/v2/gm/agent-turns")
    V2Response planV2(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                      @RequestBody V2Request request) {
        requestGuard.internal(token);
        if (request == null || request.action() == null || request.action().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action required");
        }
        if (request.requestedSelection() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestedSelection required");
        }
        try {
            CitationCatalog catalog = citationCatalog(request);
            GmCandidateLifecycleResult<Response> lifecycle = adapter.completeWithOneRepair(
                    request.operationKey(), prompt(request, catalog),
                    repairContext -> repairPrompt(request, repairContext, catalog),
                    new com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseContract<>(gmOutputSchema(request, catalog), json -> validateCandidate(request, parseStrictCompleteResponse(request, catalog, json))),
                    request.requestedSelection());
            GmCompletionResult<Response> completion = lifecycle.completion();
            return new V2Response(completion.response(), RequestedSelection.from(request.requestedSelection()),
                    EffectiveSelection.from(completion.effectiveSelection()), lifecycle.attemptCount(), List.of());
        } catch (GmProviderSelectionUnresolvedException unresolved) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, unresolved.code());
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateValidationException invalid) {
            LOGGER.warn("gm_candidate_validation_failed stage=REPAIR violations={}", invalid.violations());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GM provider unavailable", invalid);
        } catch (ProviderMalformedResponseException malformed) {
            LOGGER.warn("gm_provider_malformed_response message={}", malformed.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GM provider unavailable", malformed);
        } catch (RuntimeException providerFailure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GM provider unavailable", providerFailure);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode gmOutputSchema(V2Request request, CitationCatalog catalog) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode schema = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree("{\"type\":\"object\",\"required\":[\"scene\",\"judgment\",\"narration\",\"npcState\",\"proposedActiveSourceContext\",\"citedEvidence\",\"warnings\",\"toolCalls\",\"advanceStoryPlan\",\"selectedBranchId\"],\"properties\":{\"scene\":{\"type\":\"string\"},\"judgment\":{\"type\":\"string\"},\"narration\":{\"type\":\"string\"},\"npcState\":{\"type\":\"string\"},\"proposedActiveSourceContext\":{\"anyOf\":[{\"type\":\"object\",\"properties\":{\"knowledgeDocumentId\":{\"type\":\"string\"},\"extractionVersion\":{\"type\":\"integer\"},\"locator\":{\"type\":\"string\"},\"excerpt\":{\"type\":\"string\"}},\"required\":[\"knowledgeDocumentId\",\"extractionVersion\",\"locator\",\"excerpt\"],\"additionalProperties\":false},{\"type\":\"null\"}]},\"citedEvidence\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"type\":{\"type\":\"string\"},\"knowledgeDocumentId\":{\"type\":\"string\"},\"extractionVersion\":{\"type\":\"integer\"},\"locator\":{\"type\":\"string\"},\"excerpt\":{\"type\":\"string\"}},\"required\":[\"type\",\"knowledgeDocumentId\",\"extractionVersion\",\"locator\",\"excerpt\"],\"additionalProperties\":false}},\"warnings\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"toolCalls\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"toolName\":{\"type\":\"string\"},\"arguments\":{\"type\":\"object\",\"properties\":{\"scope\":{\"type\":\"string\"},\"count\":{\"type\":\"integer\"},\"sides\":{\"type\":\"integer\"},\"modifier\":{\"type\":\"integer\"}},\"required\":[\"scope\",\"count\",\"sides\",\"modifier\"],\"additionalProperties\":false},\"required\":{\"type\":\"boolean\"}},\"required\":[\"toolName\",\"arguments\",\"required\"],\"additionalProperties\":false}},\"advanceStoryPlan\":{\"type\":\"boolean\"},\"selectedBranchId\":{\"type\":\"string\"}},\"additionalProperties\":false}");
            com.fasterxml.jackson.databind.node.ObjectNode properties = (com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties");
            for (String field : java.util.List.of("scene", "judgment", "narration")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) properties.path(field)).put("minLength", 1);
            }
            com.fasterxml.jackson.databind.node.ArrayNode requiredFields = (com.fasterxml.jackson.databind.node.ArrayNode) schema.path("required");
            for (int i = requiredFields.size() - 1; i >= 0; i--) {
                if ("citedEvidence".equals(requiredFields.get(i).asText())) requiredFields.remove(i);
            }
            requiredFields.add("citationIds");
            com.fasterxml.jackson.databind.node.ObjectNode props = (com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties");
            props.remove("citedEvidence");
            com.fasterxml.jackson.databind.node.ObjectNode citationIds = props.putObject("citationIds");
            citationIds.put("type", "array");
            com.fasterxml.jackson.databind.node.ObjectNode items = citationIds.putObject("items");
            items.put("type", "string");
            if (!catalog.aliases().isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode aliases = items.putArray("enum");
                catalog.aliases().forEach(aliases::add);
            }
            if (request.storybook() != null && !request.storybook().isEmpty()) citationIds.put("minItems", 1);
            com.fasterxml.jackson.databind.node.ObjectNode toolCalls = (com.fasterxml.jackson.databind.node.ObjectNode) props.path("toolCalls");
            java.util.List<String> allowedTools = toolNames(request.tools());
            if (allowedTools.isEmpty()) {
                toolCalls.put("maxItems", 0);
            } else {
                com.fasterxml.jackson.databind.node.ObjectNode toolItem = (com.fasterxml.jackson.databind.node.ObjectNode) toolCalls.path("items");
                com.fasterxml.jackson.databind.node.ObjectNode toolProperties = (com.fasterxml.jackson.databind.node.ObjectNode) toolItem.path("properties");
                com.fasterxml.jackson.databind.node.ObjectNode toolName = (com.fasterxml.jackson.databind.node.ObjectNode) toolProperties.path("toolName");
                com.fasterxml.jackson.databind.node.ArrayNode toolEnum = toolName.putArray("enum");
                allowedTools.forEach(toolEnum::add);
            }
            return schema;
        }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    @PostMapping("/internal/v1/gm/agent-turns")
    Response plan(@RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody Request request) {
        requestGuard.internal(token);
        if (request == null || request.action() == null || request.action().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action required");
        }
        String operation = request.operationKey();
        try {
            return canonicalizeProviderMetadata(request, complete(request, operation, prompt(request)));
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException malformed) {
            try {
                return canonicalizeProviderMetadata(request, complete(request, operation + ":repair", repairPrompt(request)));
            } catch (RuntimeException stillMalformed) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "GM provider structured response unavailable: " + stillMalformed.getMessage(), stillMalformed);
            }
        } catch (RuntimeException providerFailure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GM provider unavailable: " + providerFailure.getMessage(), providerFailure);
        }
    }

    private static Response canonicalizeProviderMetadata(Request request, Response response) {
        if (request.provider() == null || request.provider().isBlank()) return response;
        return new Response(response.scene(), response.npcState(), response.judgment(), response.narration(),
                response.proposedActiveSourceContext(), response.citedEvidence(), response.warnings(),
                request.provider(), request.model(), request.reasoning(), response.stateDelta(), response.toolCalls(),
                response.advanceStoryPlan(), response.selectedBranchId(), response.citationBindings());
    }

    private Response complete(Request request, String operation, String prompt) {
        if (request.provider() == null || request.provider().isBlank()) {
            return adapter.complete(operation, prompt, json -> parseCompleteResponse(request, json));
        }
        return adapter.complete(operation, prompt, json -> parseCompleteResponse(request, json),
                new GmProviderRequest(request.provider(), request.model(), request.reasoning()));
    }

    private Response parseCompleteResponse(Request request, String json) {
            try {
                // Luna occasionally emits an empty object for the read-only state
                // delta and a structured object for npcState/advanceStoryPlan.
                // Normalize those representation-only variants before applying the
                // canonical contract; non-empty state deltas remain rejected.
                com.fasterxml.jackson.databind.node.ObjectNode normalized =
                        (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(json);
                com.fasterxml.jackson.databind.JsonNode npcState = normalized.get("npcState");
                if (npcState != null && !npcState.isTextual() && !npcState.isNull()) {
                    normalized.put("npcState", mapper.writeValueAsString(npcState));
                }
                normalizeArrayField(normalized, "stateDelta");
                normalizeArrayField(normalized, "toolCalls");
                // Tool calls from the free-form provider are not executable until a
                // typed command is explicitly submitted; discard malformed calls here.
                normalized.putArray("toolCalls");
                normalizeArrayField(normalized, "citedEvidence");
                normalizeArrayField(normalized, "warnings");
                com.fasterxml.jackson.databind.JsonNode citations = normalized.get("citedEvidence");
                if (citations.isArray() && java.util.stream.StreamSupport.stream(citations.spliterator(), false)
                        .anyMatch(item -> !validCitation(item))) {
                    normalized.putArray("citedEvidence");
                    ((com.fasterxml.jackson.databind.node.ArrayNode) normalized.get("warnings"))
                            .add("Provider citation format was invalid; unsupported citations were discarded.");
                }
                com.fasterxml.jackson.databind.JsonNode active = normalized.get("proposedActiveSourceContext");
                if (active != null && !active.isObject()) {
                    normalized.putNull("proposedActiveSourceContext");
                } else if (active != null && active.isObject()
                        && (active.path("knowledgeDocumentId").isMissingNode()
                        || active.path("locator").asText().isBlank()
                        || active.path("excerpt").asText().isBlank())) {
                    normalized.putNull("proposedActiveSourceContext");
                }
                com.fasterxml.jackson.databind.JsonNode advancePlan = normalized.get("advanceStoryPlan");
                if (advancePlan == null || advancePlan.isNull() || !advancePlan.isBoolean()) {
                    normalized.put("advanceStoryPlan", false);
                }
                if (!normalized.path("advanceStoryPlan").asBoolean(false)
                        || !knownBranch(request.storyPlanContext(), normalized.path("selectedBranchId").asText())) {
                    normalized.put("advanceStoryPlan", false);
                    normalized.put("selectedBranchId", "");
                }
                Response response = mapper.treeToValue(normalized, Response.class);
                if (response.proposedActiveSourceContext() instanceof String source
                        && source.isBlank()) {
                    response = new Response(response.scene(), response.npcState(), response.judgment(),
                            response.narration(), null, response.citedEvidence(), response.warnings(),
                            response.provider(), response.model(), response.reasoning(), response.stateDelta(),
                            response.toolCalls(), response.advanceStoryPlan(), response.selectedBranchId(), response.citationBindings());
                }
                return requireComplete(response, toolNames(request.tools()));
            } catch (Exception exception) {
                throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException(
                        "GM structured response invalid: " + exception.getMessage());
            }
    }

    private static void normalizeArrayField(com.fasterxml.jackson.databind.node.ObjectNode node, String field) {
        com.fasterxml.jackson.databind.JsonNode value = node.get(field);
        if (value != null && (value.isNull() || (value.isTextual() && value.asText().isBlank())
                || (value.isObject() && value.size() == 0))) {
            node.putArray(field);
        }
    }

    private static boolean knownBranch(String storyPlanContext, String selectedBranchId) {
        if (storyPlanContext == null || storyPlanContext.isBlank()) return selectedBranchId == null || selectedBranchId.isBlank();
        String marker = "availableBranches=";
        int start = storyPlanContext.indexOf(marker);
        if (start < 0) return selectedBranchId == null || selectedBranchId.isBlank();
        String listed = storyPlanContext.substring(start + marker.length());
        int end = listed.indexOf(';');
        if (end >= 0) listed = listed.substring(0, end);
        if (listed.isBlank()) return selectedBranchId == null || selectedBranchId.isBlank();
        return java.util.Arrays.stream(listed.split(","))
                .map(String::trim)
                .anyMatch(selectedBranchId::equals);
    }

    private Response parseStrictCompleteResponse(V2Request request, CitationCatalog catalog, String json) {
        com.fasterxml.jackson.databind.JsonNode node;
        try {
            node = mapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new ProviderMalformedResponseException("GM candidate malformed: candidate must be a JSON object");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ProviderMalformedResponseException("GM candidate malformed: " + safeMessage(exception));
        }
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            com.fasterxml.jackson.databind.JsonNode ids = objectNode.get("citationIds");
            if (ids != null && !ids.isNull()) {
                if (!ids.isArray()) throw unknownCitation("citationIds", "citationIds must be an array");
                com.fasterxml.jackson.databind.node.ArrayNode hydrated = objectNode.putArray("citedEvidence");
                for (com.fasterxml.jackson.databind.JsonNode id : ids) {
                    if (!id.isTextual() || catalog.resolve(id.asText()) == null) {
                        throw unknownCitation(id.isTextual() ? id.asText() : String.valueOf(id), "unknown citation alias");
                    }
                    hydrated.add(mapper.valueToTree(catalog.resolve(id.asText())));
                }
            }
            objectNode.remove("citationIds");
            if (!objectNode.has("citedEvidence") || objectNode.get("citedEvidence").isNull()) objectNode.putArray("citedEvidence");
            if (!objectNode.has("warnings") || objectNode.get("warnings").isNull()) objectNode.putArray("warnings");
            if (!objectNode.has("toolCalls") || objectNode.get("toolCalls").isNull()) objectNode.putArray("toolCalls");
            if (!objectNode.has("stateDelta") || objectNode.get("stateDelta").isNull()) objectNode.putArray("stateDelta");
            com.fasterxml.jackson.databind.JsonNode active = objectNode.get("proposedActiveSourceContext");
            if (active != null && !active.isNull()
                    && (!active.isObject()
                    || active.path("knowledgeDocumentId").isMissingNode()
                    || active.path("knowledgeDocumentId").isNull()
                    || !validUuid(active.path("knowledgeDocumentId").asText())
                    || active.path("extractionVersion").isMissingNode()
                    || !active.path("extractionVersion").canConvertToLong()
                    || !active.path("locator").isTextual() || active.path("locator").asText().isBlank()
                    || !active.path("excerpt").isTextual() || active.path("excerpt").asText().isBlank())) {
                objectNode.putNull("proposedActiveSourceContext");
            }
        }
        final Response response;
        try {
            response = requireComplete(mapper.treeToValue(node, Response.class), toolNames(request.tools()));
        } catch (Exception exception) {
            java.util.List<String> missing = new java.util.ArrayList<>();
            for (String field : java.util.List.of("scene", "judgment", "narration")) {
                if (!node.has(field) || node.get(field).isNull()) missing.add(field);
            }
            String detail = missing.isEmpty() ? safeMessage(exception) : "missing required GM fields: " + String.join(", ", missing);
            String code = detail.contains("malformed GM tool call") ? "GM_TOOL_CALL_MALFORMED"
                    : (detail.contains("unsupported GM tool call") ? "GM_TOOL_UNKNOWN" : "GM_REQUIRED_FIELD_MISSING");
            String field = code.startsWith("GM_TOOL_") ? "toolCalls" : (missing.isEmpty() ? "candidate" : missing.get(0));
            LOGGER.warn("gm_tool_call_contract_failed stage=PARSE toolCallShape={} allowedTools={}",
                    node.path("toolCalls").toString(), toolNames(request.tools()));
            throw new com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateValidationException(List.of(
                    new com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateViolation(
                            code, field, detail)));
        }
        return response;
    }

    private static com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateValidationException unknownCitation(String alias, String message) {
        return new com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateValidationException(List.of(
                new com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateViolation(
                        "UNKNOWN_CITATION_ID", "citationIds", message + ": " + alias)));
    }

    private static CitationCatalog citationCatalog(V2Request request) {
        return CitationCatalog.from(request.storybook(), request.rulebook(), request.resolution());
    }

    private static boolean validUuid(String value) {
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private Response validateCandidate(V2Request request, Response response) {
        if (!request.storybook().isEmpty() && response.citedEvidence().isEmpty()) {
            throw new com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateValidationException(List.of(
                    new com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateViolation(
                            "STORYBOOK_CITATION_REQUIRED", "citedEvidence", "Storybook citation required")));
        }
        List<?> allowed = java.util.stream.Stream.of(request.storybook(), request.rulebook(), request.resolution())
                .flatMap(List::stream).toList();
        List<Object> canonicalCitations = new java.util.ArrayList<>();
        for (Object citation : response.citedEvidence()) {
            Object canonical = canonicalCitation(citation, allowed);
            if (canonical == null) {
                throw new com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateValidationException(List.of(
                        new com.dndmaster.aigamemaster.infrastructure.ai.GmCandidateViolation(
                                "CITATION_OUTSIDE_EVIDENCE_PACK", "citedEvidence",
                                "Citation identity is outside the Evidence Pack: " + citationIdentity(citation))));
            }
            canonicalCitations.add(canonical);
        }
        return new Response(response.scene(), response.npcState(), response.judgment(), response.narration(),
                response.proposedActiveSourceContext(), canonicalCitations, response.warnings(), response.provider(),
                response.model(), response.reasoning(), response.stateDelta(), response.toolCalls(),
                response.advanceStoryPlan(), response.selectedBranchId(), response.citationBindings());
    }

    private Object canonicalCitation(Object citation, List<?> allowed) {
        if (allowed.contains(citation)) return citation;
        java.util.Map<?, ?> candidate = asMap(citation);
        if (candidate == null) return null;
        List<?> matches = allowed.stream().filter(item -> {
            java.util.Map<?, ?> evidence = asMap(item);
            return evidence != null && sameReference(candidate, evidence);
        }).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private java.util.Map<?, ?> asMap(Object value) {
        if (value instanceof java.util.Map<?, ?> map) return map;
        try { return mapper.convertValue(value, java.util.Map.class); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static boolean sameReference(java.util.Map<?, ?> left, java.util.Map<?, ?> right) {
        return sameValue(left, right, "knowledgeDocumentId")
                && sameValue(left, right, "extractionVersion")
                && sameValue(left, right, "locator")
                && (left.get("type") == null || sameValue(left, right, "type"));
    }

    private static String citationIdentity(Object citation) {
        if (!(citation instanceof java.util.Map<?, ?> map)) return String.valueOf(citation);
        return "type=" + map.get("type") + ", knowledgeDocumentId=" + map.get("knowledgeDocumentId")
                + ", extractionVersion=" + map.get("extractionVersion") + ", locator=" + map.get("locator");
    }

    private static boolean sameValue(java.util.Map<?, ?> left, java.util.Map<?, ?> right, String key) {
        Object leftValue = left.get(key);
        Object rightValue = right.get(key);
        return leftValue != null && rightValue != null && String.valueOf(leftValue).equals(String.valueOf(rightValue));
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "required candidate fields are missing";
        return message.replaceAll("[\\r\\n]", " ").replaceAll("[0-9a-fA-F-]{24,}", "<redacted>");
    }

    private static boolean validCitation(com.fasterxml.jackson.databind.JsonNode item) {
        if (item == null || !item.isObject()
                || !item.path("type").isTextual() || item.path("type").asText().isBlank()
                || !supportedEvidenceType(item.path("type").asText())
                || !item.hasNonNull("knowledgeDocumentId")
                || !item.path("extractionVersion").canConvertToLong()
                || !item.path("locator").isTextual() || item.path("locator").asText().isBlank()
                || !item.path("excerpt").isTextual() || item.path("excerpt").asText().isBlank()) return false;
        try { java.util.UUID.fromString(item.path("knowledgeDocumentId").asText()); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private static boolean supportedEvidenceType(String value) {
        return "STORYBOOK".equals(value) || "RULEBOOK".equals(value) || "RESOLUTION".equals(value);
    }

    private static List<String> toolNames(List<?> tools) {
        if (tools == null) return List.of();
        return tools.stream().map(value -> {
            if (value instanceof String name) return name;
            if (value instanceof java.util.Map<?, ?> map) {
                Object name = map.get("name");
                if (name == null) name = map.get("toolName");
                return name == null ? "" : String.valueOf(name);
            }
            return "";
        }).filter(name -> !name.isBlank()).distinct().toList();
    }

    private static String repairPrompt(Request r) {
        return """
                Return exactly one JSON object and no markdown.
                Required keys: scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings, provider, model, reasoning, stateDelta, toolCalls.
                Use non-null strings for scene, judgment, narration; use [] for all arrays and null for proposedActiveSourceContext.
                Do not make rule claims or invent facts. The player action is: %s
                Current scene: %s
                """.formatted(r.action(), r.currentScene());
    }

    private static String repairPrompt(V2Request request, com.dndmaster.aigamemaster.infrastructure.ai.GmRepairContext repair, CitationCatalog catalog) {
        return """
                Return exactly one repaired JSON object and no markdown.
                Required keys: scene, npcState, judgment, narration, proposedActiveSourceContext, citationIds, warnings, toolCalls, advanceStoryPlan, selectedBranchId.
                Do not add semantic defaults, neutral narration, neutral judgment, or guessed citations.
                Only change fields implicated by the supplied violations. Preserve every other valid field from initialCandidate exactly.
                Never add a tool call unless a supplied violation explicitly targets toolCalls. Any tool call must use an allowed tool name and its exact supplied schema.
                citationIds must contain only aliases from the supplied catalog. Never invent or rewrite evidence identity;
                the server resolves aliases to canonical Evidence Pack objects.
                Preserve the same requested provider selection and the same Evidence Pack below.
                initialCandidate=%s
                violations=%s
                storybook=%s rulebook=%s resolution=%s
                playerAction=%s currentScene=%s
                allowedTools=%s
                %s
                """.formatted(repair.rawResponse(), repair.violations(), request.storybook(), request.rulebook(), request.resolution(),
                request.action(), request.currentScene(), request.tools(), catalog.promptDescription());
    }

    @PostMapping("/internal/v1/gm/context-compactions")
    CompactionResponse compact(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                               @RequestBody CompactionRequest request) {
        requestGuard.internal(token);
        if (request == null || request.context() == null || request.context().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "context required");
        }
        return adapter.complete("context-compaction:" + request.sessionId() + ":" + request.sourceTurnId(), compactionPrompt(request), json -> {
            try {
                CompactionResponse response = mapper.readValue(json, CompactionResponse.class);
                if (response.summary() == null || response.summary().isBlank()
                        || response.unresolvedThreats() == null || response.planRevisionId() == null || response.planVersion() < 1) {
                    throw new IllegalArgumentException("summary, threats, planRevisionId and planVersion required");
                }
                return response;
            } catch (Exception exception) {
                throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException(
                        "GM compaction response invalid: " + exception.getMessage());
            }
        });
    }

    @PostMapping("/internal/v1/gm/companion-candidates")
    CompanionCandidateResponse companionCandidate(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                                   @RequestBody CompanionCandidateRequest request) {
        requestGuard.internal(token);
        if (request == null || request.sessionId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId required");
        try {
            var parser = (com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<CompanionCandidateResponse>) json -> {
                try {
                    CompanionCandidateResponse result = mapper.readValue(json, CompanionCandidateResponse.class);
                    if (blank(result.name()) || blank(result.race()) || blank(result.characterClass()) || blank(result.sheetSummary())) {
                        throw new IllegalArgumentException("candidate fields required");
                    }
                    return result;
                } catch (Exception exception) { throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException("companion candidate invalid"); }
            };
            return request.provider() == null || request.provider().isBlank()
                    ? adapter.complete("companion-candidate:" + request.sessionId() + ":" + UUID.randomUUID(), companionPrompt(request), parser)
                    : adapter.complete("companion-candidate:" + request.sessionId() + ":" + UUID.randomUUID(), companionPrompt(request), parser,
                            new GmProviderRequest(request.provider(), request.model(), request.reasoning()));
        } catch (RuntimeException failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GM companion candidate unavailable", failure); }
    }

    @PostMapping("/internal/v1/gm/quality-evaluation")
    GmQualityEvaluationService.EvaluationReport evaluateQuality(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody List<GmQualityEvaluationService.Scenario> scenarios) {
        requestGuard.internal(token);
        return new GmQualityEvaluationService(adapter, mapper).evaluateReport(scenarios);
    }

    private static String compactionPrompt(CompactionRequest r) {
        return """
                SYSTEM: Compact GM context for internal resume only. Preserve canonical facts as references, not replacements.
                Return JSON only with summary, unresolvedThreats, planRevisionId, planVersion.
                Do not copy or rewrite exactTail. Do not invent state absent from context.
                sessionId=%s sourceTurnId=%s context=%s exactTail=%s snapshotReferences=%s
                """.formatted(r.sessionId(), r.sourceTurnId(), r.context(), r.exactTail(), r.snapshotReferences());
    }
    private static String companionPrompt(CompanionCandidateRequest request) {
        return """
                Return exactly one JSON object and no markdown with name,race,characterClass,sheetSummary.
                Create a legal D&D 5e (2014) level-1 fighter companion. Allowed races: 드워프, 엘프, 하플링, 인간.
                characterClass must be 파이터. Summary must be one concise Korean sentence.
                Do not use copyrighted character names. sessionId=%s
                """.formatted(request.sessionId());
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String prompt(Request r) {
        return """
                SYSTEM: You are "마르셀", a seasoned Korean tabletop RPG game master: warm, witty, vivid,
                and attentive to player agency. Speak like a real GM at the table, in natural conversational
                Korean. Address the party directly using polite but relaxed spoken language (합니다체와
                해요체를 자연스럽게 섞되 문어체 보고서처럼 쓰지 말 것). Never phrase a player-facing
                question as an instruction ending in "~해줘", "~하십시오", or "설명해줘". Ask naturally,
                for example: "문을 열어볼까요?", "어떻게 움직이시겠어요?", "누가 먼저 나설까요?"
                Do not steer the player toward one preferred action. Never end with a single suggested
                action such as "병을 치울까요?" or "문을 열어볼까요?". You may end with no question at all,
                or present at least two materially different options (including an open-ended option such as
                "다른 방법을 시도해도 좋습니다") without implying which one is best. When you present
                options, keep them separate from the scene narration using this exact spoken-chat format:
                first write the scene, then a blank line, then "선택지:", followed by a Markdown ordered list
                starting at 1. Never put numbered options inline in a paragraph, and never use bullets for them.
                You are a read-only game master. Use only supplied locked evidence and context.
                Never reveal hidden data. Never invent rules, rolls, or state changes.
                Return JSON only with fields scene,npcState,judgment,narration,proposedActiveSourceContext,
                citationIds,warnings,toolCalls,advanceStoryPlan,selectedBranchId. citationIds must use only aliases from the supplied catalog.
                toolCalls may contain only dice.roll or character.update; each call has toolName,argumentsJson,required.
                Every rule claim needs a citation from supplied evidence.
                Ground the turn in at least one supplied storybook item by selecting at least one STORYBOOK citation alias.
                The server resolves citationIds to canonical evidence; never emit opaque document IDs, locators, or excerpts.
                Preserve currentScene and unresolved facts across turns. Change scene only when the supplied storybook
                or resolution evidence establishes the transition. Do not end narration with a single player-facing
                recommendation; use multiple choices in a separate ordered list or simply leave the scene open
                for the player's response.
                Treat the player's action as the latest event in the fiction. Do not repeat a stale description as if
                nothing happened: if the player says they pick up, move, close, break, speak to, or otherwise affect
                an object or creature, acknowledge that attempt in the narration. When the outcome is uncertain,
                describe the attempt as pending and ask for the appropriate roll; when no roll is needed, describe
                the resulting state. The current scene must not claim that an object is still in its pre-action state
                after the player has acted on it. Keep the player's action and the GM's response in conversational
                spoken Korean, not as a request to "설명해줘" or a report.
                For story-plan advancement, advanceStoryPlan MUST be false unless the player explicitly completed a
                transition condition. If it is true, selectedBranchId MUST be copied exactly from a branch ID present
                in the supplied storyPlan; never invent branch IDs. If no valid branch ID is visible, keep it false.
                Available tools for this turn are listed below. For every tool call, argumentsJson MUST be a JSON
                object matching the exact inputSchema for that tool. Do not include server-owned identifiers such
                as adventureId, ruleSetId, sessionId, turnId, commandId, or expectedVersion.
                adventureId=%s packageId=%s bindingVersion=%s action=%s
                currentScene=%s npcState=%s pendingAction=%s latestJudgment=%s
                storybook=%s rulebook=%s resolution=%s recentTurns=%s characters=%s storyPlan=%s tools=%s
                """.formatted(r.adventureId(), r.scenarioPackageId(), r.bindingVersion(), r.action(), r.currentScene(),
                r.npcState(), r.pendingAction(), r.latestJudgment(), r.storybook(), r.rulebook(), r.resolution(), r.recentTurns(),
                r.characterSnapshots(), r.storyPlanContext(), r.tools());
    }

    private static String prompt(V2Request request, CitationCatalog catalog) {
        return prompt(request.toLegacyRequest()) + "\n" + catalog.promptDescription();
    }

    /** Request-scoped opaque citation aliases. The model selects aliases; the server owns evidence identity. */
    private static final class CitationCatalog {
        private final java.util.LinkedHashMap<String, Object> byAlias;

        private CitationCatalog(java.util.LinkedHashMap<String, Object> byAlias) {
            this.byAlias = byAlias;
        }

        static CitationCatalog from(List<?> storybook, List<?> rulebook, List<?> resolution) {
            java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
            add(values, "S", storybook);
            add(values, "R", rulebook);
            add(values, "X", resolution);
            return new CitationCatalog(values);
        }

        private static void add(java.util.Map<String, Object> values, String prefix, List<?> entries) {
            if (entries == null) return;
            for (int i = 0; i < entries.size(); i++) values.put(prefix + (i + 1), entries.get(i));
        }

        Object resolve(String alias) { return byAlias.get(alias); }
        List<String> aliases() { return List.copyOf(byAlias.keySet()); }
        String promptDescription() {
            String entries = byAlias.entrySet().stream().map(entry -> {
                String alias = entry.getKey();
                String category = alias.startsWith("S") ? "STORYBOOK" : alias.startsWith("R") ? "RULEBOOK" : "RESOLUTION";
                return alias + " (" + category + ")";
            }).collect(java.util.stream.Collectors.joining(", "));
            return "Citation catalog (select IDs only; do not output evidence objects): " + entries;
        }
    }

    public record Request(String operationKey, UUID adventureId, UUID ownerPlayerId, UUID sessionId, UUID turnId, UUID scenarioPackageId, long bindingVersion, String turnCapability, String action,
                          String currentScene, String npcState, String pendingAction, String latestJudgment,
                          List<?> storybook, List<?> rulebook, List<?> resolution, List<String> recentTurns,
                          List<String> characterSnapshots, String storyPlanContext, String provider, String model,
                          String reasoning, List<?> tools) {
        public Request(String operationKey, UUID adventureId, UUID ownerPlayerId, UUID sessionId, UUID turnId, UUID scenarioPackageId,
                       long bindingVersion, String turnCapability, String action, String currentScene, String npcState,
                       String pendingAction, String latestJudgment, List<?> storybook, List<?> rulebook, List<?> resolution,
                       List<String> recentTurns, List<String> characterSnapshots, String storyPlanContext,
                       String provider, String model, String reasoning) {
            this(operationKey, adventureId, ownerPlayerId, sessionId, turnId, scenarioPackageId, bindingVersion, turnCapability,
                    action, currentScene, npcState, pendingAction, latestJudgment, storybook, rulebook, resolution,
                    recentTurns, characterSnapshots, storyPlanContext, provider, model, reasoning, List.of());
        }
    }

    public record V2Request(String operationKey, UUID adventureId, UUID ownerPlayerId, UUID sessionId, UUID turnId,
                            UUID scenarioPackageId, long bindingVersion, String turnCapability, String action,
                            String currentScene, String npcState, String pendingAction, String latestJudgment,
                            List<?> storybook, List<?> rulebook, List<?> resolution, List<String> recentTurns,
                            List<String> characterSnapshots, String storyPlanContext,
                            RequestedGmProviderSelection requestedSelection, List<?> tools) {
        public V2Request(String operationKey, UUID adventureId, UUID ownerPlayerId, UUID sessionId, UUID turnId,
                         UUID scenarioPackageId, long bindingVersion, String turnCapability, String action,
                         String currentScene, String npcState, String pendingAction, String latestJudgment,
                         List<?> storybook, List<?> rulebook, List<?> resolution, List<String> recentTurns,
                         List<String> characterSnapshots, String storyPlanContext,
                         RequestedGmProviderSelection requestedSelection) {
            this(operationKey, adventureId, ownerPlayerId, sessionId, turnId, scenarioPackageId, bindingVersion,
                    turnCapability, action, currentScene, npcState, pendingAction, latestJudgment, storybook,
                    rulebook, resolution, recentTurns, characterSnapshots, storyPlanContext, requestedSelection, List.of());
        }
        Request toLegacyRequest() {
            return new Request(operationKey, adventureId, ownerPlayerId, sessionId, turnId, scenarioPackageId,
                    bindingVersion, turnCapability, action, currentScene, npcState, pendingAction, latestJudgment,
                    storybook, rulebook, resolution, recentTurns, characterSnapshots, storyPlanContext,
                    requestedSelection.provider(), requestedSelection.model(), requestedSelection.reasoning(), tools);
        }
    }

    public record V2Response(Response candidate, RequestedSelection requestedSelection,
                             EffectiveSelection effectiveSelection, int attemptCount,
                             List<GmCandidateViolation> violations) {
        public V2Response(Response candidate, RequestedSelection requestedSelection,
                          EffectiveSelection effectiveSelection, int attemptCount) {
            this(candidate, requestedSelection, effectiveSelection, attemptCount, List.of());
        }
    }

    public record RequestedSelection(UUID endpointId, String provider, String model, String reasoning) {
        static RequestedSelection from(RequestedGmProviderSelection value) {
            return new RequestedSelection(value.endpointId(), value.provider(), value.model(), value.reasoning());
        }
    }

    public record EffectiveSelection(UUID endpointId, java.time.Instant endpointVersion,
                                     String provider, String model, String reasoning) {
        static EffectiveSelection from(EffectiveGmProviderSelection value) {
            return new EffectiveSelection(value.endpointId(), value.endpointVersion(), value.provider(), value.model(), value.reasoning());
        }
    }
    public record CompanionCandidateRequest(UUID sessionId, String provider, String model, String reasoning) {}
    public record CompanionCandidateResponse(String name, String race, String characterClass, String sheetSummary) {}

    public record CompactionRequest(UUID sessionId, UUID sourceTurnId, String context, Object exactTail, Object snapshotReferences) {}

    public record CompactionResponse(String summary, List<String> unresolvedThreats, UUID planRevisionId, long planVersion) {}

    static Response requireComplete(Response response) {
        return requireComplete(response, java.util.List.of("dice.roll", "character.update"));
    }

    static Response requireComplete(Response response, java.util.Collection<String> allowedTools) {
        if (response == null || response.scene() == null || response.judgment() == null || response.narration() == null
                || response.citedEvidence() == null || response.warnings() == null) {
            throw new IllegalArgumentException("missing required GM fields: scene, judgment, narration, citedEvidence, warnings");
        }
        requireText(response.scene(), "scene");
        requireText(response.judgment(), "judgment");
        requireText(response.narration(), "narration");
        if (response.stateDelta() != null && !response.stateDelta().isEmpty()) throw new IllegalArgumentException("read-only GM state delta must be empty");
        if (response.toolCalls() != null) {
            for (int index = 0; index < response.toolCalls().size(); index++) {
                Response.ToolCall call = response.toolCalls().get(index);
                if (call == null || call.toolName() == null || call.toolName().isBlank() || call.argumentsJson() == null) {
                    throw new IllegalArgumentException("malformed GM tool call at toolCalls[" + index + "]");
                }
                if (!allowedTools.contains(call.toolName())) {
                    throw new IllegalArgumentException("unsupported GM tool call: " + call.toolName() + " allowed=" + allowedTools);
                }
            }
        }
        return response;
    }

    private static void requireText(String value, String name) {
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    public record Response(String scene, String npcState, String judgment, String narration, Object proposedActiveSourceContext,
                           List<?> citedEvidence, List<String> warnings, String provider, String model, String reasoning,
                           List<String> stateDelta, List<ToolCall> toolCalls, boolean advanceStoryPlan, String selectedBranchId,
                           List<GmCitationBinding> citationBindings) {
        public Response {
            citationBindings = citationBindings == null ? List.of() : List.copyOf(citationBindings);
        }

        public Response(String scene, String npcState, String judgment, String narration, Object proposedActiveSourceContext,
                        List<?> citedEvidence, List<String> warnings, String provider, String model, String reasoning,
                        List<String> stateDelta) {
            this(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings, provider, model, reasoning,
                    stateDelta, List.of(), false, "", List.of());
        }
        public record ToolCall(String toolName,
                               @com.fasterxml.jackson.annotation.JsonAlias("arguments") Object argumentsJson,
                               boolean required) {}
    }
}

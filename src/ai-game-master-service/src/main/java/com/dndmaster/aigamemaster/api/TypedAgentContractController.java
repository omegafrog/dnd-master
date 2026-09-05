package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role-specific, read-only AI boundaries. The AI process receives no tool,
 * database, filesystem, or generic HTTP capability through these contracts.
 */
@RestController
public final class TypedAgentContractController {
    private final GmCompletionAdapter adapter;
    private final ObjectMapper mapper;
    private final ApiRequestGuard requestGuard;

    @Autowired
    public TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper,
            @Value("${INTERNAL_SERVICE_TOKEN:typed-agent-local-token}") String internalToken) {
        this(adapter, mapper, new ApiRequestGuard(internalToken));
    }

    public TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard) {
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.requestGuard = Objects.requireNonNull(requestGuard, "request guard must not be null");
    }

    @PostMapping("/internal/gm/scenario-compilation")
    ScenarioCompilationResponse scenarioCompilation(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ScenarioCompilationRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete(request.operationKey(),
                "ROLE=SCENARIO_COMPILATION\nSTORYBOOK_CONTEXT=" + request.storybookContext(),
                json -> parseCompilation(json));
    }

    @PostMapping("/internal/gm/scenario-lookup")
    ScenarioLookupResponse scenarioLookup(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ScenarioLookupRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete("scenario-lookup:" + request.query(),
                "ROLE=SCENARIO_LOOKUP\nREAD_ONLY_LOCKED_SCENARIO_MODEL=" + write(request.lockedScenarioModel())
                        + "\nQUERY=" + request.query(),
                this::parseLookup);
    }

    @PostMapping("/internal/gm/runtime-turn")
    RuntimeTurnResponse runtimeTurn(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody RuntimeTurnRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete(request.operationKey(),
                "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=" + write(request.factLookupResults())
                        + "\nACTION=" + request.action()
                        + "\nOUTPUT_CONTRACT=Return exactly one JSON object with three non-empty string fields: "
                        + "scene, judgment, narration. Do not use markdown, code fences, or any other text.",
                this::parseRuntimeTurn);
    }

    @PostMapping("/internal/gm/narration-safety")
    NarrationSafetyResponse narrationSafety(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody NarrationSafetyRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete("narration-safety", "ROLE=NARRATION_SAFETY\nNARRATION=" + request.narration()
                        + "\nDISCLOSED_FACT_IDS=" + write(request.disclosedFactIds()), this::parseSafety);
    }

    private ScenarioCompilationResponse parseCompilation(String json) {
        JsonNode root = readObject(json);
        return new ScenarioCompilationResponse(required(root, "status"),
                root.path("scenarioModel").isObject()
                        ? mapper.convertValue(root.path("scenarioModel"), new TypeReference<Map<String, Object>>() { })
                        : Map.of());
    }

    private ScenarioLookupResponse parseLookup(String json) {
        JsonNode root = readObject(json);
        List<String> ids = root.has("supportingElementIds")
                ? mapper.convertValue(root.path("supportingElementIds"), mapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class))
                : List.of();
        String status = required(root, "status");
        if (!status.equals("FOUND") && !status.equals("NOT_FOUND")) throw new IllegalArgumentException("invalid lookup status");
        return new ScenarioLookupResponse(status, root.path("answer").asText(""), ids);
    }

    private RuntimeTurnResponse parseRuntimeTurn(String json) {
        JsonNode root = readObject(json);
        return new RuntimeTurnResponse(required(root, "scene"), required(root, "judgment"), required(root, "narration"));
    }

    private NarrationSafetyResponse parseSafety(String json) {
        JsonNode root = readObject(json);
        if (!root.has("approved") || !root.path("approved").isBoolean()) throw new IllegalArgumentException("approved is required");
        return new NarrationSafetyResponse(root.path("approved").booleanValue(), root.path("reason").asText(""));
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

    private static String required(JsonNode root, String field) {
        String value = root.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("typed request serialization failed", e); }
    }

    private static void require(Object request) {
        if (request == null) throw new IllegalArgumentException("typed agent request is required");
    }

    public record ScenarioCompilationRequest(String operationKey, String storybookContext) {
        public ScenarioCompilationRequest {
            operationKey = required(operationKey, "operationKey");
            storybookContext = required(storybookContext, "storybookContext");
        }
    }

    public record ScenarioLookupRequest(String query, Map<String, Object> lockedScenarioModel) {
        public ScenarioLookupRequest {
            query = required(query, "query");
            lockedScenarioModel = Map.copyOf(Objects.requireNonNull(lockedScenarioModel, "lockedScenarioModel is required"));
        }
    }

    public record RuntimeTurnRequest(String operationKey, String action, List<Map<String, Object>> factLookupResults) {
        public RuntimeTurnRequest {
            operationKey = required(operationKey, "operationKey");
            action = required(action, "action");
            factLookupResults = List.copyOf(Objects.requireNonNull(factLookupResults, "factLookupResults is required"));
        }
    }

    public record NarrationSafetyRequest(String narration, List<String> disclosedFactIds) {
        public NarrationSafetyRequest {
            narration = required(narration, "narration");
            disclosedFactIds = List.copyOf(Objects.requireNonNull(disclosedFactIds, "disclosedFactIds is required"));
        }
    }

    public record ScenarioCompilationResponse(String status, Map<String, Object> scenarioModel) { }
    public record ScenarioLookupResponse(String status, String answer, List<String> supportingElementIds) { }
    public record RuntimeTurnResponse(String scene, String judgment, String narration) { }
    public record NarrationSafetyResponse(boolean approved, String reason) { }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}

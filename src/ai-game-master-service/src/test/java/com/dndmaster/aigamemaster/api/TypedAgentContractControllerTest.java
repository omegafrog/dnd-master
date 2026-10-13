package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.aigamemaster.infrastructure.ai.EffectiveGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionResult;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderSelectionResolver;
import com.dndmaster.aigamemaster.infrastructure.ai.RequestedGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser;
import com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException;
import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(OutputCaptureExtension.class)
class TypedAgentContractControllerTest {
    private static final UUID SOLO_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000321");
    private static final UUID SELECTED_ENDPOINT_ID = UUID.fromString("00000000-0000-0000-0000-000000000322");

    @Test
    void scenario_compilation_validation_reason_logs_only_known_shape_errors() {
        org.junit.jupiter.api.Assertions.assertEquals("status is required",
                TypedAgentContractController.safeScenarioCompilationFailureReason(
                        new IllegalArgumentException("status is required")));
        org.junit.jupiter.api.Assertions.assertEquals("unclassified invalid response",
                TypedAgentContractController.safeScenarioCompilationFailureReason(
                        new IllegalArgumentException("private Storybook text")));
    }

    @Test
    void scenario_compilation_rejects_wrong_diagnostics_shape_with_safe_dev_log(CapturedOutput output) {
        String sentinel = "PRIVATE_DIAGNOSTICS_SENTINEL";
        var controller = new TypedAgentContractController(new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                return parser.parse("{\"status\":\"READY\",\"scenarioModel\":null,\"diagnostics\":\"" + sentinel + "\"}");
            }
        }, new ObjectMapper(), new ApiRequestGuard("service-secret"));
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        try {
            ResponseEntity<?> response = controller.scenarioCompilation("service-secret",
                    new TypedAgentContractController.ScenarioCompilationRequest(
                            SOLO_PLAYER_ID, "scenario-compilation:bad-diagnostics", "PRIVATE_STORYBOOK_SENTINEL"));

            org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
            org.junit.jupiter.api.Assertions.assertTrue(output.getAll().contains("path=diagnostics"));
            org.junit.jupiter.api.Assertions.assertTrue(output.getAll().contains("expected=array"));
            org.junit.jupiter.api.Assertions.assertTrue(output.getAll().contains("actual=string"));
            org.junit.jupiter.api.Assertions.assertTrue(output.getAll().contains("violationCount=2"));
            org.junit.jupiter.api.Assertions.assertTrue(output.getAll().contains("path=scenarioModel"));
            org.junit.jupiter.api.Assertions.assertFalse(output.getAll().contains(sentinel));
            org.junit.jupiter.api.Assertions.assertFalse(output.getAll().contains("PRIVATE_STORYBOOK_SENTINEL"));
        } finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }
    }

    @Test
    void scenario_compilation_accepts_valid_model_and_diagnostics() {
        var controller = new TypedAgentContractController(new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                return parser.parse("{\"status\":\"READY\",\"scenarioModel\":{\"schemaVersion\":1,\"actors\":[],\"locations\":[],\"objectives\":[],\"revelations\":[],\"encounters\":[],\"relationships\":[],\"resolutionCriteria\":[],\"startingSituation\":\"Opening\"},\"diagnostics\":[]}");
            }
        }, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        ResponseEntity<?> response = controller.scenarioCompilation("service-secret",
                new TypedAgentContractController.ScenarioCompilationRequest(
                        SOLO_PLAYER_ID, "scenario-compilation:valid", "source excerpt"));

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        var compilation = (TypedAgentContractController.ScenarioCompilationResponse) response.getBody();
        org.junit.jupiter.api.Assertions.assertEquals("READY", compilation.status());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), compilation.diagnostics());
    }

    @Test
    void changed_endpoint_snapshot_is_rejected_before_ai_execution() {
        AtomicReference<String> sent = new AtomicReference<>();
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> {
            sent.set(prompt);
            throw new AssertionError("AI must not be called");
        });
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(),
                new ApiRequestGuard("service-secret"), requested -> resolution("actual-model", requested));
        var request = new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "look",
                SELECTED_ENDPOINT_ID, "openai", "stale-model", "high", SELECTED_ENDPOINT_ID,
                Instant.EPOCH.toString(), "openai", "stale-model", "ROLE=RUNTIME_GM");
        assertThrows(ResponseStatusException.class, () -> controller.runtimeTurn("service-secret", request));
        org.junit.jupiter.api.Assertions.assertNull(sent.get());
    }

    @Test
    void combat_turn_contract_returns_structured_source_cited_action() {
        var controller = new TypedAgentContractController(selectedAdapter((operation, prompt, requested) ->
                "{\"kind\":\"ACTION\",\"actorId\":\"00000000-0000-0000-0000-000000000323\","
                        + "\"action\":\"Scimitar\",\"targetId\":\"00000000-0000-0000-0000-000000000324\","
                        + "\"citationKeys\":[\"goblin-scimitar\"]}"), new ObjectMapper(),
                new ApiRequestGuard("service-secret"), requested -> resolution("actual-model", requested));
        var request = new TypedAgentContractController.CombatTurnDecisionRequest(SOLO_PLAYER_ID, "combat-op",
                SELECTED_ENDPOINT_ID, "openai", "actual-model", "high", SELECTED_ENDPOINT_ID,
                Instant.EPOCH.toString(), "openai", "actual-model", "ROLE=COMBAT_TURN_DECISION");

        var result = controller.combatTurnDecision("service-secret", request);

        org.junit.jupiter.api.Assertions.assertEquals("ACTION", result.kind());
        org.junit.jupiter.api.Assertions.assertEquals("Scimitar", result.action());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("goblin-scimitar"), result.citationKeys());
    }

    @Test
    void combat_turn_end_requires_a_favorable_action_assessment_and_rule_citations() {
        var controller = new TypedAgentContractController(selectedAdapter((operation, prompt, requested) ->
                "{\"kind\":\"END_TURN\",\"actorId\":\"00000000-0000-0000-0000-000000000323\","
                        + "\"endTurnAssessment\":\"no favorable legal action\",\"citationKeys\":[]}"), new ObjectMapper(),
                new ApiRequestGuard("service-secret"), requested -> resolution("actual-model", requested));
        var request = new TypedAgentContractController.CombatTurnDecisionRequest(SOLO_PLAYER_ID, "combat-op",
                SELECTED_ENDPOINT_ID, "openai", "actual-model", "high", SELECTED_ENDPOINT_ID,
                Instant.EPOCH.toString(), "openai", "actual-model", "ROLE=COMBAT_TURN_DECISION");

        assertThrows(IllegalArgumentException.class, () -> controller.combatTurnDecision("service-secret", request));
    }

    @Test
    void every_typed_agent_endpoint_requires_the_internal_service_token() {
        TypedAgentContractController controller = new TypedAgentContractController(
                emptyAdapter(), new ObjectMapper(), new ApiRequestGuard("service-secret"));

        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.scenarioCompilation("wrong", new TypedAgentContractController.ScenarioCompilationRequest(SOLO_PLAYER_ID, "op", "storybook")));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.scenarioLookup("wrong", new TypedAgentContractController.ScenarioLookupRequest(SOLO_PLAYER_ID, "door", Map.of())));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.runtimeTurn("wrong", new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "open door", List.of())));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.narrationSafety("wrong", new TypedAgentContractController.NarrationSafetyRequest(SOLO_PLAYER_ID, "A door opens.", List.of())));
    }

    @Test
    void typed_requests_reject_missing_required_values_before_provider_access() {
        TypedAgentContractController controller = new TypedAgentContractController(
                emptyAdapter(), new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.scenarioLookup("service-secret", new TypedAgentContractController.ScenarioLookupRequest(SOLO_PLAYER_ID, " ", Map.of())));
    }

    @Test
    void rulebook_action_agent_input_rejects_unknown_fields_before_provider_access() throws Exception {
        var controller = new TypedAgentContractController(emptyAdapter(), new ObjectMapper(), new ApiRequestGuard("service-secret"));
        var body = new ObjectMapper().readTree("""
                {"soloPlayerId":"00000000-0000-0000-0000-000000000321","endpointId":null,"provider":"ollama",
                 "model":"model","reasoning":"medium","operationKey":"rulebook-actions:source-anchor-v4:test",
                 "extractionInput":{"documentId":"89ef62fc-b2dd-4b1b-91c9-04810d880f3a","extractionVersion":"ev-1",
                   "queryCoverage":[],"evidence":[],"rawPrompt":"must not be accepted"},
                 "outputSchema":{"type":"object","additionalProperties":false,"required":["definitions"],"properties":{"definitions":{"type":"array"}}}}
                """);

        assertThrows(IllegalArgumentException.class, () -> controller.rulebookActionDefinitions("service-secret", body));
    }

    @Test
    void rulebook_action_agent_input_rejects_invalid_source_identity_and_empty_evidence_before_provider_access() throws Exception {
        var controller = new TypedAgentContractController(emptyAdapter(), new ObjectMapper(), new ApiRequestGuard("service-secret"));
        String prefix = """
                {"soloPlayerId":"00000000-0000-0000-0000-000000000321","endpointId":null,"provider":"ollama",
                 "model":"model","reasoning":"medium","operationKey":"rulebook-actions:source-anchor-v4:test",
                 "extractionInput":{"documentId":"89ef62fc-b2dd-4b1b-91c9-04810d880f3a","extractionVersion":"%s",
                   "queryCoverage":[],"evidence":[{"id":"%s","locator":"page=1","anchors":[{"id":"%s","text":"%s"}]}]},
                 "outputSchema":{"type":"object","additionalProperties":false,"required":["definitions"],"properties":{"definitions":{"type":"array","items":{"type":"object","properties":{"sourceAnchorId":{"enum":["%s"]}}}}}}}
                """;
        String validId = "82ad2366-a013-4d13-b922-9fa2c5970634";
        for (String[] invalid : List.of(new String[] {"", validId, "quote"}, new String[] {"ev-1", "not-a-uuid", "quote"},
                new String[] {"ev-1", validId, ""})) {
            String anchorId = "0";
            var body = new ObjectMapper().readTree(prefix.formatted(invalid[0], invalid[1], anchorId, invalid[2], anchorId));
            assertThrows(IllegalArgumentException.class, () -> controller.rulebookActionDefinitions("service-secret", body));
        }
    }

    @Test
    void rulebook_action_agent_output_is_checked_against_dynamic_evidence_schema() throws Exception {
        var controller = new TypedAgentContractController(selectedAdapter((operation, prompt, requested) -> "{}"),
                new ObjectMapper(), new ApiRequestGuard("service-secret"), requested -> resolution("model", requested));
        String id = "82ad2366-a013-4d13-b922-9fa2c5970634";
        ObjectMapper mapper = new ObjectMapper();
        var body = mapper.createObjectNode().put("soloPlayerId", SOLO_PLAYER_ID.toString()).putNull("endpointId")
                .put("provider", "ollama").put("model", "model").put("reasoning", "medium")
                .put("operationKey", "rulebook-actions:source-anchor-v4:test");
        body.set("extractionInput", mapper.readTree("""
                {"documentId":"89ef62fc-b2dd-4b1b-91c9-04810d880f3a","extractionVersion":"ev-1","queryCoverage":[],
                 "evidence":[{"id":"82ad2366-a013-4d13-b922-9fa2c5970634","locator":"page=1","anchors":[{"id":"0","text":"A supported rule."}]}]}
                """));
        body.set("outputSchema", strictRulebookActionOutputSchema(mapper, id));

        assertThrows(IllegalArgumentException.class, () -> controller.rulebookActionDefinitions("service-secret", body));
    }

    @Test
    void assigned_action_response_requires_one_outcome_for_each_candidate_id() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String valid = "{\"candidateResults\":[{\"candidateId\":\"candidate-a\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"missing range\",\"neededFields\":[],\"sharedRules\":[],\"sharedRuleApplicability\":[]},{\"candidateId\":\"candidate-b\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"missing range\",\"neededFields\":[],\"sharedRules\":[],\"sharedRuleApplicability\":[]}],\"definitions\":[]}";
        var controller = new TypedAgentContractController(selectedAdapter((operation, prompt, requested) -> valid),
                mapper, new ApiRequestGuard("service-secret"), requested -> resolution("model", requested));
        var body = mapper.createObjectNode().put("soloPlayerId", SOLO_PLAYER_ID.toString()).putNull("endpointId")
                .put("provider", "ollama").put("model", "model").put("reasoning", "medium").put("operationKey", "assigned-actions:test");
        body.set("extractionInput", mapper.readTree("""
                {"documentId":"89ef62fc-b2dd-4b1b-91c9-04810d880f3a","extractionVersion":"ev-1",
                 "queryCoverage":[{"queryId":"assigned-actions","queryFingerprint":"fp","candidateCount":1}],
                 "evidence":[{"id":"82ad2366-a013-4d13-b922-9fa2c5970634","locator":"page=1","anchors":[{"id":"0","text":"A listed action."}]}],
                 "assignedActions":[{"id":"candidate-a","name":"Action A","actionKind":"SPELL","sourceLocator":"page=1","evidenceIds":["82ad2366-a013-4d13-b922-9fa2c5970634"],"knownFields":{},"neededFields":[],"sharedRules":[]},{"id":"candidate-b","name":"Action B","actionKind":"SPELL","sourceLocator":"page=1","evidenceIds":["82ad2366-a013-4d13-b922-9fa2c5970634"],"knownFields":{},"neededFields":[],"sharedRules":[]}]}
                """));
        body.set("outputSchema", assignedActionOutputSchema(mapper));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("outputSchema").path("properties").path("candidateResults").path("items")
                .path("properties").path("sharedRules")).put("maxItems", 0);

        var response = controller.rulebookActionDefinitions("service-secret", body);

        org.junit.jupiter.api.Assertions.assertEquals(2, ((com.fasterxml.jackson.databind.JsonNode) response.getBody()).path("candidateResults").size());
        var wrongAnchorScope = (com.fasterxml.jackson.databind.node.ObjectNode) assignedActionOutputSchema(mapper).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) wrongAnchorScope.path("properties").path("candidateResults").path("items")
                .path("properties").path("sharedRules")).put("maxItems", 0);
        ((com.fasterxml.jackson.databind.node.ObjectNode) wrongAnchorScope.path("properties").path("definitions").path("items")
                .path("anyOf").get(0).path("properties").path("sourceAnchorId")).putArray("enum").add("999");
        body.set("outputSchema", wrongAnchorScope);
        body.put("operationKey", "assigned-actions:wrong-anchor-scope");
        assertThrows(IllegalArgumentException.class, () -> controller.rulebookActionDefinitions("service-secret", body));
        var unsupported = (com.fasterxml.jackson.databind.node.ObjectNode) assignedActionOutputSchema(mapper).deepCopy();
        var requiredSharedFields = (com.fasterxml.jackson.databind.node.ArrayNode) unsupported.path("properties").path("candidateResults").path("items")
                .path("properties").path("sharedRules").path("items").path("properties").path("fields").path("required");
        for (int index = 0; index < requiredSharedFields.size(); index++) {
            if ("cost".equals(requiredSharedFields.get(index).asText())) { requiredSharedFields.remove(index); break; }
        }
        body.set("outputSchema", unsupported);
        body.put("operationKey", "assigned-actions:optional-output-field");
        assertThrows(IllegalArgumentException.class, () -> controller.rulebookActionDefinitions("service-secret", body));
        var omittedController = new TypedAgentContractController(selectedAdapter((operation, prompt, requested) ->
                "{\"candidateResults\":[{\"candidateId\":\"candidate-a\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"missing range\",\"neededFields\":[]}] ,\"definitions\":[]}"),
                mapper, new ApiRequestGuard("service-secret"), requested -> resolution("model", requested));
        body.put("operationKey", "assigned-actions:omitted");
        assertThrows(IllegalArgumentException.class, () -> omittedController.rulebookActionDefinitions("service-secret", body));

        var duplicateController = new TypedAgentContractController(selectedAdapter((operation, prompt, requested) ->
                "{\"candidateResults\":[{\"candidateId\":\"candidate-a\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"missing range\",\"neededFields\":[]},{\"candidateId\":\"candidate-a\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"duplicate\",\"neededFields\":[]}],\"definitions\":[]}"),
                mapper, new ApiRequestGuard("service-secret"), requested -> resolution("model", requested));
        body.put("operationKey", "assigned-actions:duplicate");
        assertThrows(IllegalArgumentException.class, () -> duplicateController.rulebookActionDefinitions("service-secret", body));

        var foreignController = new TypedAgentContractController(selectedAdapter((operation, prompt, requested) ->
                "{\"candidateResults\":[{\"candidateId\":\"foreign-id\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"unknown\",\"neededFields\":[]},{\"candidateId\":\"candidate-b\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"missing range\",\"neededFields\":[]}],\"definitions\":[]}"),
                mapper, new ApiRequestGuard("service-secret"), requested -> resolution("model", requested));
        body.put("operationKey", "assigned-actions:foreign");
        assertThrows(IllegalArgumentException.class, () -> foreignController.rulebookActionDefinitions("service-secret", body));
    }

    @Test
    void rulebook_action_token_estimate_usesValidatedPromptAndSchemaWithoutCallingProvider() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var controller = new TypedAgentContractController(emptyAdapter(), mapper, new ApiRequestGuard("service-secret"));
        var body = mapper.createObjectNode().put("soloPlayerId", SOLO_PLAYER_ID.toString()).putNull("endpointId")
                .put("provider", "ollama").put("model", "model").put("reasoning", "medium").put("operationKey", "estimate-only");
        body.set("extractionInput", mapper.readTree("""
                {"documentId":"89ef62fc-b2dd-4b1b-91c9-04810d880f3a","extractionVersion":"ev-1",
                 "queryCoverage":[{"queryId":"assigned-actions","queryFingerprint":"fp","candidateCount":2}],
                 "evidence":[{"id":"82ad2366-a013-4d13-b922-9fa2c5970634","locator":"page=1","anchors":[{"id":"0","text":"short"}]}],
                 "assignedActions":[{"id":"candidate-a","name":"Action A","actionKind":"SPELL","sourceLocator":"page=1","evidenceIds":["82ad2366-a013-4d13-b922-9fa2c5970634"],"knownFields":{},"neededFields":[],"sharedRules":[]},
                                    {"id":"candidate-b","name":"Action B","actionKind":"SPELL","sourceLocator":"page=1","evidenceIds":["82ad2366-a013-4d13-b922-9fa2c5970634"],"knownFields":{},"neededFields":[],"sharedRules":[]}]}
                """));
        body.set("outputSchema", assignedActionOutputSchema(mapper));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("outputSchema").path("properties").path("candidateResults")
                .path("items").path("properties").path("sharedRules")).put("maxItems", 0);

        var first = controller.estimateRulebookActionInputTokens("service-secret", body);
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("extractionInput").path("evidence").get(0).path("anchors").get(0))
                .put("text", "火の矢。".repeat(100));
        var second = controller.estimateRulebookActionInputTokens("service-secret", body);

        long shortEstimate = ((Number) ((Map<?, ?>) first.getBody()).get("estimatedInputTokens")).longValue();
        long expandedEstimate = ((Number) ((Map<?, ?>) second.getBody()).get("estimatedInputTokens")).longValue();
        org.junit.jupiter.api.Assertions.assertTrue(shortEstimate > 0);
        org.junit.jupiter.api.Assertions.assertTrue(expandedEstimate > shortEstimate);
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, ((Map<?, ?>) first.getBody()).get("includesProviderSystemAndToolOverhead"));
    }

    @Test
    void rulebook_action_input_preservesMoreThanFortyEightEvidenceItems() {
        List<TypedAgentContractController.RulebookEvidenceInput> evidence = java.util.stream.IntStream.range(0, 65)
                .mapToObj(index -> new TypedAgentContractController.RulebookEvidenceInput(
                        UUID.nameUUIDFromBytes(("evidence-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                        "page=" + index, List.of(new TypedAgentContractController.RulebookActionAnchorInput("0", "source " + index))))
                .toList();

        var input = new TypedAgentContractController.RulebookActionExtractionInput(UUID.randomUUID(), "version-1", List.of(), evidence, List.of());

        org.junit.jupiter.api.Assertions.assertEquals(65, input.evidence().size());
    }

    @Test
    void assigned_action_input_accepts_65_sources_over_24k_and_discovery_remains_capped_at_24k() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String response = "{\"candidateResults\":[{\"candidateId\":\"candidate-a\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"missing range\",\"neededFields\":[],\"sharedRules\":[],\"sharedRuleApplicability\":[]},{\"candidateId\":\"candidate-b\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"missing range\",\"neededFields\":[],\"sharedRules\":[],\"sharedRuleApplicability\":[]}],\"definitions\":[]}";
        var controller = new TypedAgentContractController(selectedAdapter((operation, prompt, requested) -> response),
                mapper, new ApiRequestGuard("service-secret"), requested -> resolution("model", requested));
        var body = mapper.createObjectNode().put("soloPlayerId", SOLO_PLAYER_ID.toString()).putNull("endpointId")
                .put("provider", "ollama").put("model", "model").put("reasoning", "medium").put("operationKey", "assigned-actions:48k-test");
        var extractionInput = mapper.createObjectNode().put("documentId", "89ef62fc-b2dd-4b1b-91c9-04810d880f3a").put("extractionVersion", "ev-1");
        extractionInput.putArray("queryCoverage");
        var evidence = extractionInput.putArray("evidence");
        List<String> evidenceIds = new java.util.ArrayList<>();
        for (int item = 0; item < 65; item++) {
            String evidenceId = item == 0 ? "82ad2366-a013-4d13-b922-9fa2c5970634"
                    : UUID.nameUUIDFromBytes(("oversized-assigned-evidence-" + item).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            evidenceIds.add(evidenceId);
            var source = evidence.addObject().put("id", evidenceId).put("locator", "page=" + (item + 1));
            var anchors = source.putArray("anchors");
            anchors.addObject().put("id", "0").put("text", "x".repeat(500));
        }
        var assigned = extractionInput.putArray("assignedActions");
        var candidateA = assigned.addObject().put("id", "candidate-a").put("name", "Action A").put("actionKind", "SPELL").put("sourceLocator", "page=1");
        evidenceIds.forEach(candidateA.putArray("evidenceIds")::add);
        var known = candidateA.putObject("knownFields");
        for (String field : List.of("level", "castingTime", "cost", "range", "rangeForm", "components", "duration", "school",
                "conditions", "attackSave", "damageEffect", "effect", "targetMode", "originMode", "effectShape", "pathBlocking",
                "propagation", "verticalDirection", "normalRangeFeet", "longRangeFeet", "lengthFeet", "widthFeet", "heightFeet", "radiusFeet"))
            known.putNull(field);
        candidateA.putArray("neededFields").add("cost"); candidateA.putArray("sharedRules");
        var candidateB = assigned.addObject().put("id", "candidate-b").put("name", "Action B").put("actionKind", "SPELL").put("sourceLocator", "page=1");
        evidenceIds.forEach(candidateB.putArray("evidenceIds")::add);
        candidateB.putObject("knownFields"); candidateB.putArray("neededFields"); candidateB.putArray("sharedRules");
        body.set("extractionInput", extractionInput);
        var schema = (com.fasterxml.jackson.databind.node.ObjectNode) assignedActionOutputSchema(mapper).deepCopy();
        schema.put("x-stage", "ASSIGNED");
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties").path("candidateResults").path("items").path("properties").path("sharedRules")).put("maxItems", 8);
        setAnchorEnum(schema.path("properties").path("candidateResults").path("items").path("properties").path("sharedRules").path("items").path("properties").path("sourceAnchorId"), 1);
        setAnchorEnum(schema.path("properties").path("definitions").path("items").path("properties").path("sourceAnchorId"), 1);
        for (var identity : schema.path("properties").path("definitions").path("items").path("anyOf"))
            setAnchorEnum(identity.path("properties").path("sourceAnchorId"), 1);
        body.set("outputSchema", schema);

        var estimateController = new TypedAgentContractController(emptyAdapter(), mapper, new ApiRequestGuard("service-secret"));
        var estimate = estimateController.estimateRulebookActionInputTokens("service-secret", body);
        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.OK, estimate.getStatusCode());
        org.junit.jupiter.api.Assertions.assertTrue(mapper.writeValueAsBytes(body).length > 24_000);

        var result = controller.rulebookActionDefinitions("service-secret", body);

        org.junit.jupiter.api.Assertions.assertEquals(2, ((com.fasterxml.jackson.databind.JsonNode) result.getBody()).path("candidateResults").size());
        org.junit.jupiter.api.Assertions.assertEquals(65, evidence.size());
        org.junit.jupiter.api.Assertions.assertEquals(32_500, evidence.size() * 500);

        var discoveryBody = body.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) discoveryBody.path("extractionInput")).remove("assignedActions");
        var discoverySchema = mapper.createObjectNode().put("type", "object").put("x-stage", "DISCOVERY");
        discoveryBody.set("outputSchema", discoverySchema);
        discoveryBody.put("operationKey", "discovery-actions:24k-test");
        assertThrows(IllegalArgumentException.class, () -> controller.rulebookActionDefinitions("service-secret", discoveryBody));
    }

    private static com.fasterxml.jackson.databind.JsonNode assignedActionOutputSchema(ObjectMapper mapper) {
        var schema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false).put("x-stage", "ASSIGNED");
        schema.putArray("required").add("candidateResults").add("definitions");
        var properties = schema.putObject("properties");
        var results = properties.putObject("candidateResults").put("type", "array").put("minItems", 2).put("maxItems", 2);
        var result = results.putObject("items").put("type", "object").put("additionalProperties", false);
        result.putArray("required").add("candidateId").add("status").add("reason").add("neededFields").add("sharedRules").add("sharedRuleApplicability");
        var resultProperties = result.putObject("properties");
        resultProperties.putObject("candidateId").put("type", "string").putArray("enum").add("candidate-a").add("candidate-b");
        resultProperties.putObject("status").put("type", "string").putArray("enum").add("COMPLETED").add("INSUFFICIENT_EVIDENCE").add("FAILED");
        resultProperties.putObject("reason").putArray("type").add("string").add("null");
        resultProperties.putObject("neededFields").put("type", "array").putObject("items")
                .put("type", "string").putArray("enum").add("range").add("cost");
        var shared = resultProperties.putObject("sharedRules").put("type", "array").put("maxItems", 8).putObject("items");
        shared.put("type", "object").put("additionalProperties", false);
        shared.putArray("required").add("applicability").add("conditions").add("sourceAnchorId").add("fields");
        var sharedProps = shared.putObject("properties");
        sharedProps.putObject("applicability").put("type", "string").putArray("enum").add("APPLIES").add("AMBIGUOUS");
        sharedProps.putObject("conditions").put("type", "string");
        sharedProps.putObject("sourceAnchorId").put("type", "string").put("pattern", "^[0-9]{1,6}$").putArray("enum").add("0");
        var sharedFields = strictRulebookActionOutputSchema(mapper, "0").path("properties").path("definitions").path("items")
                .path("properties").path("fields").deepCopy();
        sharedProps.set("fields", sharedFields);
        var applicability = resultProperties.putObject("sharedRuleApplicability").put("type", "array").put("minItems", 0).put("maxItems", 0).putObject("items");
        applicability.put("type", "object").put("additionalProperties", false);
        applicability.putArray("required").add("id").add("applicability");
        var applicabilityProps = applicability.putObject("properties");
        applicabilityProps.putObject("id").put("type", "string");
        applicabilityProps.putObject("applicability").put("type", "string").putArray("enum").add("APPLIES").add("AMBIGUOUS").add("NOT_APPLICABLE");
        var definitions = (com.fasterxml.jackson.databind.node.ObjectNode) strictRulebookActionOutputSchema(mapper,
                "82ad2366-a013-4d13-b922-9fa2c5970634:span-0").path("properties").path("definitions").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) definitions.path("items").path("properties").path("id"))
                .putArray("enum").add("candidate-a").add("candidate-b");
        var anyOf = ((com.fasterxml.jackson.databind.node.ObjectNode) definitions.path("items")).putArray("anyOf");
        for (String[] action : List.of(new String[] {"candidate-a", "Action A"}, new String[] {"candidate-b", "Action B"})) {
            var variant = anyOf.addObject();
            variant.set("properties", definitions.path("items").path("properties").deepCopy());
            var identity = (com.fasterxml.jackson.databind.node.ObjectNode) variant.path("properties");
            identity.putObject("id").put("type", "string").putArray("enum").add(action[0]);
            identity.putObject("name").put("type", "string").putArray("enum").add(action[1]);
            identity.putObject("actionKind").put("type", "string").putArray("enum").add("SPELL");
            variant.set("required", definitions.path("items").path("required").deepCopy());
            variant.put("additionalProperties", false);
        }
        properties.set("definitions", definitions);
        return schema;
    }

    private static com.fasterxml.jackson.databind.JsonNode strictRulebookActionOutputSchema(ObjectMapper mapper, String anchorId) {
        var schema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putArray("required").add("definitions");
        var definitions = schema.putObject("properties").putObject("definitions").put("type", "array");
        var item = definitions.putObject("items").put("type", "object").put("additionalProperties", false);
        var itemNames = List.of("id", "name", "actionKind", "sourceAnchorId", "unsupportedFields", "fields");
        item.putArray("required").addAll(itemNames.stream().map(mapper.getNodeFactory()::textNode).toList());
        var properties = item.putObject("properties");
        itemNames.forEach(name -> properties.putObject(name).put("type", name.equals("fields") ? "object" : "string"));
        properties.putObject("sourceAnchorId").put("type", "string").put("pattern", "^[0-9]{1,6}$").putArray("enum").add(anchorId.matches("[0-9]{1,6}") ? anchorId : "0");
        var fields = (com.fasterxml.jackson.databind.node.ObjectNode) properties.path("fields");
        fields.put("additionalProperties", false);
        var fieldNames = List.of("level", "castingTime", "cost", "range", "rangeForm", "components", "duration", "school", "conditions",
                "attackSave", "damageEffect", "effect", "targetMode", "originMode", "effectShape", "pathBlocking", "propagation", "verticalDirection",
                "normalRangeFeet", "longRangeFeet", "lengthFeet", "widthFeet", "heightFeet", "radiusFeet");
        var fieldProperties = fields.putObject("properties");
        fieldNames.forEach(name -> fieldProperties.putObject(name).putArray("type").add("string").add("null"));
        fields.putArray("required").addAll(fieldNames.stream().map(mapper.getNodeFactory()::textNode).toList());
        return schema;
    }

    private static void setAnchorEnum(com.fasterxml.jackson.databind.JsonNode schema, int count) {
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) schema;
        object.remove("enum");
        var values = object.putArray("enum");
        for (int index = 0; index < count; index++) values.add(Integer.toString(index));
    }

    @Test
    void conversation_compaction_returns_only_a_candidate_for_the_requested_confirmed_range() {
        AtomicReference<String> prompt = new AtomicReference<>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String value, StructuredResponseParser<T> parser) {
                prompt.set(value);
                return parser.parse("{\"sourceStart\":4,\"sourceEnd\":7,\"expectedAdventureVersion\":9,\"summary\":\"문을 열고 안으로 들어가 복도를 살핀다\"}");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));
        var result = controller.conversationCompaction("service-secret",
                new TypedAgentContractController.ConversationCompactionRequest(4, 7, 9,
                        List.of(new TypedAgentContractController.ConversationEntry(4, "PLAYER", "문을 열고 주변의 낡은 흔적과 안전한 길을 천천히 살핀다"),
                                new TypedAgentContractController.ConversationEntry(5, "AI_GAME_MASTER", "무거운 문이 열리며 안쪽에서 찬바람이 불어오고 낮은 소리가 들린다"),
                                new TypedAgentContractController.ConversationEntry(6, "PLAYER", "복도 안으로 조심스럽게 들어가 벽과 바닥에 이상한 흔적이 있는지 살핀다"),
                                new TypedAgentContractController.ConversationEntry(7, "AI_GAME_MASTER", "복도 끝에 닫힌 문이 있고 그 앞에 오래된 발자국이 여러 개 남아 있다"))));
        org.junit.jupiter.api.Assertions.assertEquals("문을 열고 안으로 들어가 복도를 살핀다", result.summary());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("SOURCE_START=4"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("EXPECTED_ADVENTURE_VERSION=9"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("character speech"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("scene flow"));
    }

    @Test
    void conversation_compaction_forwards_the_adventure_owner_to_the_provider() {
        AtomicReference<UUID> owner = new AtomicReference<>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                return complete(SOLO_PLAYER_ID, operation, prompt, parser);
            }
            @Override public <T> T complete(UUID soloPlayerId, String operation, String prompt,
                    StructuredResponseParser<T> parser) {
                owner.set(soloPlayerId);
                return parser.parse("{\"sourceStart\":4,\"sourceEnd\":4,\"expectedAdventureVersion\":9,\"summary\":\"플레이어가 문을 연다\"}");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));
        controller.conversationCompaction("service-secret",
                new TypedAgentContractController.ConversationCompactionRequest(SOLO_PLAYER_ID, 4, 4, 9,
                        List.of(new TypedAgentContractController.ConversationEntry(4, "PLAYER", "문을 열고 주변을 천천히 살펴 안전한 길을 찾는다"))));
        org.junit.jupiter.api.Assertions.assertEquals(SOLO_PLAYER_ID, owner.get());
    }

    @Test
    void conversation_compaction_returns_long_term_record_candidates_only_for_confirmed_runtime_facts() {
        UUID factId = UUID.randomUUID();
        UUID establishedTurnId = UUID.randomUUID();
        AtomicReference<String> prompt = new AtomicReference<>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String value, StructuredResponseParser<T> parser) {
                prompt.set(value);
                return parser.parse("{\"sourceStart\":4,\"sourceEnd\":4,\"expectedAdventureVersion\":9,\"summary\":\"플레이어가 문을 연다\",\"longTermFacts\":[{\"factId\":\"" + factId + "\",\"establishedTurnId\":\"" + establishedTurnId + "\",\"kind\":\"RELATIONSHIP\",\"relevance\":\"성문 경비의 협력 약속\",\"playerVisible\":true}]}");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));
        var result = controller.conversationCompaction("service-secret",
                new TypedAgentContractController.ConversationCompactionRequest(SOLO_PLAYER_ID, 4, 4, 9,
                        List.of(new TypedAgentContractController.ConversationEntry(4, "PLAYER", "문을 열고 주변을 천천히 살펴 경비와 대화한다")),
                        List.of(new TypedAgentContractController.RuntimeFactReference(factId, establishedTurnId, "경비가 성문을 열기로 약속했다"))));

        org.junit.jupiter.api.Assertions.assertEquals(factId, result.longTermFacts().getFirst().factId());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("CONFIRMED_RUNTIME_FACTS"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("longTermFacts"));
    }

    @Test
    void conversation_compaction_discards_invalid_optional_fact_proposals_without_losing_summary_candidate() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String value, StructuredResponseParser<T> parser) {
                return parser.parse("{\"sourceStart\":4,\"sourceEnd\":4,\"expectedAdventureVersion\":9,"
                        + "\"summary\":\"문을 열고 경비를 만난다\","
                        + "\"longTermFacts\":[{\"factId\":\"not-a-uuid\",\"establishedTurnId\":\"also-not-a-uuid\","
                        + "\"kind\":\"GOAL\",\"relevance\":\"오래된 제안\",\"playerVisible\":true},"
                        + "{\"factId\":\"00000000-0000-0000-0000-000000000399\","
                        + "\"establishedTurnId\":\"00000000-0000-0000-0000-000000000398\","
                        + "\"kind\":\"GOAL\",\"relevance\":\"확인되지 않은 제안\",\"playerVisible\":true}]} ");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var result = controller.conversationCompaction("service-secret",
                new TypedAgentContractController.ConversationCompactionRequest(SOLO_PLAYER_ID, 4, 4, 9,
                        List.of(new TypedAgentContractController.ConversationEntry(4, "PLAYER", "문을 열고 주변을 천천히 살펴 경비와 대화한다")), List.of()));

        org.junit.jupiter.api.Assertions.assertEquals("문을 열고 경비를 만난다", result.summary());
        org.junit.jupiter.api.Assertions.assertTrue(result.longTermFacts().isEmpty());
    }

    @Test
    void conversation_compaction_rejects_a_candidate_for_another_source_version() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String value, StructuredResponseParser<T> parser) {
                return parser.parse("{\"sourceStart\":4,\"sourceEnd\":7,\"expectedAdventureVersion\":10,\"summary\":\"플레이어가 복도에 들어간다\"}");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));
        assertThrows(IllegalArgumentException.class, () -> controller.conversationCompaction("service-secret",
                new TypedAgentContractController.ConversationCompactionRequest(4, 7, 9,
                        List.of(new TypedAgentContractController.ConversationEntry(4, "PLAYER", "문을 연다"),
                                new TypedAgentContractController.ConversationEntry(5, "AI_GAME_MASTER", "문이 열린다"),
                                new TypedAgentContractController.ConversationEntry(6, "PLAYER", "안으로 간다"),
                                new TypedAgentContractController.ConversationEntry(7, "AI_GAME_MASTER", "복도를 본다")))));
    }

    @Test
    void conversation_compaction_rejects_missing_or_incomplete_source_references() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String value, StructuredResponseParser<T> parser) {
                return parser.parse("{\"sourceStart\":4,\"sourceEnd\":5,\"expectedAdventureVersion\":9,\"summary\":\"\"}");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));
        var conversation = List.of(new TypedAgentContractController.ConversationEntry(4, "PLAYER", "문을 연다"),
                new TypedAgentContractController.ConversationEntry(5, "AI_GAME_MASTER", "문이 열린다"));
        assertThrows(IllegalArgumentException.class, () -> controller.conversationCompaction("service-secret",
                new TypedAgentContractController.ConversationCompactionRequest(4, 5, 9, conversation)));
        assertThrows(IllegalArgumentException.class, () -> new TypedAgentContractController.ConversationCompactionRequest(4, 5, 9,
                List.of(conversation.getFirst())));
    }

    @Test
    void conversation_compaction_accepts_one_mean_preserving_summary_across_multiple_entries_without_echoing_provenance() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String value, StructuredResponseParser<T> parser) {
                return parser.parse("{\"sourceStart\":4,\"sourceEnd\":5,\"expectedAdventureVersion\":9,\"summary\":\"플레이어가 위험을 살피며 문을 열고 들어갔고, 어두운 복도에서 찬바람을 느꼈다\"}");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));
        var conversation = List.of(new TypedAgentContractController.ConversationEntry(4, "PLAYER", "문을 열고 안을 조심히 살펴본 뒤 안쪽으로 들어간다. 주변에 위험이 없는지도 확인한다"),
                new TypedAgentContractController.ConversationEntry(5, "AI_GAME_MASTER", "문이 열리자 어둡고 긴 복도가 나타난다. 바깥의 온기와 달리 안쪽에서는 차가운 바람이 불어온다"));
        var result = controller.conversationCompaction("service-secret",
                new TypedAgentContractController.ConversationCompactionRequest(4, 5, 9, conversation));
        org.junit.jupiter.api.Assertions.assertTrue(result.summary().contains("위험을 살피며"));
    }

    @Test
    void conversation_compaction_rejects_summary_with_unrequested_source_sequence() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String value, StructuredResponseParser<T> parser) {
                return parser.parse("{\"sourceStart\":5,\"sourceEnd\":5,\"expectedAdventureVersion\":9,\"summary\":\"문을 열었다\"}");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));
        assertThrows(IllegalArgumentException.class, () -> controller.conversationCompaction("service-secret",
                new TypedAgentContractController.ConversationCompactionRequest(4, 4, 9,
                        List.of(new TypedAgentContractController.ConversationEntry(4, "PLAYER", "문을 열고 안을 자세히 살펴본다")))));
    }

    @Test
    void runtime_turn_requires_the_server_confirmed_solo_player_id() {
        TypedAgentContractController controller = new TypedAgentContractController(
                emptyAdapter(), new ObjectMapper(), new ApiRequestGuard("service-secret"));

        assertThrows(NullPointerException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(null, "op", "look around", List.of(), Map.of())));
    }

    @Test
    void typed_lookup_compilation_and_safety_forward_the_server_confirmed_solo_player_id() {
        var seen = new java.util.ArrayList<UUID>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{}");
            }
            @Override public <T> T complete(UUID owner, String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                seen.add(owner);
                String response = operation.startsWith("scenario-compilation") ? "{\"status\":\"READY\",\"scenarioModel\":{\"schemaVersion\":1,\"actors\":[],\"locations\":[],\"objectives\":[],\"revelations\":[],\"encounters\":[],\"relationships\":[],\"resolutionCriteria\":[],\"startingSituation\":\"Opening\"},\"diagnostics\":[]}"
                        : operation.startsWith("action-context-lookup") ? "{\"status\":\"NOT_FOUND\",\"answer\":\"\",\"supportingElementIds\":[]}"
                        : "{\"approved\":true}";
                return parser.parse(response);
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        controller.scenarioCompilation("service-secret", new TypedAgentContractController.ScenarioCompilationRequest(SOLO_PLAYER_ID, "scenario-compilation:op", "storybook"));
        controller.scenarioLookup("service-secret", new TypedAgentContractController.ScenarioLookupRequest(SOLO_PLAYER_ID, "door", Map.of()));
        controller.narrationSafety("service-secret", new TypedAgentContractController.NarrationSafetyRequest(SOLO_PLAYER_ID, "문이 열린다.", List.of()));

        org.junit.jupiter.api.Assertions.assertEquals(List.of(SOLO_PLAYER_ID, SOLO_PLAYER_ID, SOLO_PLAYER_ID), seen);
    }

    @Test
    void initial_situation_generation_uses_the_selected_provider_and_internal_authentication() throws Exception {
        var seen = new AtomicReference<String>();
        var seenSchema = new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        var generatedJson = new AtomicReference<>("{\"stages\":[{\"stageId\":\"opening\"}]}");
        var schema = new ObjectMapper().readTree("{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}");
        var endpointId = UUID.randomUUID();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                throw new AssertionError("selected-provider completion must be used");
            }
            @Override public <T> GmCompletionResult<T> completeWithResolution(UUID owner, String operation,
                    String prompt, StructuredResponseParser<T> parser, RequestedGmProviderSelection requested,
                    GmProviderSelectionResolver.EndpointResolution resolution, com.fasterxml.jackson.databind.JsonNode rag,
                    com.fasterxml.jackson.databind.JsonNode outputSchema) {
                org.junit.jupiter.api.Assertions.assertEquals(SOLO_PLAYER_ID, owner);
                org.junit.jupiter.api.Assertions.assertEquals(endpointId, requested.endpointId());
                org.junit.jupiter.api.Assertions.assertEquals("openai", requested.provider());
                org.junit.jupiter.api.Assertions.assertEquals("story-model", requested.model());
                org.junit.jupiter.api.Assertions.assertEquals("high", requested.reasoning());
                seenSchema.set(outputSchema);
                seen.set(prompt);
                return new GmCompletionResult<>(parser.parse(generatedJson.get()), resolution.effectiveSelection());
            }
        };
        var selection = new GmProviderSelectionResolver.EndpointResolution(
                new com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint(endpointId, "test",
                        com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint.Provider.OPENAI_COMPATIBLE,
                        java.net.URI.create("http://localhost"), "story-model", null, true, java.time.Instant.EPOCH),
                new EffectiveGmProviderSelection(endpointId, java.time.Instant.EPOCH, "openai", "story-model", "high"));
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"),
                request -> selection);
        var request = new TypedAgentContractController.InitialSituationRequest(SOLO_PLAYER_ID, endpointId,
                "openai", "story-model", "high", "initial-situation:adventure:backbone", "source grounded prompt", schema);

        var response = controller.initialSituation("service-secret", request);

        org.junit.jupiter.api.Assertions.assertTrue(response.path("stages").isArray());
        org.junit.jupiter.api.Assertions.assertTrue(seen.get().contains("source grounded prompt"));
        org.junit.jupiter.api.Assertions.assertEquals(schema, seenSchema.get());
        generatedJson.set("{\"stages\":{\"opening\":{},\"opening\":{}}}");
        assertThrows(IllegalArgumentException.class, () -> controller.initialSituation("service-secret", request));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.initialSituation("wrong-secret", request));
        assertThrows(IllegalArgumentException.class, () -> new TypedAgentContractController.InitialSituationRequest(
                SOLO_PLAYER_ID, endpointId, "openai", "story-model", "high", "operation", "prompt",
                new ObjectMapper().createArrayNode()));
        assertThrows(IllegalArgumentException.class, () -> new TypedAgentContractController.InitialSituationRequest(
                SOLO_PLAYER_ID, endpointId, "openai", "story-model", "high", "operation", "prompt",
                new ObjectMapper().createObjectNode().put("type", "object")));
    }

    @Test
    void initial_situation_subtasks_keep_the_selected_model_and_use_medium_reasoning() throws Exception {
        var endpointId = UUID.randomUUID();
        var schema = new ObjectMapper().readTree("{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}");
        var seenReasoning = new AtomicReference<String>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                throw new AssertionError("delegated completion must be used");
            }
            @Override public <T> GmCompletionResult<T> completeWithResolutionAndSubtasks(UUID owner, String operation,
                    String prompt, StructuredResponseParser<T> parser, RequestedGmProviderSelection requested,
                    GmProviderSelectionResolver.EndpointResolution resolution,
                    com.fasterxml.jackson.databind.JsonNode outputSchema,
                    List<com.dndmaster.aigamemaster.application.ai.AiExecutionRequest.Subtask> subtasks) {
                org.junit.jupiter.api.Assertions.assertEquals("gpt-5.6-luna", requested.model());
                seenReasoning.set(requested.reasoning());
                return new GmCompletionResult<>(parser.parse("{\"stages\":{\"opening\":{}}}"), resolution.effectiveSelection());
            }
        };
        var endpoint = new com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint(endpointId, "test",
                com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint.Provider.CODEX_CLI,
                java.net.URI.create("http://localhost"), "gpt-5.6-luna", null, true, java.time.Instant.EPOCH);
        var selection = new GmProviderSelectionResolver.EndpointResolution(endpoint,
                new EffectiveGmProviderSelection(endpointId, java.time.Instant.EPOCH, "codex", "gpt-5.6-luna", "medium"));
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"),
                ignored -> selection);
        var stageTask = new TypedAgentContractController.InitialSituationStageTask("opening", "stage prompt", schema);
        var request = new TypedAgentContractController.InitialSituationRequest(SOLO_PLAYER_ID, endpointId,
                "codex", "gpt-5.6-luna", "medium", "initial-situation:delegated", "parent prompt", schema,
                true, List.of(stageTask));

        controller.initialSituation("service-secret", request);

        org.junit.jupiter.api.Assertions.assertEquals("medium", seenReasoning.get());
    }

    @Test
    void action_context_lookup_receives_only_scoped_facts_and_retrieved_sources() {
        AtomicReference<String> prompt = new AtomicReference<>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String value,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                prompt.set(value);
                return parser.parse("{\"status\":\"not_found\",\"answer\":\"\",\"supportingElementIds\":[]}");
            }
        };
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.scenarioLookup("service-secret",
                new TypedAgentContractController.ScenarioLookupRequest(SOLO_PLAYER_ID, "opening",
                        "the gatehouse", "North Road", Map.of("gate", "open"),
                        List.of(Map.of("content", "Mara promised a reward")),
                        List.of(Map.of("evidenceType", "STORYBOOK", "locator", "page:3", "excerpt", "The reward is a key.")),
                        Map.of("schemaVersion", 1), "gatehouse guard situation", List.of(Map.of("kind", "NPC", "relevance", "Mara promised a reward")), List.of()));

        org.junit.jupiter.api.Assertions.assertEquals("NOT_FOUND", response.status());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("OUTPUT_CONTRACT"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("CURRENT_SITUATION=\"the gatehouse\""));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("CONFIRMED_GAME_STATE={\"gate\":\"open\"}"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Mara promised a reward"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("The reward is a key."));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("PREPARED_SITUATION=\"gatehouse guard situation\""));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("LONG_TERM_ADVENTURE_FACTS"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Do not use dialogue history"));
        org.junit.jupiter.api.Assertions.assertFalse(prompt.get().contains("RECENT_TURNS"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("status is FOUND or NOT_FOUND"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("No markdown"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().indexOf("OUTPUT_CONTRACT") < prompt.get().indexOf("\nPLAYER_ACTION="));
    }

    @Test
    void rejects_unknown_fields_in_the_isolated_lookup_request() throws Exception {
        TypedAgentContractController controller = new TypedAgentContractController(
                selectedAdapter((operation, prompt, requested) -> "{\"status\":\"NOT_FOUND\",\"answer\":\"\",\"supportingElementIds\":[]}"),
                new ObjectMapper(), new ApiRequestGuard("service-secret"));
        var body = new ObjectMapper().readTree("""
                {"soloPlayerId":"%s","query":"open gate","currentSituation":"","currentScene":"",
                 "establishedGameState":{},"runtimeAddedFacts":[],"evidence":[],"lockedScenarioModel":{},
                 "preparedSituation":"","longTermFacts":[],"knownEvidenceIds":[],"unexpected":true}
                """.formatted(SOLO_PLAYER_ID));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.scenarioLookup("service-secret", body));
    }

    @Test
    void scenario_compilation_prompt_requires_storybook_grounded_encounters_and_exact_sources() {
        AtomicReference<String> prompt = new AtomicReference<>();
        AtomicReference<JsonNode> outputSchema = new AtomicReference<>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String value,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                prompt.set(value);
                return parser.parse("{\"status\":\"READY\",\"scenarioModel\":{\"schemaVersion\":1,\"actors\":[],\"locations\":[],\"objectives\":[],\"revelations\":[],\"encounters\":[],\"relationships\":[],\"resolutionCriteria\":[],\"startingSituation\":\"Opening\"},\"diagnostics\":[]}");
            }
            @Override public <T> T complete(UUID owner, String operation, String value,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser, JsonNode schema) {
                outputSchema.set(schema);
                return complete(operation, value, parser);
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        ResponseEntity<?> result = controller.scenarioCompilation("service-secret", new TypedAgentContractController.ScenarioCompilationRequest(
                SOLO_PLAYER_ID, "scenario-compilation:test", "DOCUMENT_ID=abc\\nEXTRACTION_VERSION=1\\nLOCATOR=page:2\\nTEXT=Eight Giant Rats begin combat."));

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.OK, result.getStatusCode());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Find essential combat encounters"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Never invent or rewrite a source reference"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("scenarioModel.encounters"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("diagnostics (an array of strings; use an empty array when there are no diagnostics)"));
        org.junit.jupiter.api.Assertions.assertTrue(outputSchema.get().path("required").toString().contains("diagnostics"));
        org.junit.jupiter.api.Assertions.assertEquals("array", outputSchema.get().path("properties").path("diagnostics").path("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals("string", outputSchema.get().path("properties").path("diagnostics").path("items").path("type").asText());
        JsonNode modelSchema = outputSchema.get().path("properties").path("scenarioModel");
        org.junit.jupiter.api.Assertions.assertEquals(9, modelSchema.path("required").size());
        org.junit.jupiter.api.Assertions.assertEquals("#/$defs/elements", modelSchema.path("properties").path("encounters").path("$ref").asText());
        JsonNode elementSchema = outputSchema.get().path("$defs").path("elements").path("items");
        org.junit.jupiter.api.Assertions.assertEquals("object", elementSchema.path("properties").path("attributes").path("type").asText());
        org.junit.jupiter.api.Assertions.assertTrue(elementSchema.path("properties").path("attributes").path("additionalProperties").asBoolean());
        JsonNode validEnvelope;
        try {
            validEnvelope = new ObjectMapper().readTree("""
                    {"status":"READY","scenarioModel":{"schemaVersion":1,"actors":[{"elementId":"guard","type":"npc",
                    "attributes":{"role":"watcher","customRule":7},"sourceRefs":[{"knowledgeDocumentId":{"value":"doc-1"},
                    "extractionVersion":1,"locator":"page:1"}]}],"locations":[],"objectives":[],"revelations":[],
                    "encounters":[],"relationships":[],"resolutionCriteria":[],"startingSituation":"At the gate."},"diagnostics":[]}
                    """);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
        var validationErrors = com.networknt.schema.SchemaRegistry.withDefaultDialect(
                com.networknt.schema.SpecificationVersion.DRAFT_2020_12).getSchema(outputSchema.get()).validate(validEnvelope);
        org.junit.jupiter.api.Assertions.assertTrue(validationErrors.isEmpty(), validationErrors.toString());
    }

    @Test
    void scenario_compilation_rejects_diagnostics_object_after_passing_schema_to_adapter() {
        AtomicReference<JsonNode> outputSchema = new AtomicReference<>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                throw new AssertionError("schema-aware completion should be used");
            }
            @Override public <T> T complete(UUID owner, String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser, JsonNode schema) {
                outputSchema.set(schema);
                return parser.parse("{\"status\":\"BLOCKED\",\"scenarioModel\":null,\"diagnostics\":{\"reason\":\"unavailable\"}}");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        ResponseEntity<?> response = controller.scenarioCompilation("service-secret",
                new TypedAgentContractController.ScenarioCompilationRequest(SOLO_PLAYER_ID,
                        "scenario-compilation:diagnostics-object", "source"));

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals("array", outputSchema.get().path("properties").path("diagnostics").path("type").asText());
        org.junit.jupiter.api.Assertions.assertTrue(outputSchema.get().path("$defs").path("elements").path("items").path("required").toString().contains("sourceRefs"));
    }

    @Test
    void scenario_compilation_aggregates_model_envelope_and_diagnostics_violations(org.springframework.boot.test.system.CapturedOutput output) {
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        try {
            GmCompletionAdapter adapter = new GmCompletionAdapter() {
                @Override public <T> T complete(String operation, String prompt,
                        com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                    return parser.parse("""
                            {"status":"READY","scenarioModel":{"actors":[{"elementId":"","type":7,"attributes":[],"sourceRefs":[{"knowledgeDocumentId":{},"extractionVersion":0}]}],"startingSituation":""},"diagnostics":{"reason":"bad-shape"}}
                            """);
                }
            };
            var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

            ResponseEntity<?> response = controller.scenarioCompilation("service-secret",
                    new TypedAgentContractController.ScenarioCompilationRequest(SOLO_PLAYER_ID,
                            "scenario-compilation:aggregate-shapes", "source"));
            String logs = output.getAll();

            org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
            org.junit.jupiter.api.Assertions.assertTrue(logs.contains("violationCount=14"), logs);
            for (String path : List.of("scenarioModel.schemaVersion", "scenarioModel.locations", "scenarioModel.objectives",
                    "scenarioModel.revelations", "scenarioModel.encounters", "scenarioModel.relationships",
                    "scenarioModel.resolutionCriteria", "scenarioModel.actors[0].elementId", "scenarioModel.actors[0].type",
                    "scenarioModel.actors[0].attributes", "scenarioModel.actors[0].sourceRefs[0].knowledgeDocumentId.value",
                    "scenarioModel.actors[0].sourceRefs[0].extractionVersion", "scenarioModel.actors[0].sourceRefs[0].locator", "diagnostics")) {
                org.junit.jupiter.api.Assertions.assertTrue(logs.contains("path=" + path), path + " not reported: " + logs);
            }
            org.junit.jupiter.api.Assertions.assertFalse(logs.contains("bad-shape"));
        } finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }
    }

    @Test
    void scenario_compilation_returns_safe_provider_failure_code_and_operation_correlation() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                throw new AiExecutionUnavailableException(new AiExecutionFailure(
                        AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE, "CONNECTION_UNAVAILABLE"));
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        ResponseEntity<?> response = controller.scenarioCompilation("service-secret",
                new TypedAgentContractController.ScenarioCompilationRequest(
                        SOLO_PLAYER_ID, "scenario-compilation:compile-123", "private source excerpt"));

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        var error = (TypedAgentContractController.ScenarioCompilationAgentError) response.getBody();
        org.junit.jupiter.api.Assertions.assertEquals("AI_EXECUTION_CONNECTION_UNAVAILABLE", error.code());
        org.junit.jupiter.api.Assertions.assertEquals("scenario-compilation:compile-123", error.correlationId());
        org.junit.jupiter.api.Assertions.assertEquals("AiExecutionUnavailableException", error.rootCauseClass());
        org.junit.jupiter.api.Assertions.assertTrue(error.retryable());
        org.junit.jupiter.api.Assertions.assertFalse(new ObjectMapper().valueToTree(error).toString().contains("private source excerpt"));
    }

    @Test
    void scenario_compilation_marks_malformed_provider_output_as_bad_gateway() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                throw new ProviderMalformedResponseException("untrusted provider output");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        ResponseEntity<?> response = controller.scenarioCompilation("service-secret",
                new TypedAgentContractController.ScenarioCompilationRequest(
                        SOLO_PLAYER_ID, "scenario-compilation:compile-456", "private source excerpt"));

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        var error = (TypedAgentContractController.ScenarioCompilationAgentError) response.getBody();
        org.junit.jupiter.api.Assertions.assertEquals("SCENARIO_COMPILATION_RESPONSE_INVALID", error.code());
        org.junit.jupiter.api.Assertions.assertEquals("ProviderMalformedResponseException", error.rootCauseClass());
        org.junit.jupiter.api.Assertions.assertFalse(error.retryable());
    }

    @Test
    void runtime_turn_forwards_the_already_composed_single_prompt() {
        AtomicReference<String> prompt = new AtomicReference<>();
        AtomicReference<RequestedGmProviderSelection> selection = new AtomicReference<>();
        GmCompletionAdapter adapter = selectedAdapter((operation, value, requested) -> {
            prompt.set(value);
            selection.set(requested);
            return "{\"scene\":\"양조장\",\"judgment\":\"안전함\",\"narration\":\"방 안은 조용합니다.\",\"situation\":" + situation("SCENARIO", "cellar-rat-ambush") + ",\"combatStart\":true,\"mapEntryRequested\":true,\"판정제안\":{\"필요\":false},\"combatEnemies\":[{\"scenarioId\":\"cellar-rat-ambush\",\"enemyKey\":\"giant-rat\",\"name\":\"거대 쥐\",\"count\":8}]}";
        });

        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        TypedAgentContractController.RuntimeTurnResponse response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "SESSION_OPENING",
                        SELECTED_ENDPOINT_ID, "openai", "gpt-5", "high", List.of(), Map.of()));

        org.junit.jupiter.api.Assertions.assertTrue(response.combatStart());
        org.junit.jupiter.api.Assertions.assertTrue(response.mapEntryRequested());
        org.junit.jupiter.api.Assertions.assertEquals("cellar-rat-ambush", response.combatEnemies().get(0).scenarioId());
        org.junit.jupiter.api.Assertions.assertEquals(8, response.combatEnemies().get(0).count());
        org.junit.jupiter.api.Assertions.assertEquals(new RequestedGmProviderSelection(SELECTED_ENDPOINT_ID, "openai", "gpt-5", "high"), selection.get());
        org.junit.jupiter.api.Assertions.assertEquals("ROLE=RUNTIME_GM", prompt.get());
    }

    @Test
    void runtime_turn_passes_its_dedicated_schema_and_keeps_selection_and_rag_context() {
        var seenSchema = new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        var seenRag = new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        var seenSelection = new AtomicReference<RequestedGmProviderSelection>();
        var adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                throw new AssertionError("resolved provider path must be used");
            }
            @Override public <T> GmCompletionResult<T> completeWithResolution(UUID owner, String operation,
                    String prompt, StructuredResponseParser<T> parser, RequestedGmProviderSelection requested,
                    GmProviderSelectionResolver.EndpointResolution resolution,
                    com.fasterxml.jackson.databind.JsonNode ragContext,
                    com.fasterxml.jackson.databind.JsonNode outputSchema) {
                seenSchema.set(outputSchema);
                seenRag.set(ragContext);
                seenSelection.set(requested);
                return new GmCompletionResult<>(parser.parse(runtimeResponse("[\"RULEBOOK:rules:2:page=18\"]")), resolution.effectiveSelection());
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));
        var rag = new ObjectMapper().createObjectNode().put("query", "safe structured context");
        String allowedKey = "RULEBOOK:rules:2:page=18";
        var request = new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "schema-op", "look",
                SELECTED_ENDPOINT_ID, "openai", "gpt-5", "high", SELECTED_ENDPOINT_ID,
                Instant.EPOCH.toString(), "openai", "gpt-5", "ROLE=RUNTIME_GM", rag, List.of(allowedKey));

        controller.runtimeTurn("service-secret", request);

        org.junit.jupiter.api.Assertions.assertEquals("gpt-5", seenSelection.get().model());
        org.junit.jupiter.api.Assertions.assertEquals("high", seenSelection.get().reasoning());
        org.junit.jupiter.api.Assertions.assertEquals(rag, seenRag.get());
        org.junit.jupiter.api.Assertions.assertEquals("https://dnd-master.local/contracts/ai-game-master/v1/runtime-turn-response.json",
                seenSchema.get().path("$id").asText());
        var evidenceSchema = seenSchema.get().path("properties").path("판정제안").path("anyOf").get(1)
                .path("properties").path("근거키");
        org.junit.jupiter.api.Assertions.assertTrue(evidenceSchema.path("items").path("pattern").asText().contains("\\u3000"));
        org.junit.jupiter.api.Assertions.assertEquals(1, evidenceSchema.path("minItems").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(allowedKey, evidenceSchema.path("items").path("enum").get(0).asText());
        var checkBranches = seenSchema.get().path("properties").path("판정제안").path("anyOf");
        for (var branch : checkBranches) {
            org.junit.jupiter.api.Assertions.assertEquals("object", branch.path("type").asText());
            org.junit.jupiter.api.Assertions.assertFalse(branch.path("additionalProperties").asBoolean());
            org.junit.jupiter.api.Assertions.assertEquals(11, branch.path("required").size());
            branch.path("properties").fields().forEachRemaining(field ->
                    org.junit.jupiter.api.Assertions.assertTrue(field.getValue().has("type"),
                            "provider strict schema branch property requires a type: " + field.getKey()));
        }
    }

    @Test
    void runtime_turn_rejects_missing_empty_and_blank_check_evidence_but_allows_no_check(CapturedOutput output) {
        for (String evidence : List.of("MISSING", "[]", "[\" \"]")) {
            String result = evidence;
            var adapter = selectedAdapter((operation, prompt, requested) -> runtimeResponse(result));
            var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> controller.runtimeTurn(
                    "service-secret", new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "invalid-" + evidence,
                            "look", List.of())));
        }
        var noCheck = selectedAdapter((operation, prompt, requested) -> runtimeResponse("null"));
        var response = new TypedAgentContractController(noCheck, new ObjectMapper(), new ApiRequestGuard("service-secret"))
                .runtimeTurn("service-secret", new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "no-check", "look", List.of()));
        org.junit.jupiter.api.Assertions.assertFalse(response.checkProposal().required());
        if ("true".equalsIgnoreCase(System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED"))) {
            org.assertj.core.api.Assertions.assertThat(output).contains("dev_gm_runtime_schema outcome=rejected")
                    .contains("expectedFields=").contains("actualFields=").contains("evidenceCount=0")
                    .doesNotContain("look");
        }
    }

    @Test
    void runtime_turn_logs_safe_json_parse_location_for_malformed_provider_response(CapturedOutput output) {
        String malformed = "{\"privatePlayerText\": ";
        var adapter = selectedAdapter((operation, prompt, requested) -> malformed);
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> controller.runtimeTurn(
                "service-secret", new TypedAgentContractController.RuntimeTurnRequest(
                        SOLO_PLAYER_ID, "malformed-json", "private player input", List.of())));

        if ("true".equalsIgnoreCase(System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED"))) {
            org.assertj.core.api.Assertions.assertThat(output).contains("parseFailureClass=")
                    .contains("parseLine=1").contains("parseColumn=")
                    .doesNotContain(malformed).doesNotContain("private player input");
        }
    }

    @Test
    void runtime_turn_schema_matches_java_blank_whitespace_for_evidence_keys() throws Exception {
        var mapper = new ObjectMapper();
        var schemaJson = mapper.readTree(TypedAgentContractController.class
                .getResourceAsStream("/ai-game-master/schemas/runtime-turn-response.json"));
        var schema = com.networknt.schema.SchemaRegistry.withDefaultDialect(com.networknt.schema.SpecificationVersion.DRAFT_2020_12)
                .getSchema(schemaJson);
        var validCheck = providerCompatibleRuntimeResponse(mapper, "[\"valid-key\"]");
        org.junit.jupiter.api.Assertions.assertTrue(schema.validate(validCheck).isEmpty());
        var falseCheck = providerCompatibleRuntimeResponse(mapper, "null");
        org.junit.jupiter.api.Assertions.assertTrue(schema.validate(falseCheck).isEmpty());
        for (String evidence : List.of("MISSING", "[]", "[\"\u3000\"]")) {
            var invalid = providerCompatibleRuntimeResponse(mapper, "[\"valid-key\"]");
            if (evidence.equals("MISSING")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("판정제안")).remove("근거키");
            } else {
                ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("판정제안")).set("근거키", mapper.readTree(evidence));
            }
            org.junit.jupiter.api.Assertions.assertFalse(schema.validate(invalid).isEmpty());
        }
        var nonBreakingSpace = providerCompatibleRuntimeResponse(mapper, "[\"\u00a0\"]");
        org.junit.jupiter.api.Assertions.assertTrue(schema.validate(nonBreakingSpace).isEmpty());
    }

    @Test
    void runtime_turn_schema_limits_check_evidence_to_supplied_keys_when_available() throws Exception {
        String allowedKey = "RULEBOOK:rules:2:page=18";
        var adapter = new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        GmCompletionAdapter capture = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                throw new AssertionError("resolved provider path must be used");
            }
            @Override public <T> GmCompletionResult<T> completeWithResolution(UUID owner, String operation, String prompt,
                    StructuredResponseParser<T> parser, RequestedGmProviderSelection requested,
                    GmProviderSelectionResolver.EndpointResolution resolution,
                    com.fasterxml.jackson.databind.JsonNode ragContext, com.fasterxml.jackson.databind.JsonNode outputSchema) {
                adapter.set(outputSchema);
                String response = runtimeResponse("[\"" + allowedKey + "\"]");
                return new GmCompletionResult<>(parser.parse(response), resolution.effectiveSelection());
            }
        };
        var controller = new TypedAgentContractController(capture, new ObjectMapper(), new ApiRequestGuard("service-secret"));
        controller.runtimeTurn("service-secret", new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID,
                "allowed-key", "look", SELECTED_ENDPOINT_ID, "openai", "gpt-5", "high", SELECTED_ENDPOINT_ID,
                Instant.EPOCH.toString(), "openai", "gpt-5", "ROLE=RUNTIME_GM", null, List.of(allowedKey)));
        var allowedSchema = adapter.get();
        var schemaValidator = com.networknt.schema.SchemaRegistry.withDefaultDialect(com.networknt.schema.SpecificationVersion.DRAFT_2020_12)
                .getSchema(allowedSchema);
        var allowedResponse = providerCompatibleRuntimeResponse(new ObjectMapper(), "[\"" + allowedKey + "\"]");
        var unknownResponse = providerCompatibleRuntimeResponse(new ObjectMapper(), "[\"RULEBOOK:other:page=1\"]");
        org.junit.jupiter.api.Assertions.assertTrue(schemaValidator.validate(allowedResponse).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(schemaValidator.validate(unknownResponse).isEmpty());

        var noEvidenceResponse = controller.runtimeTurn("service-secret", new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID,
                "no-evidence", "look", SELECTED_ENDPOINT_ID, "openai", "gpt-5", "high", SELECTED_ENDPOINT_ID,
                Instant.EPOCH.toString(), "openai", "gpt-5", "ROLE=RUNTIME_GM", null, List.of()));
        org.junit.jupiter.api.Assertions.assertTrue(noEvidenceResponse.checkProposal().required());
        org.junit.jupiter.api.Assertions.assertEquals(2, adapter.get().path("properties").path("판정제안").path("anyOf").size());
    }

    @Test
    void runtime_turn_parser_accepts_provider_required_nullable_unused_fields() throws Exception {
        var mapper = new ObjectMapper();
        String responseJson = mapper.writeValueAsString(providerCompatibleRuntimeResponse(mapper, "null"));
        var controller = new TypedAgentContractController(
                selectedAdapter((operation, prompt, requested) -> responseJson), mapper,
                new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "provider-nullable", "살펴본다", List.of()));

        org.junit.jupiter.api.Assertions.assertFalse(response.checkProposal().required());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), response.runtimeFacts());
        org.junit.jupiter.api.Assertions.assertFalse(response.completion().complete());
        org.junit.jupiter.api.Assertions.assertNull(response.situation().location());
    }

    private static com.fasterxml.jackson.databind.JsonNode providerCompatibleRuntimeResponse(ObjectMapper mapper, String evidence) throws Exception {
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(runtimeResponse(evidence));
        var situation = (com.fasterxml.jackson.databind.node.ObjectNode) response.path("situation");
        for (String field : List.of("location", "problem", "threat", "goal", "reference")) situation.putNull(field);
        response.putArray("runtimeFacts");
        var completion = response.putObject("completion").put("complete", false);
        completion.putArray("resolvedObjectiveIds");
        completion.putArray("satisfiedResolutionCriteriaIds");
        completion.put("concludingScene", "");
        response.putArray("citedEvidence");
        var proposal = (com.fasterxml.jackson.databind.node.ObjectNode) response.path("판정제안");
        if (evidence.equals("null")) {
            for (String field : List.of("이유", "판정능력또는기술", "대상캐릭터ID", "굴림주체", "굴림식", "성공시결과", "실패시결과")) proposal.putNull(field);
            proposal.putNull("보정치");
            proposal.putNull("난이도");
            proposal.putArray("근거키");
        }
        return response;
    }

    private static String runtimeResponse(String evidenceKeys) {
        String proposal = "null".equals(evidenceKeys) ? "{\"필요\":false}" : "{\"필요\":true,\"이유\":\"단서를 확인합니다.\",\"판정능력또는기술\":\"지각\",\"대상캐릭터ID\":\"" + SOLO_PLAYER_ID + "\",\"굴림주체\":\"플레이어\",\"굴림식\":\"1d20\",\"보정치\":0,\"난이도\":10," +
                ("MISSING".equals(evidenceKeys) ? "" : "\"근거키\":" + evidenceKeys + ",") + "\"성공시결과\":\"단서를 찾습니다.\",\"실패시결과\":\"단서를 찾지 못합니다.\"}";
        return "{\"scene\":\"복도\",\"judgment\":\"판정\",\"narration\":\"복도가 조용합니다.\",\"situation\":" + situation("FALLBACK", "") +
                ",\"combatStart\":false,\"mapEntryRequested\":false,\"combatEnemies\":[],\"판정제안\":" + proposal + "}";
    }

    @Test
    void runtime_turn_reads_optional_runtime_facts_from_the_typed_response() {
        var adapter = selectedAdapter((operation, value, requested) -> "{\"scene\":\"양조장\",\"judgment\":\"협상 가능\",\"narration\":\"글로우킨이 조건을 제안합니다.\",\"situation\":"
                + situation("SCENARIO", "cellar")
                + ",\"combatStart\":false,\"mapEntryRequested\":false,\"판정제안\":{\"필요\":false},\"combatEnemies\":[],"
                + "\"runtimeFacts\":[{\"subject\":\"보상\",\"content\":\"글로우킨이 30골드를 제안했습니다.\"}],"
                + "\"citedEvidence\":[\"RULEBOOK:rules:2:page=63\",\"STORYBOOK:story:2:page=3\"]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "PLAYER_ACTION", List.of()));

        org.junit.jupiter.api.Assertions.assertEquals(1, response.runtimeFacts().size());
        org.junit.jupiter.api.Assertions.assertEquals("보상", response.runtimeFacts().get(0).subject());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("RULEBOOK:rules:2:page=63", "STORYBOOK:story:2:page=3"), response.citedEvidence());
    }

    @Test
    void runtime_turn_accepts_a_structured_player_check_proposal() {
        String key = "RULEBOOK:rules:2:page=18";
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"복도\",\"judgment\":\"지각 판정\","
                + "\"narration\":\"굴림 결과를 기다립니다.\",\"situation\":" + situation("FALLBACK", "")
                + ",\"combatStart\":false,\"mapEntryRequested\":false,\"combatEnemies\":[],"
                + "\"citedEvidence\":[\"" + key + "\"],"
                + "\"판정제안\":{\"필요\":true,\"이유\":\"숨은 움직임을 확인합니다.\",\"판정능력또는기술\":\"지각\","
                + "\"대상캐릭터ID\":\"" + SOLO_PLAYER_ID + "\",\"굴림주체\":\"플레이어\",\"굴림식\":\"1d20\","
                + "\"보정치\":2,\"난이도\":12,\"근거키\":[\"" + key + "\"],"
                + "\"성공시결과\":\"움직임의 방향을 파악합니다.\",\"실패시결과\":\"확신할 단서를 찾지 못합니다.\"}}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "살펴본다", List.of()));

        org.junit.jupiter.api.Assertions.assertTrue(response.checkProposal().required());
        org.junit.jupiter.api.Assertions.assertEquals("플레이어", response.checkProposal().rollMethod());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(key), response.checkProposal().evidenceKeys());
    }

    @Test
    void opening_rejects_player_visible_text_written_only_in_a_foreign_language() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"양조장\",\"judgment\":\"안전함\","
                + "\"narration\":\"The room is quiet.\",\"situation\":" + situation("SCENARIO", "cellar-rat-ambush")
                + ",\"combatStart\":false,\"mapEntryRequested\":false,\"판정제안\":{\"필요\":false},\"combatEnemies\":[]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "SESSION_OPENING", List.of())));
    }

    @Test
    void normal_action_rejects_player_narration_written_only_in_english() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> """
                {"scene":"Brewery cellar","judgment":"No check required",
                 "narration":"The room is quiet.","situation":{"kind":"CONTINUE",
                   "location":"Brewery cellar","problem":"Rats in cellar","threat":"Supplies at risk",
                   "goal":"Investigate cellar","basis":"FALLBACK","reference":"","required":true},
                 "combatStart":false,"mapEntryRequested":false,"판정제안":{"필요":false},
                 "combatEnemies":[],"runtimeFacts":[{"subject":"Brewery owner","content":"Requested help"}]}
                """);
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "살펴본다", List.of())));
    }

    @Test
    void normal_action_accepts_english_internal_values_with_korean_player_narration() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> """
                {"scene":"Brewery cellar","judgment":"No check required",
                 "narration":"NPC Bob이 지하실 입구에서 기다립니다. 1d20 굴림은 필요하지 않습니다.",
                 "situation":{"kind":"CONTINUE","location":"Brewery cellar",
                   "problem":"Rats in cellar","threat":"Supplies at risk","goal":"Investigate cellar",
                   "basis":"FALLBACK","reference":"","required":true},
                 "combatStart":false,"mapEntryRequested":false,"판정제안":{"필요":false},
                 "combatEnemies":[],"runtimeFacts":[{"subject":"Brewery owner","content":"Requested help"}]}
                """);
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "살펴본다", List.of()));

        org.junit.jupiter.api.Assertions.assertEquals("Brewery cellar", response.scene());
        org.junit.jupiter.api.Assertions.assertEquals("No check required", response.judgment());
        org.junit.jupiter.api.Assertions.assertEquals("NPC Bob이 지하실 입구에서 기다립니다. 1d20 굴림은 필요하지 않습니다.", response.narration());
    }

    @Test
    void normal_action_accepts_a_situation_with_only_the_provided_core_element() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> """
                {"scene":"양조장 지하실","judgment":"문제를 확인했다","narration":"쥐가 통로를 막고 있습니다.",
                 "situation":{"kind":"TRANSITION","problem":"쥐가 통로를 막고 있다",
                   "basis":"FALLBACK","reference":"","required":true},
                 "combatStart":false,"mapEntryRequested":false,"판정제안":{"필요":false},"combatEnemies":[]}
                """);
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "살펴본다", List.of()));

        org.junit.jupiter.api.Assertions.assertNull(response.situation().location());
        org.junit.jupiter.api.Assertions.assertEquals("쥐가 통로를 막고 있다", response.situation().problem());
        org.junit.jupiter.api.Assertions.assertNull(response.situation().threat());
        org.junit.jupiter.api.Assertions.assertNull(response.situation().goal());
    }

    @Test
    void opening_accepts_korean_text_with_names_abbreviations_and_dice_notation() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"Potent Brew 양조장\",\"judgment\":\"HP 변화 없이 DC 12 확인과 1d20 굴림을 기다립니다.\","
                + "\"narration\":\"NPC Bob이 입구에서 기다립니다. 어떻게 하시겠어요?\",\"situation\":" + situation("SCENARIO", "cellar-rat-ambush")
                + ",\"combatStart\":false,\"mapEntryRequested\":false,\"판정제안\":{\"필요\":false},\"combatEnemies\":[]}");
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "SESSION_OPENING", List.of()));

        org.junit.jupiter.api.Assertions.assertEquals("Potent Brew 양조장", response.scene());
        org.junit.jupiter.api.Assertions.assertEquals("HP 변화 없이 DC 12 확인과 1d20 굴림을 기다립니다.", response.judgment());
        org.junit.jupiter.api.Assertions.assertEquals("NPC Bob이 입구에서 기다립니다. 어떻게 하시겠어요?", response.narration());
    }

    @Test
    void opening_accepts_original_language_internal_values_with_korean_narration() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> """
                {"scene":"Wizard’s Tower Brewing Co.","judgment":"No check required",
                 "narration":"양조장 입구에서 주인이 도움을 요청합니다. 어떻게 하시겠어요?",
                 "situation":{"kind":"CONTINUE","location":"Wizard’s Tower Brewing Co.",
                   "problem":"Rats in cellar","threat":"Supplies at risk","goal":"Investigate cellar",
                   "basis":"FALLBACK","reference":"","required":true},
                 "combatStart":false,"mapEntryRequested":false,"판정제안":{"필요":false},
                 "combatEnemies":[{"mode":"INSTANT","enemyKey":"giant-rat","name":"Giant Rat","count":1}],
                 "runtimeFacts":[{"subject":"Brewery owner","content":"Requested help"}]}
                """);
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "SESSION_OPENING", List.of()));

        org.junit.jupiter.api.Assertions.assertEquals("Wizard’s Tower Brewing Co.", response.scene());
        org.junit.jupiter.api.Assertions.assertEquals("No check required", response.judgment());
        org.junit.jupiter.api.Assertions.assertEquals("양조장 입구에서 주인이 도움을 요청합니다. 어떻게 하시겠어요?", response.narration());
    }

    @Test
    void opening_rejects_player_visible_text_without_korean_letters() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"양조장\",\"judgment\":\"안전함\","
                + "\"narration\":\"123 !!!\",\"situation\":" + situation("SCENARIO", "cellar-rat-ambush")
                + ",\"combatStart\":false,\"mapEntryRequested\":false,\"판정제안\":{\"필요\":false},\"combatEnemies\":[]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "SESSION_OPENING", List.of())));
    }

    @Test
    void runtime_turn_rejects_a_missing_combat_start_decision() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"brewery\",\"judgment\":\"safe\",\"narration\":\"방 안은 조용합니다.\"}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "look around", List.of())));
    }

    @Test
    void runtime_turn_rejects_fields_outside_its_response_schema() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"복도\",\"judgment\":\"안전\","
                + "\"narration\":\"복도가 조용합니다.\",\"situation\":" + situation("FALLBACK", "")
                + ",\"combatStart\":false,\"mapEntryRequested\":false,\"판정제안\":{\"필요\":false},"
                + "\"combatEnemies\":[],\"unknown\":true}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "look around", List.of())));
    }

    @Test
    void runtime_turn_rejects_unknown_request_fields_before_provider_execution() throws Exception {
        TypedAgentContractController controller = new TypedAgentContractController(
                selectedAdapter((operation, prompt, requested) -> "{}"), new ObjectMapper(), new ApiRequestGuard("service-secret"));
        var body = new ObjectMapper().readTree("""
                {"soloPlayerId":"%s","operationKey":"op","action":"look","endpointId":"%s",
                 "provider":"openai","model":"gpt-5","reasoning":"low","effectiveEndpointId":"%s",
                 "effectiveEndpointVersion":"v1","effectiveProvider":"openai","effectiveModel":"gpt-5",
                 "prompt":"role","unexpected":true}
                """.formatted(SOLO_PLAYER_ID, SELECTED_ENDPOINT_ID, SELECTED_ENDPOINT_ID));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret", body));
    }

    @Test
    void runtime_turn_rejects_combat_without_a_structured_enemy_name() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"brewery\",\"judgment\":\"combat\",\"narration\":\"문이 벌컥 열립니다.\",\"situation\":" + situation("FALLBACK", "") + ",\"combatStart\":true,\"mapEntryRequested\":false,\"판정제안\":{\"필요\":false},\"combatEnemies\":[]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "look around", List.of())));
    }

    @Test
    void runtime_turn_accepts_an_explicit_instant_combat_mode_without_a_scenario_id() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"cellar\",\"judgment\":\"critical failure\","
                + "\"narration\":\"소리를 듣고 거대 쥐가 나타납니다.\",\"situation\":" + situation("FALLBACK", "") + ",\"combatStart\":true,\"mapEntryRequested\":false,"
                + "\"판정제안\":{\"필요\":false},\"combatEnemies\":[{\"mode\":\"INSTANT\",\"scenarioId\":\"\","
                + "\"enemyKey\":\"giant-rat\",\"name\":\"Giant Rat\",\"count\":1}]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "critical failure", List.of()));

        org.junit.jupiter.api.Assertions.assertEquals("INSTANT", response.combatEnemies().get(0).mode());
        org.junit.jupiter.api.Assertions.assertEquals("", response.combatEnemies().get(0).scenarioId());
    }

    private static GmCompletionAdapter emptyAdapter() {
        return new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                throw new AssertionError("provider must not be called by authorization tests");
            }
        };
    }

    private static GmCompletionAdapter selectedAdapter(SelectedJson response) {
        return new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                throw new AssertionError("selected provider path must be used");
            }

            @Override
            public <T> GmCompletionResult<T> completeWithSelection(UUID soloPlayerId, String operation,
                    String prompt, StructuredResponseParser<T> parser, RequestedGmProviderSelection requested) {
                return new GmCompletionResult<>(parser.parse(response.json(operation, prompt, requested)), effectiveSelection(requested));
            }
        };
    }

    private static EffectiveGmProviderSelection effectiveSelection(RequestedGmProviderSelection requested) {
        UUID endpointId = requested.endpointId() == null ? SELECTED_ENDPOINT_ID : requested.endpointId();
        return new EffectiveGmProviderSelection(endpointId, Instant.EPOCH, requested.provider(), requested.model(), requested.reasoning());
    }

    private static com.dndmaster.aigamemaster.infrastructure.ai.GmProviderSelectionResolver.EndpointResolution resolution(
            String model, RequestedGmProviderSelection requested) {
        var endpoint = new com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint(SELECTED_ENDPOINT_ID,
                "selected", com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint.Provider.OPENAI_COMPATIBLE,
                java.net.URI.create("http://localhost"), model, null, true, Instant.EPOCH);
        return new com.dndmaster.aigamemaster.infrastructure.ai.GmProviderSelectionResolver.EndpointResolution(endpoint,
                new EffectiveGmProviderSelection(SELECTED_ENDPOINT_ID, Instant.EPOCH, "openai", model, requested.reasoning()));
    }

    @FunctionalInterface
    private interface SelectedJson {
        String json(String operation, String prompt, RequestedGmProviderSelection requested);
    }

    private static String situation(String basis, String reference) {
        return "{\"kind\":\"TRANSITION\",\"location\":\"지하 저장고\",\"problem\":\"위협을 찾기\","
                + "\"threat\":\"적대적인 생물\",\"goal\":\"안전 확보\",\"basis\":\"" + basis
                + "\",\"reference\":\"" + reference + "\",\"required\":true}";
    }

}

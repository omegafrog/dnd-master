package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.aigamemaster.infrastructure.ai.EffectiveGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionResult;
import com.dndmaster.aigamemaster.infrastructure.ai.RequestedGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser;
import com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException;
import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TypedAgentContractControllerTest {
    private static final UUID SOLO_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000321");
    private static final UUID SELECTED_ENDPOINT_ID = UUID.fromString("00000000-0000-0000-0000-000000000322");

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
                String response = operation.startsWith("scenario-compilation") ? "{\"status\":\"READY\",\"scenarioModel\":{\"schemaVersion\":1}}"
                        : operation.startsWith("scenario-lookup") ? "{\"status\":\"NOT_FOUND\",\"answer\":\"\",\"supportingElementIds\":[]}"
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
    void scenario_lookup_prompt_declares_the_json_contract_required_by_its_parser() {
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
                new TypedAgentContractController.ScenarioLookupRequest(SOLO_PLAYER_ID, "opening", Map.of()));

        org.junit.jupiter.api.Assertions.assertEquals("NOT_FOUND", response.status());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("OUTPUT_CONTRACT"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("status must be FOUND or NOT_FOUND"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Do not use markdown, code fences, or any other text"));
    }

    @Test
    void scenario_compilation_prompt_requires_storybook_grounded_encounters_and_exact_sources() {
        AtomicReference<String> prompt = new AtomicReference<>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String value,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                prompt.set(value);
                return parser.parse("{\"status\":\"READY\",\"scenarioModel\":{\"schemaVersion\":1}}");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        ResponseEntity<?> result = controller.scenarioCompilation("service-secret", new TypedAgentContractController.ScenarioCompilationRequest(
                SOLO_PLAYER_ID, "scenario-compilation:test", "DOCUMENT_ID=abc\\nEXTRACTION_VERSION=1\\nLOCATOR=page:2\\nTEXT=Eight Giant Rats begin combat."));

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.OK, result.getStatusCode());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Find essential combat encounters"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Never invent or rewrite a source reference"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("scenarioModel.encounters"));
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
    void opening_rejects_player_visible_text_that_contains_a_foreign_language() {
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
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"brewery\",\"judgment\":\"safe\",\"narration\":\"The room is quiet.\"}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "look around", List.of())));
    }

    @Test
    void runtime_turn_rejects_combat_without_a_structured_enemy_name() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"brewery\",\"judgment\":\"combat\",\"narration\":\"The door bursts open.\",\"situation\":" + situation("FALLBACK", "") + ",\"combatStart\":true,\"mapEntryRequested\":false,\"판정제안\":{\"필요\":false},\"combatEnemies\":[]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "look around", List.of())));
    }

    @Test
    void runtime_turn_accepts_an_explicit_instant_combat_mode_without_a_scenario_id() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"cellar\",\"judgment\":\"critical failure\","
                + "\"narration\":\"The noise draws a giant rat.\",\"situation\":" + situation("FALLBACK", "") + ",\"combatStart\":true,\"mapEntryRequested\":false,"
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

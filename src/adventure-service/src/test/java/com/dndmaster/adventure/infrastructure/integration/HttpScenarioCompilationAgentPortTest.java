package com.dndmaster.adventure.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpScenarioCompilationAgentPortTest {
    @Test
    void retains_safe_remote_failure_code_and_correlation_id_without_copying_response_text() {
        var failure = HttpScenarioCompilationAgentPort.decodeFailure(503,
                "{\"code\":\"AI_EXECUTION_CONNECTION_UNAVAILABLE\",\"correlationId\":\"scenario-compilation:compile-123\","
                        + "\"rootCauseClass\":\"AiExecutionUnavailableException\",\"retryable\":true,\"debug\":\"private prompt\"}",
                "scenario-compilation:fallback");

        assertThat(failure.httpStatus()).isEqualTo(503);
        assertThat(failure.code()).isEqualTo("AI_EXECUTION_CONNECTION_UNAVAILABLE");
        assertThat(failure.correlationId()).isEqualTo("scenario-compilation:compile-123");
        assertThat(failure.rootCauseClass()).isEqualTo("AiExecutionUnavailableException");
        assertThat(failure.retryable()).isTrue();
        assertThat(failure.getMessage()).doesNotContain("private prompt");
    }

    @Test
    void falls_back_to_http_status_and_operation_key_for_unstructured_remote_errors() {
        var failure = HttpScenarioCompilationAgentPort.decodeFailure(500, "not-json", "scenario-compilation:compile-456");

        assertThat(failure.code()).isEqualTo("SCENARIO_COMPILATION_AGENT_HTTP_500");
        assertThat(failure.correlationId()).isEqualTo("scenario-compilation:compile-456");
        assertThat(failure.rootCauseClass()).isEqualTo("RemoteScenarioCompilationFailure");
        assertThat(failure.retryable()).isTrue();
    }

    @Test
    void convertsStructuredStartingSituationDetailsToReadableText() {
        Map<String, Object> situation = new LinkedHashMap<>();
        situation.put("opening", "You arrive at the brewery.");
        situation.put("location", "The cellar entrance.");
        Map<String, Object> model = new HashMap<>(Map.of("startingSituation", situation));

        HttpScenarioCompilationAgentPort.normalizeStartingSituation(model);

        assertThat(model.get("startingSituation"))
                .isEqualTo("opening: You arrive at the brewery.; location: The cellar entrance.");
    }

    @Test
    void normalizesAnIntegralDecimalSchemaVersionFromTheAgent() {
        Map<String, Object> model = new HashMap<>(Map.of("schemaVersion", "2.0"));

        HttpScenarioCompilationAgentPort.normalizeSchemaVersion(model);

        assertThat(model.get("schemaVersion")).isEqualTo(2);
    }

    @Test
    void leavesNonIntegralSchemaVersionForContractValidation() {
        Map<String, Object> model = new HashMap<>(Map.of("schemaVersion", "2.5"));

        HttpScenarioCompilationAgentPort.normalizeSchemaVersion(model);

        assertThat(model.get("schemaVersion")).isEqualTo("2.5");
    }

    @Test
    void restoresElementTypesFromTheirScenarioModelCollectionsWhenTheAgentOmitsThem() {
        Map<String, Object> actor = new HashMap<>(Map.of("elementId", "npc-1", "attributes", Map.of(), "sourceRefs", List.of()));
        Map<String, Object> encounter = new HashMap<>(Map.of("elementId", "rats", "attributes", Map.of(), "sourceRefs", List.of()));
        Map<String, Object> model = new HashMap<>(Map.of("actors", List.of(actor), "encounters", List.of(encounter)));

        HttpScenarioCompilationAgentPort.normalizeElementTypes(model);

        assertThat(elementType(model, "actors")).isEqualTo("actor");
        assertThat(elementType(model, "encounters")).isEqualTo("combat-scenario");
    }

    @Test
    void preservesExplicitTypesAndUsesAnElementTypeAliasWhenPresent() {
        Map<String, Object> actor = new HashMap<>(Map.of("elementId", "npc-1", "type", "story-character",
                "attributes", Map.of(), "sourceRefs", List.of()));
        Map<String, Object> location = new HashMap<>(Map.of("elementId", "cellar", "elementType", "brewery-cellar",
                "attributes", Map.of(), "sourceRefs", List.of()));
        Map<String, Object> model = new HashMap<>(Map.of("actors", List.of(actor), "locations", List.of(location)));

        HttpScenarioCompilationAgentPort.normalizeElementTypes(model);

        assertThat(elementType(model, "actors")).isEqualTo("story-character");
        assertThat(elementType(model, "locations")).isEqualTo("brewery-cellar");
    }

    private static Object elementType(Map<String, Object> model, String collection) {
        Object element = ((List<?>) model.get(collection)).getFirst();
        return ((Map<?, ?>) element).get("type");
    }
}

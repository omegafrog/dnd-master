package com.dndmaster.adventure.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationAgentPort.ScenarioCompilationAgentRequest;
import com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

@ExtendWith(OutputCaptureExtension.class)
class HttpScenarioCompilationAgentPortTest {
    @Test
    void scenario_model_deserialization_logs_only_path_and_json_shapes(CapturedOutput output) throws Exception {
        String privateValue = "PRIVATE_SCENARIO_MODEL_VALUE";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/gm/scenario-compilation", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = ("{\"status\":\"READY\",\"scenarioModel\":{\"schemaVersion\":\""
                    + privateValue + "\",\"actors\":[],\"locations\":[],\"objectives\":[],"
                    + "\"revelations\":[],\"encounters\":[],\"relationships\":[],"
                    + "\"resolutionCriteria\":[],\"startingSituation\":\"\"},\"diagnostics\":[]}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        String previous = System.getProperty(DevelopmentDiagnostics.FLAG);
        System.setProperty(DevelopmentDiagnostics.FLAG, "true");
        server.start();
        try {
            var port = new HttpScenarioCompilationAgentPort(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "internal-secret");

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> port.compile(new ScenarioCompilationAgentRequest("scenario-compilation:invalid-model",
                            "private Storybook excerpt")));

            assertThat(output.getAll()).contains("dev_scenario_compilation stage=output_validation")
                    .contains("operationKey=scenario-compilation:invalid-model")
                    .contains("code=SCENARIO_MODEL_TYPE_MISMATCH")
                    .contains("path=scenarioModel.schemaVersion")
                    .contains("expected=int")
                    .contains("actual=VALUE_STRING")
                    .doesNotContain(privateValue)
                    .doesNotContain("private Storybook excerpt");
        } finally {
            server.stop(0);
            if (previous == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
            else System.setProperty(DevelopmentDiagnostics.FLAG, previous);
        }
    }

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

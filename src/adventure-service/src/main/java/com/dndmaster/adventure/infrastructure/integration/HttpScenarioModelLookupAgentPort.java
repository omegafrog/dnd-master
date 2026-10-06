package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.ScenarioLookupResult;
import com.dndmaster.adventure.application.runtime.ScenarioModelLookupAgentPort;
import com.dndmaster.adventure.application.runtime.ScenarioModelLookupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Adapter for the AI ScenarioModel lookup endpoint; it sends no RAG or mutation capability. */
public final class HttpScenarioModelLookupAgentPort implements ScenarioModelLookupAgentPort {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HttpScenarioModelLookupAgentPort.class);
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpScenarioModelLookupAgentPort(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        this.internalToken = Objects.requireNonNull(internalToken);
    }

    @Override
    public ScenarioLookupResult lookup(ScenarioModelLookupRequest request) {
        return lookup(new UUID(0, 0), request);
    }

    @Override
    public ScenarioLookupResult lookup(UUID soloPlayerId, ScenarioModelLookupRequest request) {
        try {
            String body = mapper.writeValueAsString(new LookupRequest(soloPlayerId, request.query(), request.lockedScenarioModel()));
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("internal/gm/scenario-lookup"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                devLog("dev_agent_http operation=scenario_lookup outcome=http_error playerId={} queryFingerprint={} status={} response={}",
                        soloPlayerId, com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(request.query()),
                        response.statusCode(), com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.safeBody(response.body()));
                throw new IllegalStateException("scenario lookup returned " + response.statusCode());
            }
            LookupResponse result = mapper.readValue(response.body(), LookupResponse.class);
            devLog("dev_agent_http operation=scenario_lookup outcome=success playerId={} queryFingerprint={} resultStatus={} supportingElementIds={}",
                    soloPlayerId, com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(request.query()),
                    result.status(), result.supportingElementIds());
            if ("NOT_FOUND".equals(result.status())) return ScenarioLookupResult.notFound();
            if (!"FOUND".equals(result.status())) throw new IllegalStateException("invalid scenario lookup status");
            return ScenarioLookupResult.found(result.answer(), result.supportingElementIds());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ScenarioLookupFailureException("scenario lookup interrupted", exception);
        } catch (ScenarioLookupFailureException exception) {
            throw exception;
        } catch (Exception exception) {
            devLog("dev_agent_http operation=scenario_lookup outcome=exception playerId={} queryFingerprint={} failureClass={} failure={}",
                    soloPlayerId, com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(request.query()),
                    exception.getClass().getName(), exception.getMessage());
            throw new ScenarioLookupFailureException("scenario lookup failed", exception);
        }
    }

    private static void devLog(String pattern, Object... args) {
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) LOGGER.warn(pattern, args);
    }

    record LookupRequest(UUID soloPlayerId, String query, Object lockedScenarioModel) { }
    record LookupResponse(String status, String answer, List<String> supportingElementIds) {
        LookupResponse {
            supportingElementIds = supportingElementIds == null ? List.of() : List.copyOf(supportingElementIds);
        }
    }
}

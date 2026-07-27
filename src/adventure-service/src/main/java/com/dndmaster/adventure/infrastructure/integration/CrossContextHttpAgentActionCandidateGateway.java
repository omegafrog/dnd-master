package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.AgentActionCandidate;
import com.dndmaster.adventure.application.runtime.AgentActionCandidatePort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class CrossContextHttpAgentActionCandidateGateway implements AgentActionCandidatePort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpAgentActionCandidateGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public AgentActionCandidate propose(Request request) {
        try {
            String body = objectMapper.writeValueAsString(new CandidateRequest(
                    request.adventureId().value(), request.ownerPlayerId().value(), request.characterSheetId().value(),
                    request.characterSheet().name(), request.characterSheet().level(), request.context().currentScene()));
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/agent-actions"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("agent action proposal failed with status " + response.statusCode());
            }
            CandidateResponse candidate = objectMapper.readValue(response.body(), CandidateResponse.class);
            return new AgentActionCandidate(candidate.turnId(), candidate.commandId(), request.characterSheetId(), candidate.action());
        } catch (IOException exception) {
            throw new IllegalStateException("agent action proposal failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("agent action proposal interrupted", exception);
        }
    }

    record CandidateRequest(java.util.UUID adventureId, java.util.UUID ownerPlayerId, java.util.UUID characterSheetId,
                            String characterName, int level, String currentScene) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CandidateResponse(java.util.UUID turnId, java.util.UUID commandId, String action) {}
}

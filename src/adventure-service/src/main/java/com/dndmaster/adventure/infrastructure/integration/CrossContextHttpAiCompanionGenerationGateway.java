package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.session.AiCompanionGenerationPort;
import com.dndmaster.adventure.domain.adventure.AiCompanionCandidate;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

/** Uses the active back-office Agent Endpoint through ai-game-master. */
public final class CrossContextHttpAiCompanionGenerationGateway implements AiCompanionGenerationPort {
    private final HttpClient client; private final URI baseUri; private final Duration timeout; private final ObjectMapper mapper; private final String token;
    public CrossContextHttpAiCompanionGenerationGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String token) { this.client=client; this.baseUri=baseUri; this.timeout=timeout; this.mapper=mapper; this.token=token; }
    @Override public AiCompanionCandidate generate(SessionId session, OwnerPlayerId owner) {
        try {
            String body = mapper.writeValueAsString(Map.of("sessionId", session.value()));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/companion-candidates")).timeout(timeout)
                    .header("Content-Type", "application/json").header("X-Internal-Token", token).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()/100 != 2) throw new IllegalStateException("AI companion generation failed: " + response.statusCode());
            var json = mapper.readTree(response.body());
            return new AiCompanionCandidate(java.util.UUID.randomUUID(), json.path("name").asText(), json.path("race").asText(), json.path("characterClass").asText(), json.path("sheetSummary").asText());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("AI companion generation interrupted", e); }
        catch (Exception e) { if (e instanceof IllegalStateException failure) throw failure; throw new IllegalStateException("AI companion generation failed", e); }
    }
}

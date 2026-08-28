package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.session.AiCompanionSheetCreationPort;
import com.dndmaster.adventure.domain.adventure.AiCompanionCandidate;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;

public final class CrossContextHttpAiCompanionCharacterSheetGateway implements AiCompanionSheetCreationPort {
    private final HttpClient client; private final URI baseUri; private final Duration timeout;
    private final ObjectMapper mapper; private final String token;
    public CrossContextHttpAiCompanionCharacterSheetGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String token) {
        this.client = client; this.baseUri = baseUri; this.timeout = timeout; this.mapper = mapper; this.token = token;
    }
    @Override public CharacterSheetId create(SessionId sessionId, OwnerPlayerId owner, AiCompanionCandidate candidate) {
        try {
            var body = new LinkedHashMap<String, Object>();
            body.put("ownerPlayerId", owner.value());
            body.put("candidateId", candidate.candidateId());
            if (candidate.name() != null) body.put("name", candidate.name());
            if (candidate.race() != null) body.put("race", candidate.race());
            if (candidate.characterClass() != null) body.put("characterClass", candidate.characterClass());
            if (candidate.sheetSummary() != null) body.put("sheetSummary", candidate.sheetSummary());
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/adventure-sessions/" + sessionId.value() + "/ai-companion-sheets"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("AI companion sheet creation failed: " + response.statusCode());
            String id = mapper.readTree(response.body()).path("characterSheetId").asText();
            if (id.isBlank()) throw new IllegalStateException("AI companion sheet response omitted characterSheetId");
            return new CharacterSheetId(java.util.UUID.fromString(id));
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("AI companion sheet creation interrupted", exception); }
        catch (Exception exception) { if (exception instanceof IllegalStateException failure) throw failure; throw new IllegalStateException("AI companion sheet creation failed", exception); }
    }
}

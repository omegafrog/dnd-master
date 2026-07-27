package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.session.CharacterSheetOwnershipPort;
import com.dndmaster.adventure.domain.adventure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class CrossContextHttpCharacterSheetOwnershipGateway implements CharacterSheetOwnershipPort {
    private final HttpClient client; private final URI baseUri; private final Duration timeout; private final ObjectMapper mapper; private final String internalToken;
    public CrossContextHttpCharacterSheetOwnershipGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken) { this.client = client; this.baseUri = baseUri; this.timeout = timeout; this.mapper = mapper; this.internalToken = internalToken; }
    public void verify(SessionId sessionId, OwnerPlayerId ownerPlayerId, CharacterSheetId sheetId) {
        try {
            var request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/adventure-sessions/" + sessionId.value() + "/character-sheets/" + sheetId.value() + "/ownership")).timeout(timeout).header("X-Internal-Token", internalToken).header("X-Owner-Player-ID", ownerPlayerId.value().toString()).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("character sheet ownership validation failed: " + response.statusCode());
            var view = mapper.readTree(response.body());
            if (!sessionId.value().toString().equals(view.path("sessionId").asText())) throw new IllegalStateException("character sheet belongs to another session");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("character sheet ownership validation interrupted", e); }
        catch (Exception e) { if (e instanceof IllegalStateException failure) throw failure; throw new IllegalStateException("character sheet ownership validation failed", e); }
    }
}

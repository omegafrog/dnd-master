package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.session.CharacterSheetDeletionPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public final class CrossContextHttpCharacterSheetDeletionGateway implements CharacterSheetDeletionPort {
    private final HttpClient client; private final URI baseUri; private final Duration timeout; private final ObjectMapper mapper; private final String token;
    public CrossContextHttpCharacterSheetDeletionGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String token) { this.client=client;this.baseUri=baseUri;this.timeout=timeout;this.mapper=mapper;this.token=token; }
    public void delete(UUID sessionId, List<UUID> ids) { try { var body=mapper.writeValueAsString(new Request(sessionId, ids)); var response=client.send(HttpRequest.newBuilder(baseUri.resolve("internal/v1/character-sheets/deletion-requests")).timeout(timeout).header("Content-Type","application/json").header("X-Internal-Token",token).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()); if(response.statusCode()/100!=2) throw new IllegalStateException("character deletion failed: "+response.statusCode()); } catch(Exception e){ if(e instanceof RuntimeException r) throw r; throw new IllegalStateException("character deletion request failed",e); } }
    private record Request(UUID sessionId, List<UUID> characterSheetIds) {}
}

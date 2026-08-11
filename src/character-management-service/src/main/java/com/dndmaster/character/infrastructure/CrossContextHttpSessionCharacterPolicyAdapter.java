package com.dndmaster.character.infrastructure;

import com.dndmaster.character.application.SessionCharacterPolicy;
import com.dndmaster.character.application.SessionCharacterPolicyPort;
import com.dndmaster.character.domain.AdventureId;
import com.dndmaster.character.domain.CharacterSheetId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class CrossContextHttpSessionCharacterPolicyAdapter implements SessionCharacterPolicyPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpSessionCharacterPolicyAdapter(HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = client; this.baseUri = baseUri; this.timeout = timeout; this.objectMapper = objectMapper;
    }

    @Override public SessionCharacterPolicy policyFor(AdventureId sessionId) { return request(sessionId, null); }
    @Override public SessionCharacterPolicy policyFor(AdventureId sessionId, CharacterSheetId sheetId) { return request(sessionId, sheetId); }

    private SessionCharacterPolicy request(AdventureId sessionId, CharacterSheetId sheetId) {
        String path = "api/v1/adventure-sessions/internal/" + sessionId.value() + "/character-policy";
        if (sheetId != null) path += "?characterSheetId=" + sheetId.value();
        try {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve(path)).timeout(timeout).header("X-Internal-Service", "character-management").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("adventure session policy lookup failed: " + response.statusCode());
            PolicyView view = objectMapper.readValue(response.body(), PolicyView.class);
            return new SessionCharacterPolicy(view.acceptingCharacterSheets(), view.nameMutable(), view.levelMutable(), view.raceMutable(), view.characterClassMutable(), view.backgroundMutable(), view.startingAbilitiesMutable(), view.characterEdition());
        } catch (Exception exception) { throw new IllegalStateException("could not load adventure session character policy", exception); }
    }

    private record PolicyView(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable,
            boolean raceMutable, boolean characterClassMutable, boolean backgroundMutable, boolean startingAbilitiesMutable,
            String characterEdition) {}
}

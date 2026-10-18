package com.dndmaster.character.infrastructure;

import com.dndmaster.character.application.AbilityLearningPreparationPort;
import com.dndmaster.character.domain.CharacterSheetId;
import com.dndmaster.character.domain.SessionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;

public final class AdventureAbilityLearningPreparationGateway implements AbilityLearningPreparationPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final String internalToken;
    private final ObjectMapper mapper;

    public AdventureAbilityLearningPreparationGateway(HttpClient client, URI baseUri, Duration timeout,
            String internalToken, ObjectMapper mapper) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.internalToken = Objects.requireNonNull(internalToken);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void prepare(UUID adventureId, SessionId sessionId, UUID ownerPlayerId, CharacterSheetId sheetId,
            List<String> abilityIds) {
        send("internal/v1/adventures/abilities/prepare", adventureId, sessionId, ownerPlayerId, sheetId, abilityIds, null);
    }

    @Override
    public void prepare(UUID adventureId, SessionId sessionId, UUID ownerPlayerId, CharacterSheetId sheetId,
            List<String> abilityIds, JsonNode sourceDefinition) {
        send("internal/v1/adventures/abilities/prepare", adventureId, sessionId, ownerPlayerId, sheetId, abilityIds, sourceDefinition);
    }

    @Override
    public void activate(UUID adventureId, SessionId sessionId, UUID ownerPlayerId, CharacterSheetId sheetId,
            List<String> abilityIds) {
        send("internal/v1/adventures/abilities/activate", adventureId, sessionId, ownerPlayerId, sheetId, abilityIds, null);
    }

    private void send(String path, UUID adventureId, SessionId sessionId, UUID ownerPlayerId, CharacterSheetId sheetId,
            List<String> abilityIds, JsonNode sourceDefinition) {
        try {
            String body = mapper.writeValueAsString(new Request(adventureId, sessionId.value(), ownerPlayerId,
                    sheetId.value(), abilityIds, sourceDefinition));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409 && response.body() != null && response.body().contains("ABILITY_SOURCE_INPUT_REQUIRED"))
                throw new AbilitySourceInputRequiredException("능력 정의를 원문 근거에 맞춰 입력한 뒤 같은 습득 요청을 다시 제출해야 합니다.");
            if (response.statusCode() / 100 != 2)
                throw new IllegalStateException("ability definition preparation failed: " + response.statusCode());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ability definition preparation interrupted", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("ability definition preparation failed", failure);
        }
    }

    private record Request(UUID adventureId, UUID sessionId, UUID ownerId, UUID characterSheetId,
            List<String> abilityIds, JsonNode sourceDefinition) {}
}

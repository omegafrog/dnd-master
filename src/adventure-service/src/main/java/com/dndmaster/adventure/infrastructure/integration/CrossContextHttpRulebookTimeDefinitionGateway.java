package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.GameSystemTimeDefinitionAdapter;
import com.dndmaster.adventure.application.runtime.GameSystemDefinitionPort;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads the locked session rulebook source. GM input never participates. */
public final class CrossContextHttpRulebookTimeDefinitionGateway implements Function<UUID, OptionalInt>, GameSystemDefinitionPort {
    private final AdventureSessionRepository sessions;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;

    public CrossContextHttpRulebookTimeDefinitionGateway(AdventureSessionRepository sessions, HttpClient httpClient,
            URI baseUri, Duration timeout, ObjectMapper mapper) {
        this.sessions = Objects.requireNonNull(sessions);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public OptionalInt apply(UUID sessionId) {
        return find(sessionId).map(definition -> GameSystemTimeDefinitionAdapter.secondsPerTurn(definition.definitionJson()))
                .filter(OptionalInt::isPresent).orElse(OptionalInt.empty());
    }

    @Override
    public java.util.Optional<GameSystemDefinitionPort.Definition> find(UUID sessionId) {
        try {
            var session = sessions.findById(new SessionId(sessionId)).orElseThrow();
            var configuration = session.runtimeConfiguration();
            if (configuration == null) return java.util.Optional.empty();
            for (UUID rulebookId : configuration.rulebookIds()) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/rulebooks/" + rulebookId + "/game-system-definition"))
                            .timeout(timeout).GET().build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() / 100 != 2) continue;
                    PublishedDefinition source = mapper.readValue(response.body(), PublishedDefinition.class);
                    if (GameSystemTimeDefinitionAdapter.secondsPerTurn(source.definitionJson()).isPresent())
                        return java.util.Optional.of(new GameSystemDefinitionPort.Definition(source.version(), source.definitionJson()));
                } catch (Exception ignored) {
                    // One unavailable rulebook must not hide a later locked rulebook.
                }
            }
            return java.util.Optional.empty();
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }

    private record PublishedDefinition(UUID rulebookId, long version, String definitionJson) {}
}

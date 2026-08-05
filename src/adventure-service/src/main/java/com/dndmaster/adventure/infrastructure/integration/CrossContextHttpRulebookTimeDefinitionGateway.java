package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.GameSystemTimeDefinitionAdapter;
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
public final class CrossContextHttpRulebookTimeDefinitionGateway implements Function<UUID, OptionalInt> {
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
        try {
            var session = sessions.findById(new SessionId(sessionId)).orElseThrow();
            var configuration = session.runtimeConfiguration();
            if (configuration == null) return OptionalInt.empty();
            for (UUID rulebookId : configuration.rulebookIds()) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("api/v1/rulebooks/" + rulebookId + "/source-preview"))
                            .timeout(timeout).GET().build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() / 100 != 2) continue;
                    SourcePreview source = mapper.readValue(response.body(), SourcePreview.class);
                    OptionalInt seconds = GameSystemTimeDefinitionAdapter.secondsPerTurn(source.content());
                    if (seconds.isPresent()) return seconds;
                } catch (Exception ignored) {
                    // One unavailable rulebook must not hide a later locked rulebook.
                }
            }
            return OptionalInt.empty();
        } catch (Exception ignored) {
            return OptionalInt.empty();
        }
    }

    private record SourcePreview(String content) {}
}

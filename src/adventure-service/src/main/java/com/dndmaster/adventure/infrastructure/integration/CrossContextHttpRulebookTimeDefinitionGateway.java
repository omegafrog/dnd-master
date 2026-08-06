package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.GameSystemTimeDefinitionAdapter;
import com.dndmaster.adventure.application.runtime.GameSystemDefinitionPort;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.runtime.RuntimeBindingRepository;
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
    private final RuntimeBindingRepository bindings;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;
    private final boolean requireLock;

    public CrossContextHttpRulebookTimeDefinitionGateway(AdventureSessionRepository sessions, RuntimeBindingRepository bindings,
            HttpClient httpClient, URI baseUri, Duration timeout, ObjectMapper mapper) {
        this(sessions, bindings, httpClient, baseUri, timeout, mapper, "", false);
    }

    public CrossContextHttpRulebookTimeDefinitionGateway(AdventureSessionRepository sessions, RuntimeBindingRepository bindings,
            HttpClient httpClient, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken) {
        this(sessions, bindings, httpClient, baseUri, timeout, mapper, internalToken, true);
    }

    private CrossContextHttpRulebookTimeDefinitionGateway(AdventureSessionRepository sessions, RuntimeBindingRepository bindings,
            HttpClient httpClient, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken, boolean requireLock) {
        this.sessions = Objects.requireNonNull(sessions);
        this.bindings = Objects.requireNonNull(bindings);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        this.internalToken = internalToken == null ? "" : internalToken;
        this.requireLock = requireLock;
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
            var adventureId = session.startedAdventureId();
            var binding = adventureId == null ? null : bindings.findCurrentByAdventureId(adventureId).orElse(null);
            if (requireLock && (binding == null || binding.gameSystemDefinitionVersion() < 1)) return java.util.Optional.empty();
            long lockedVersion = binding == null ? 0 : binding.gameSystemDefinitionVersion();
            for (UUID rulebookId : configuration.rulebookIds()) {
                var found = fetchDefinition(rulebookId, lockedVersion, true);
                if (found.isPresent()) return found;
            }
            return java.util.Optional.empty();
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public java.util.Optional<GameSystemDefinitionPort.Definition> findByRulebook(UUID rulebookId) {
        return fetchDefinition(rulebookId, 0, false);
    }

    @Override
    public java.util.Optional<GameSystemDefinitionPort.Definition> findByRulebook(UUID rulebookId, long version) {
        return fetchDefinition(rulebookId, version, false);
    }

    private java.util.Optional<GameSystemDefinitionPort.Definition> fetchDefinition(UUID rulebookId, long lockedVersion, boolean requireTime) {
        try {
            String suffix = lockedVersion > 0 ? "?version=" + lockedVersion : "";
            var requestBuilder = HttpRequest.newBuilder(baseUri.resolve("internal/v1/rulebooks/" + rulebookId + "/game-system-definition" + suffix))
                    .timeout(timeout).GET();
            if (!internalToken.isBlank()) requestBuilder.header("X-Internal-Token", internalToken);
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return java.util.Optional.empty();
            PublishedDefinition source = mapper.readValue(response.body(), PublishedDefinition.class);
            if (!requireTime || mapper.readTree(source.definitionJson()).isObject())
                return java.util.Optional.of(new GameSystemDefinitionPort.Definition(source.version(), source.definitionJson()));
        } catch (Exception ignored) {
            // One unavailable rulebook must not hide a later locked rulebook.
        }
        return java.util.Optional.empty();
    }

    private record PublishedDefinition(UUID rulebookId, long version, String definitionJson) {}
}

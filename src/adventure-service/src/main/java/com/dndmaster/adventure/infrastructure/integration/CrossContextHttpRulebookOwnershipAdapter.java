package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.ruleset.RulebookOwnershipHttpPort;
import com.dndmaster.adventure.domain.ruleset.OwnerPlayerId;
import com.dndmaster.adventure.domain.ruleset.RulebookId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Rulebook service authorizes both owner documents and READY shared catalog revisions. */
public final class CrossContextHttpRulebookOwnershipAdapter implements RulebookOwnershipHttpPort {
    private final HttpClient client; private final URI baseUrl; private final Duration timeout; private final ObjectMapper mapper;
    public CrossContextHttpRulebookOwnershipAdapter(HttpClient client, URI baseUrl, Duration timeout, ObjectMapper mapper) { this.client = client; this.baseUrl = baseUrl; this.timeout = timeout; this.mapper = mapper; }
    @Override public boolean isOwnedBy(RulebookId rulebookId, OwnerPlayerId owner) {
        try {
            URI uri = baseUrl.resolve("/internal/v1/rulebooks/" + rulebookId.value() + "/ownership?playerId=" + owner.value());
            var response = client.send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && mapper.readTree(response.body()).path("owned").asBoolean(false);
        } catch (Exception ignored) { return false; }
    }
}

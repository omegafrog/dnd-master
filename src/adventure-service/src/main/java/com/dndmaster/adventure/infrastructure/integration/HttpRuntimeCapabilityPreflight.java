package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.RuntimeCapabilityPreflightPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HttpRuntimeCapabilityPreflight implements RuntimeCapabilityPreflightPort {
    private final HttpClient client;
    private final URI gm, dice, character, map;
    private final Duration timeout;

    public HttpRuntimeCapabilityPreflight(HttpClient client, URI gm, URI dice, URI character, URI map, Duration timeout) {
        this.client = client; this.gm = gm; this.dice = dice; this.character = character; this.map = map; this.timeout = timeout;
    }

    @Override public Result check(String engineId, List<String> toolIds, Set<String> documentRoles) {
        List<String> blockers = new ArrayList<>();
        if ("ollama".equals(engineId) || "openai".equals(engineId)) check("provider " + engineId, gm, blockers);
        if (toolIds.contains("search")) check("search tool", gm, blockers);
        if (toolIds.contains("move") || documentRoles.contains("MAP")) check("combat-map adapter", map, blockers);
        if (toolIds.contains("note")) check("character tool", character, blockers);
        if (!toolIds.isEmpty()) check("dice adapter", dice, blockers);
        return new Result(blockers, List.of(), !blockers.isEmpty());
    }

    private void check(String name, URI base, List<String> blockers) {
        try {
            HttpRequest request = HttpRequest.newBuilder(base.resolve("actuator/health")).timeout(timeout).GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) blockers.add(name + " is unavailable (HTTP " + response.statusCode() + ")");
        } catch (Exception failure) {
            blockers.add(name + " is unavailable");
        }
    }
}

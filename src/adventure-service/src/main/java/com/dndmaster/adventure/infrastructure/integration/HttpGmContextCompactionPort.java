package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.ContextCompactionPort;
import com.dndmaster.adventure.application.runtime.ContextCompactionRequest;
import com.dndmaster.adventure.domain.runtime.checkpoint.ContextSummaryCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Provider-backed compaction adapter. Exact tail remains local and is never delegated as mutable summary state. */
public final class HttpGmContextCompactionPort implements ContextCompactionPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;

    public HttpGmContextCompactionPort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public ContextSummaryCandidate summarize(ContextCompactionRequest request) {
        try {
            String body = mapper.writeValueAsString(Request.from(request));
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/context-compactions"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("GM compaction returned " + response.statusCode());
            Response result = mapper.readValue(response.body(), Response.class);
            if (result.summary() == null || result.unresolvedThreats() == null || result.planRevisionId() == null) {
                throw new IllegalStateException("GM compaction omitted required fields");
            }
            return new ContextSummaryCandidate(result.summary(), result.unresolvedThreats(), result.planRevisionId(), result.planVersion());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GM compaction interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("GM compaction call failed", exception);
        }
    }

    record Request(UUID sessionId, UUID sourceTurnId, String context, Object exactTail, Object snapshotReferences) {
        static Request from(ContextCompactionRequest request) {
            return new Request(request.sessionId(), request.sourceTurnId(), request.context(), request.exactTail(), request.snapshotReferences());
        }
    }

    record Response(String summary, List<String> unresolvedThreats, UUID planRevisionId, long planVersion) { }
}

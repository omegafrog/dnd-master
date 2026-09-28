package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.ConversationCompactionCandidate;
import com.dndmaster.adventure.application.runtime.ConversationCompactionCandidatePort;
import com.dndmaster.adventure.application.runtime.ConversationCompactionJob;
import com.dndmaster.adventure.application.runtime.LongTermFactCandidate;
import com.dndmaster.adventure.application.runtime.TransientConversationCompactionException;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;

/** Internal HTTP client for a proposed summary. The remote service cannot persist it. */
public final class HttpConversationCompactionCandidatePort implements ConversationCompactionCandidatePort {
    private final HttpClient client; private final URI baseUri; private final Duration timeout;
    private final ObjectMapper mapper; private final String token;
    public HttpConversationCompactionCandidatePort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String token) {
        this.client=Objects.requireNonNull(client); this.baseUri=Objects.requireNonNull(baseUri); this.timeout=Objects.requireNonNull(timeout);
        this.mapper=Objects.requireNonNull(mapper); this.token=Objects.requireNonNull(token);
    }
    @Override public ConversationCompactionCandidate create(ConversationCompactionJob job, List<ConversationEntry> source) {
        return create(new UUID(0L, 0L), job, source);
    }
    @Override public ConversationCompactionCandidate create(UUID ownerPlayerId, ConversationCompactionJob job, List<ConversationEntry> source) {
        return create(ownerPlayerId, job, source, List.of());
    }
    @Override public ConversationCompactionCandidate create(UUID ownerPlayerId, ConversationCompactionJob job, List<ConversationEntry> source, List<RuntimeAddedFact> facts) {
        try {
            String json=mapper.writeValueAsString(new Request(ownerPlayerId, job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(), source, facts));
            HttpRequest request=HttpRequest.newBuilder(baseUri.resolve("/internal/gm/conversation-compaction"))
                    .timeout(timeout).header("Content-Type","application/json").header("X-Internal-Token",token)
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500) throw new TransientConversationCompactionException("AI Game Master returned " + response.statusCode());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("AI_GAME_MASTER_COMPACTION_REJECTED_HTTP_" + response.statusCode());
            Response result;
            try { result=mapper.readValue(response.body(),Response.class); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("COMPACTION_RESPONSE_DECODING_FAILED", e); }
            try {
                return new ConversationCompactionCandidate(result.sourceStart(), result.sourceEnd(), result.expectedAdventureVersion(),
                        result.excerpts(), result.longTermFacts() == null ? List.of() : result.longTermFacts());
            } catch (RuntimeException e) { throw new IllegalStateException("COMPACTION_RESPONSE_CONTRACT_INVALID", e); }
        } catch (TransientConversationCompactionException e) { throw e;
        } catch (java.io.IOException e) { throw new TransientConversationCompactionException("AI Game Master is temporarily unavailable",e);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new TransientConversationCompactionException("AI Game Master request interrupted",e);
        } catch (RuntimeException e) { throw e; }
    }
    record Request(UUID soloPlayerId,long sourceStart,long sourceEnd,long expectedAdventureVersion,List<ConversationEntry> conversation, List<RuntimeAddedFact> runtimeFacts) { }
    record Response(long sourceStart,long sourceEnd,long expectedAdventureVersion,List<ConversationCompactionCandidate.SourceExcerpt> excerpts, List<LongTermFactCandidate> longTermFacts) { }
}

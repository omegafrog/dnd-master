package com.dndmaster.relay.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpRagToolSearchTest {
    private final HttpRagToolSearch search = new HttpRagToolSearch(HttpClient.newHttpClient(),
            URI.create("http://127.0.0.1:1/"), Duration.ofSeconds(1), "internal-token", new ObjectMapper());

    @Test
    void rejectsScopeOwnedByAnotherAuthenticatedUserBeforeCallingTheKnowledgeService() {
        UUID authenticatedOwner = UUID.randomUUID();
        var scope = new ObjectMapper().createObjectNode().put("ownerId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> search.search(authenticatedOwner, scope, "perception rules"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("owner mismatch");
    }

    @Test
    void rejectsOversizedQueriesBeforeCallingTheKnowledgeService() {
        UUID owner = UUID.randomUUID();
        var scope = new ObjectMapper().createObjectNode().put("ownerId", owner.toString());
        assertThatThrownBy(() -> search.search(owner, scope, "x".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1000 characters");
    }
}

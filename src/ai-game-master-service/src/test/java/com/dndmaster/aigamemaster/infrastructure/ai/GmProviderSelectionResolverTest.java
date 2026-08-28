package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import com.dndmaster.aigamemaster.infrastructure.endpoint.InMemoryAgentEndpointStore;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmProviderSelectionResolverTest {
    @Test
    void resolves_the_requested_endpoint_once_and_preserves_the_endpoint_version() {
        UUID endpointId = UUID.randomUUID();
        Instant version = Instant.parse("2026-08-27T01:02:03Z");
        AgentEndpoint endpoint = new AgentEndpoint(endpointId, "ollama", AgentEndpoint.Provider.OLLAMA,
                URI.create("http://ollama.test/"), "qwen3:8b", null, true, version);
        AgentEndpointRegistry registry = new AgentEndpointRegistry(new InMemoryAgentEndpointStore());
        registry.save(endpoint);

        EffectiveGmProviderSelection resolved = new GmProviderSelectionResolver(registry)
                .resolve(new RequestedGmProviderSelection(endpointId, "openai", "requested-model", "medium"));

        assertEquals(endpointId, resolved.endpointId());
        assertEquals(version, resolved.endpointVersion());
        assertEquals("ollama", resolved.provider());
        assertEquals("qwen3:8b", resolved.model());
        assertEquals("medium", resolved.reasoning());
    }

    @Test
    void fails_before_provider_invocation_when_the_requested_endpoint_cannot_be_resolved() {
        AgentEndpointRegistry registry = new AgentEndpointRegistry(new InMemoryAgentEndpointStore());
        UUID endpointId = UUID.randomUUID();
        GmProviderSelectionResolver resolver = new GmProviderSelectionResolver(registry);

        GmProviderSelectionUnresolvedException error = assertThrows(GmProviderSelectionUnresolvedException.class,
                () -> resolver.resolve(new RequestedGmProviderSelection(endpointId, "ollama", "qwen3:8b", "none")));

        assertEquals(endpointId, error.requested().endpointId());
        assertEquals("GM_PROVIDER_SELECTION_UNRESOLVED", error.code());
    }

    @Test
    void resolves_active_endpoint_when_legacy_request_has_no_endpoint_id() {
        AgentEndpoint endpoint = new AgentEndpoint(UUID.randomUUID(), "codex", AgentEndpoint.Provider.CODEX_CLI,
                URI.create("http://unused.test/"), "codex-cli", null, true, Instant.now());
        AgentEndpointRegistry registry = new AgentEndpointRegistry(new InMemoryAgentEndpointStore());
        registry.save(endpoint);

        EffectiveGmProviderSelection resolved = new GmProviderSelectionResolver(registry)
                .resolve(new RequestedGmProviderSelection(null, "codex-cli", "legacy-model", "none"));

        assertEquals(endpoint.id(), resolved.endpointId());
        assertEquals("codex-cli", resolved.provider());
    }
}

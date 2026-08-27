package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import java.util.Objects;

/** Resolves one endpoint snapshot before provider work starts. */
public final class GmProviderSelectionResolver {
    private final AgentEndpointRegistry registry;

    public GmProviderSelectionResolver(AgentEndpointRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "endpoint registry required");
    }

    public EffectiveGmProviderSelection resolve(RequestedGmProviderSelection requested) {
        return resolveEndpoint(requested).effectiveSelection();
    }

    public EndpointResolution resolveEndpoint(RequestedGmProviderSelection requested) {
        Objects.requireNonNull(requested, "requested selection required");
        AgentEndpoint endpoint = requested.endpointId() == null
                ? registry.activeOrUnresolved(requested)
                : registry.find(requested.endpointId()).orElseThrow(() -> new GmProviderSelectionUnresolvedException(requested));
        EffectiveGmProviderSelection effective = new EffectiveGmProviderSelection(
                endpoint.id(), endpoint.updatedAt(), providerName(endpoint.provider()), endpoint.model(), requested.reasoning());
        return new EndpointResolution(endpoint, effective);
    }

    private static String providerName(AgentEndpoint.Provider provider) {
        return switch (provider) {
            case OLLAMA -> "ollama";
            case OPENAI_COMPATIBLE -> "openai";
            case CODEX_CLI -> "codex-cli";
        };
    }

    public record EndpointResolution(AgentEndpoint endpoint, EffectiveGmProviderSelection effectiveSelection) {
        public EndpointResolution {
            endpoint = Objects.requireNonNull(endpoint);
            effectiveSelection = Objects.requireNonNull(effectiveSelection);
        }
    }
}

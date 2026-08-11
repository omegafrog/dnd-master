package com.dndmaster.aigamemaster.application.endpoint;

import java.util.List;

public final class AgentEndpointRegistry {
    private final AgentEndpointStore store;
    public AgentEndpointRegistry(AgentEndpointStore store) { this.store = store; }
    public List<AgentEndpoint> list() { return store.list(); }
    public AgentEndpoint active() { return store.active().orElseThrow(() -> new IllegalStateException("no active agent endpoint")); }
    public void save(AgentEndpoint endpoint) { validate(endpoint); store.save(endpoint); }
    private static void validate(AgentEndpoint endpoint) {
        if (endpoint.name() == null || endpoint.name().isBlank() || endpoint.model() == null || endpoint.model().isBlank()) throw new IllegalArgumentException("endpoint name and model are required");
        if (endpoint.baseUrl() == null || endpoint.baseUrl().getHost() == null || !(endpoint.baseUrl().getScheme().equals("http") || endpoint.baseUrl().getScheme().equals("https"))) throw new IllegalArgumentException("endpoint must be an absolute HTTP(S) URL");
        String ref = endpoint.secretEnvironmentVariable();
        if (endpoint.provider() == AgentEndpoint.Provider.OPENAI_COMPATIBLE && (ref == null || !ref.matches("[A-Z][A-Z0-9_]*"))) throw new IllegalArgumentException("OpenAI-compatible endpoint requires an environment-variable secret reference");
        if (ref != null && !ref.isBlank() && !ref.matches("[A-Z][A-Z0-9_]*")) throw new IllegalArgumentException("secret must be an environment-variable name");
    }
}

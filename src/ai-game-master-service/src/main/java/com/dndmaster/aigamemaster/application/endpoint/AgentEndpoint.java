package com.dndmaster.aigamemaster.application.endpoint;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** A configured agent provider. The secret is always an environment-variable reference, never the secret value. */
public record AgentEndpoint(UUID id, String name, Provider provider, URI baseUrl, String model,
                            String secretEnvironmentVariable, boolean active, Instant updatedAt) {
    public enum Provider { OLLAMA, OPENAI_COMPATIBLE }
}

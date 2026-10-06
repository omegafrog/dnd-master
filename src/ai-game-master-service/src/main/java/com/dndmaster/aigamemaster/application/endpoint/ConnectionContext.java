package com.dndmaster.aigamemaster.application.endpoint;

public record ConnectionContext(String authorization) {
    public ConnectionContext {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.substring("Bearer ".length()).isBlank()) {
            throw new IllegalArgumentException("authenticated player is required");
        }
    }
}
